# Explication détaillée du code

Ce document explique tout le code du projet, package par package, dans l'ordre où les données
circulent réellement : du fichier `films.json` brut jusqu'à l'affichage console d'un résultat de
recherche. L'objectif est de pouvoir suivre un import ou une recherche de bout en bout et comprendre
*pourquoi* chaque choix a été fait, pas seulement *ce que* le code fait.

Deux flux distincts partagent le même modèle de données JPA :

```
Flux d'import (ImportApp)  :  films.json -> DTOs -> FilmMapper -> entités -> DAOs -> MariaDB
Flux de consultation (MenuApp) : saisie clavier -> RechercheService -> DAOs -> MariaDB -> affichage
```

---

## 1. Le modèle de données — package `entity`

Sept entités JPA, toutes dans `fr.diginamic.cinema.entity` :

- **`Pays`, `Langue`, `Genre`, `LieuNaissance`** — entités "lookup" simples : un `id` `Integer`
  auto-incrémenté (`@GeneratedValue(strategy = GenerationType.IDENTITY)`) et un champ `nom`/`libelle`
  marqué `unique = true`. Ce sont des tables de référence : la contrainte unique en base empêche
  d'avoir deux fois "France" ou deux fois "English".
- **`Personne`** — clé **naturelle** : l'`id` est directement l'id IMDb (`String`, ex. `nm0000001`),
  déclaré `final` avec `@Setter(AccessLevel.NONE)` : une fois construite, une `Personne` ne peut plus
  changer d'id. `@RequiredArgsConstructor` (Lombok) génère un constructeur `Personne(String id)` pour
  ce champ final ; `@NoArgsConstructor(force = true)` génère *aussi* un constructeur sans argument
  (obligatoire pour qu'Hibernate puisse instancier l'entité par réflexion en la chargeant depuis la
  base, même si le champ est `final`). `dateNaissance` est un `LocalDate`, `taille` un `BigDecimal`
  (jamais de `double` pour une valeur décimale exacte), et `lieuNaissance` est un `@ManyToOne(fetch =
  LAZY)` — explicitement paresseux car ce champ n'est jamais utilisé dans les recherches actuelles,
  pas la peine de le charger à chaque fois qu'une `Personne` est lue.
- **`Film`** — même pattern de clé naturelle que `Personne` (id IMDb, ex. `tt0082449`). `rating` en
  `BigDecimal`. `plot` utilise `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` plutôt que `@Lob` : `@Lob`
  générait un type `LONGTEXT` côté base alors que la colonne DDL réelle est `TEXT`, ce qui cassait la
  validation de schéma au démarrage (`hibernate.hbm2ddl.auto=validate` compare strictement le mapping
  Java au schéma existant). `anneeDebut`/`anneeFin` sont des `Integer` nullable (pas des `int`
  primitifs, puisqu'une année peut être inconnue). `genres`/`realisateurs` sont des `@ManyToMany`
  (LAZY par défaut côté JPA), `roles` un `@OneToMany(mappedBy = "film")`.
- **`Role`** — id technique auto-incrémenté (contrairement à `Film`/`Personne`, un rôle n'a pas
  d'identifiant naturel dans le JSON source). `film` et `personne` sont des `@ManyToOne` avec
  `@JoinColumn(nullable = false)` : un rôle appartient toujours à exactement un film et une personne.

**Aucune entité ne redéfinit `equals`/`hashCode`.** C'est un choix délibéré : avec des clés
`IDENTITY` auto-générées et du lazy-loading, redéfinir `equals`/`hashCode` sur des entités JPA est une
source classique de bugs (deux instances représentant la même ligne mais pas encore "égales" avant le
premier `flush`, `hashCode` qui change une fois l'id assigné si basé sur l'id, etc.). Le
dédoublonnage pendant l'import est donc géré entièrement en dehors des entités, via des
`Map<String, Entity>` côté mapper (voir section 4.2).

## 2. La configuration JPA — `persistence.xml` et `EntityManagerProvider`

`src/main/resources/META-INF/persistence.xml` déclare une persistence-unit `cinema` en
`RESOURCE_LOCAL` (pas de conteneur Jakarta EE, l'application gère elle-même ses `EntityManager`).
Elle liste explicitement les 7 classes d'entités via `<class>` — nécessaire car, hors conteneur, une
persistence-unit `RESOURCE_LOCAL` ne scanne pas automatiquement le classpath à la recherche
d'entités. `hibernate.hbm2ddl.auto=validate` signifie qu'Hibernate ne crée ni ne modifie jamais le
schéma : il compare seulement le mapping Java aux tables déjà existantes (créées via `sql/schema.sql`)
et échoue au démarrage en cas de désaccord.

`fr.diginamic.cinema.persistence.EntityManagerProvider` est un point d'accès unique et statique à
l'`EntityManagerFactory`, construite **paresseusement** (au premier appel de `getEntityManagerFactory()`,
pas au chargement de la classe). Ce n'était pas le choix initial : un champ `static final` initialisé
directement dans sa déclaration semblait plus simple, mais une exception levée dans un bloc
d'initialisation statique est toujours enveloppée par la JVM dans `ExceptionInInitializerError` (règle
JLS), quel que soit son type d'origine — et pire, la classe est alors marquée en échec *définitivement*
pour le reste de l'exécution (`NoClassDefFoundError` ensuite, même si la base redémarre). Voir bug #9
en section 7. Avec la construction paresseuse, un échec de connexion redevient une `PersistenceException`
normale et capturable, et rien n'est mis en cache en cas d'échec : l'appel suivant retente réellement.
Chaque appelant récupère un `EntityManager` frais via `getEntityManager()` (qui passe par
`getEntityManagerFactory()`, pas par le champ directement) et est responsable de le fermer
(`try-with-resources` partout dans le code).

**Identifiants de connexion externalisés via `.env`** : `persistence.xml` ne contient plus `jakarta.
persistence.jdbc.user`/`password` en dur. Au premier appel, `getEntityManagerFactory()` charge un
fichier `.env` à la racine (librairie `dotenv-java`, `Dotenv.configure().ignoreIfMissing().load()` — le
`.ignoreIfMissing()` évite de casser la CI GitHub Actions, qui n'a pas de `.env`) et construit une
`Map<String, Object>` (`DB_USER`/`DB_PASSWORD` -> `jakarta.persistence.jdbc.user`/`password`), passée à
la surcharge à deux arguments `Persistence.createEntityManagerFactory(String, Map)` : ces valeurs
priment sur celles (absentes désormais) de `persistence.xml`. `.env` n'est jamais commit (`.gitignore`) ;
`.env.example` (commit) sert de modèle avec des clés vides.

## 3. La couche DAO — package `dao`

`Dao<T, ID>` est une interface générique définissant le contrat CRUD standard : `save`, `saveAll`,
`findById`, `findAll`, `update`, `updateAll`, `delete`. `AbstractDao<T, ID>` en est l'implémentation
générique, réutilisée par les 7 DAOs concrets. Un détail d'implémentation important : `AbstractDao`
stocke un champ `Class<T> entityClass`, passé par chaque sous-classe à son constructeur (ex.
`super(Pays.class)`) — c'est un contournement classique de l'effacement de type (*type erasure*) en
Java : à l'exécution, on ne peut pas écrire `new T()` ni `T.class`, donc on garde une référence
explicite à la classe pour pouvoir appeler `em.find(entityClass, id)` ou construire une
`CriteriaQuery<T>`.

La gestion transactionnelle est factorisée dans `executeInTransaction`, avec deux surcharges : une
qui prend un `Function<EntityManager, R>` (pour les opérations qui renvoient une valeur, comme
`update`), une autre un `Consumer<EntityManager>` (pour `save`/`saveAll`/`updateAll`/`delete`, qui ne
renvoient rien). Les deux ouvrent un `EntityManager`, démarrent une transaction, exécutent l'action, et
*commit*-ent — avec un *rollback* automatique en cas d'exception. Les méthodes de lecture (`findById`,
`findAll`) n'ouvrent volontairement **pas** de transaction : une lecture seule ne modifie rien en base.

**`updateAll(Collection<T>)`** : même pattern exact que `saveAll`, mais avec `em.merge(entity)` au lieu
d'`em.persist(entity)` — une seule transaction pour toute la collection, `flush()`/`clear()` tous les
`BATCH_SIZE` éléments. Utilisée par `ImportService` (section 4.3) pour mettre à jour en une fois tous
les films déjà existants lors d'un ré-import, plutôt qu'un `update()` par film en boucle.

**Pourquoi `save`/`update`/`findById`/`delete` (versions unitaires) restent, bien que sans appelant
aujourd'hui** (vérifié par `grep` : seuls `saveAll`/`updateAll`/`findAll` sont réellement utilisés,
tous depuis `ImportService`) : ils font partie du contrat CRUD générique de `Dao<T, ID>`, cohérent avec
l'exigence "un DAO par entité" du projet — les retirer transformerait la couche DAO générique en un
helper sur mesure pour les seuls besoins d'`ImportService`, perdant sa généricité.

**`save` vs `saveAll`** : `save(T entity)` persiste une seule entité dans sa propre transaction — simple,
mais coûteux en boucle (un commit par entité). `saveAll(Collection<T> entities)` persiste toute une
collection dans **une seule transaction**, avec `flush()`/`clear()` du contexte de persistance tous les
`BATCH_SIZE` (100) éléments pour ne pas le laisser grossir indéfiniment. C'est `saveAll` qu'utilise
`ImportService` pour les gros volumes (section 4.3) — `save` seule provoquait des dizaines de milliers de
commits individuels pendant l'import (trouvé en lisant le code, pas en testant — ne figure donc pas dans
la liste de la section 7, scopée aux bugs révélés par l'exécution). `save` renvoie désormais `void` (pas `T`) :
aucun appelant n'utilisait la valeur de retour, trouvé en `grep` avant de simplifier.

Les 7 DAOs concrets (`PaysDao`, `LangueDao`, `GenreDao`, `LieuNaissanceDao`, `PersonneDao`, `FilmDao`,
`RoleDao`) ne font quasiment qu'hériter d'`AbstractDao` — sauf `FilmDao`, `PersonneDao` et `RoleDao`,
qui portent en plus les méthodes de requête JPQL nécessaires aux recherches du menu et à l'import
idempotent :

- `RoleDao.findByFilmId` — casting complet d'un film, avec `JOIN FETCH r.personne` : pas
  indispensable pour éviter un crash (`Role.personne` est `@ManyToOne`, donc *EAGER* par défaut), mais
  évite une requête SQL séparée par rôle (problème classique du *N+1 select*).
- `FilmDao.findByActeurId` — filmographie d'un acteur, `JOIN f.roles r` + `DISTINCT` (un acteur peut
  avoir plusieurs rôles dans un même film, sans `DISTINCT` il apparaîtrait plusieurs fois).
- `FilmDao.findByAnneeRange` — filtre uniquement sur `anneeDebut` (pas `anneeFin`) : choix simplifié,
  ne traite pas différemment les séries/shows dont la présence s'étend sur plusieurs années.
- `FilmDao.findCommunsEntreActeurs` — films communs à deux acteurs, double `JOIN f.roles` avec deux
  alias (`r1`, `r2`), un par acteur.
- `PersonneDao.findCommunsEntreFilms` — acteurs communs à deux films. Point notable : contrairement à
  `Film.roles`, `Personne` n'a pas de collection `roles` — la requête part donc de `Role` plutôt que
  de `Personne`, avec une sous-requête (`r.personne IN (SELECT r2.personne FROM Role r2 WHERE
  r2.film.id = :filmId2)`).
- `FilmDao.findByActeurIdAndAnneeRange` — combine le join de `findByActeurId` et le filtre de
  `findByAnneeRange`.
- `PersonneDao.findByIdentiteLike` / `FilmDao.findByNomLike` — recherche par nom partiel (`LIKE
  "%motif%"`), pour retrouver un id IMDb sans le connaître d'avance (option 7 du menu, section 5).
  Pas de `LOWER()` ni de gestion manuelle des accents : `LIKE` respecte la même collation
  `utf8mb4_general_ci` que `=` (insensible casse/accents, cf. `cleDedoublonnage` en 4.2), donc la base
  s'en charge déjà.

Chaque appel de DAO ouvre et referme son propre `EntityManager` : c'est pour cela que les champs
`LAZY` non explicitement chargés (via `JOIN FETCH`) ne peuvent plus être accédés une fois le résultat
renvoyé à l'appelant (`LazyInitializationException`). Le code évite ce piège en ne touchant jamais,
côté `MenuApp`/`Affichage`, que des champs chargés `EAGER` ou déjà rapatriés par un `JOIN FETCH` ciblé.

## 4. Le flux d'import — de `films.json` à la base

### 4.1 Les DTOs JSON — package `json`

Six classes (`FilmJson`, `PaysJson`, `LieuTournageJson`, `NaissanceJson`, `PersonneJson`, `RoleJson`)
qui miroitent exactement la structure du JSON source, champ pour champ, en types bruts (`String`,
DTO imbriqué, `List`) — jamais de `LocalDate`/`BigDecimal` ici. Le suffixe `Json` évite tout conflit de
nom avec les entités JPA du même domaine (`Film` vs `FilmJson`). Ces classes ne servent qu'au binding
Jackson automatique (`ObjectMapper.readValue`) ; toute la conversion vers un type propre (dates,
notes, tailles...) est déléguée au mapper. Ce choix a été fait sciemment : les dates du JSON ne sont
pas dans un format stable/ISO (voir 4.2), donc un binding Jackson direct vers `LocalDate` via
`jackson-datatype-jsr310` n'aurait pas fonctionné — le parsing doit rester manuel.

### 4.2 Le Mapper — package `mapper`

**`DedupCaches`** est une simple classe de données : six `Map<String, Entity>` publiques et `final`
(`pays`, `langues`, `genres`, `lieuxNaissance`, `personnes`, `films`), une par type d'entité
dédoublonnée. Une même instance de `DedupCaches` est créée par `ImportService` et transmise à chaque
appel du mapper pendant tout l'import, pour que la même `Pays` (par exemple) soit réutilisée à chaque
fois qu'un film différent cite le même pays. Elle porte aussi six `Set<String>` (`personnesExistantes`,
`filmsExistants`, `paysExistants`, `languesExistantes`, `genresExistants`, `lieuxNaissanceExistants`),
utilisés uniquement par `ImportService` pour l'import idempotent (section 4.3) — ils retiennent quelles
clés étaient déjà en base *avant* ce run, le mapper lui-même ne les consulte jamais.

**`FilmMapper`** est un pur transformateur statique (aucun accès base de données). Ses méthodes,
dans l'ordre où elles apparaissent dans le fichier :

- **`toFilm(FilmJson, DedupCaches)`** — point d'entrée public, appelé une fois par film du JSON. Le
  dédoublonnage se fait par id IMDb (`caches.films.get(key)`) : au premier film rencontré pour un id
  donné, l'entité complète est construite (pays, langue, genres, réalisateurs, rôles). Sur un doublon
  (le JSON source contient 38 ids dupliqués — des séries/shows scrapés une fois par page de
  filmographie d'acteur), seul l'intervalle d'années est élargi (`anneeDebut` = minimum,
  `anneeFin` = maximum des valeurs vues) ; tous les autres champs de cette occurrence-là sont ignorés,
  car diff manuel confirmé : seuls `url` et `anneeSortie` diffèrent réellement entre doublons, le
  reste (nom, rating, plot, langue, casting) est identique. `toFilm` ne peut donc pas utiliser
  `computeIfAbsent` comme les autres builders (qui ignore le lambda si la clé existe déjà) : il lui
  faut un `get` explicite puis une branche `if (film == null) {...} else {...}`.
- **`toPersonne`** — dédoublonne par id IMDb. Piège trouvé en testant contre les vraies données :
  `lieuNaissance` est une chaîne *vide* (pas `null`) dans ~10 840 cas ; sans la garde
  `if (lieu != null && !lieu.trim().isEmpty())`, toutes les personnes sans lieu de naissance connu se
  seraient retrouvées dédoublonnées sur un faux `LieuNaissance` de libellé `""`.
- **`toRole`** — ne dédoublonne *pas* (chaque rôle est une ligne de casting distincte, plusieurs rôles
  peuvent légitimement exister pour la même personne dans le même film). Seule la `Personne` associée
  est dédoublonnée, via `toPersonne`.
- **`isPrincipal`** — compare l'id de l'acteur d'un rôle à la liste `castingPrincipal` du JSON, pour
  dériver le booléen `Role.principal`.
- **`toPays` / `toLangue` / `toGenre` / `toLieuNaissance`** — quatre méthodes structurellement
  identiques (trim -> `cleDedoublonnage` -> `computeIfAbsent` -> construction), chacune ne différant
  que par l'entité construite. Elles délèguent à **`dedupe`**, un helper générique
  (`dedupe(Map<String, T> cache, String raw, Function<String, T> builder)`) qui factorise ce pattern
  commun : chaque appelant ne fournit plus qu'un lambda de construction propre à son entité.
- **`cleDedoublonnage`** — normalise une valeur pour servir de clé de dédoublonnage : trim, suppression
  des diacritiques (`Normalizer.normalize(..., NFD)` + regex `\p{M}`), minuscules. Nécessaire car la
  collation MariaDB utilisée (`utf8mb4_general_ci`) est insensible à la casse *et* aux accents pour la
  contrainte unique en base (`"Montreal"` et `"Montréal"` y sont la même valeur) — sans cette
  normalisation, deux valeurs "différentes" pour une `Map` Java mais "identiques" pour MariaDB
  seraient passées au travers du cache et auraient cassé au moment de l'insertion
  (`ConstraintViolationException` sur la deuxième). La valeur d'origine (première rencontrée) reste
  celle réellement stockée en base — seule la clé de recherche dans la `Map` est normalisée. `public`
  (pas package-private comme le reste, hormis `toFilm`) : `ImportService`, dans un autre package, en a
  besoin pour précharger les entités existantes avec exactement la même clé que le mapper (section 4.3)
  — la rendre publique évite de dupliquer cette logique de normalisation ailleurs.
- **`parseAnneeRange`** — sépare la chaîne brute sur le caractère **tiret demi-cadratin** `–`
  (`–`, différent du tiret `-` classique). Une seule partie -> `debut` seul, `fin = null` ; deux
  parties -> `debut`/`fin`. Renvoie toujours un `AnneeRange` (jamais `null` lui-même, même si la
  valeur brute est absente — voir section 6 pour le pourquoi de ce choix), sous forme d'un `record`
  privé imbriqué en bas du fichier.
- **`parseRating`** — normalise la virgule décimale en point avant `new BigDecimal(...)` (le JSON
  mélange les deux formats) ; vide/`null` -> `null`.
- **`parseDate`** — essaie une liste de `DateTimeFormatter` (anglais `Locale.ENGLISH`, français
  `Locale.FRANCE`) l'un après l'autre, ne capturant que `DateTimeParseException` (pas `Exception`
  générique) pour passer au suivant. Ne fabrique jamais une date partielle : si aucun format ne
  correspond exactement (jour/mois/année tous présents), renvoie `null` plutôt que d'inventer une
  valeur.
- **`parseTaille`** — parse une taille en mètres, gère le format avec mesure impériale entre
  parenthèses (`"6′ 2½″ (1.89 m)"`, ne garde que la partie entre parenthèses), et normalise en tout
  début de méthode un caractère invisible piégeux : ~1294 tailles utilisent une **espace fine
  insécable** (` `, convention typographique française) entre la valeur et l'unité au lieu d'une
  espace normale — invisible à l'œil, mais cassait silencieusement le `.replace(" m", "")` qui suit.

### 4.3 `ImportService`

Orchestre l'import en **trois phases bien séparées** (initialement deux, une phase de préchargement
ajoutée pour rendre l'import idempotent, voir plus bas) :

0. **Préchargement** — `chargerExistant(caches)`, appelée en tout premier dans `importer()`, charge
   dans `caches` tout ce qui existe déjà en base (`findAll()` sur les 6 DAOs concernés — `Role` mis à
   part, voir plus bas), avec les **mêmes clés** que celles utilisées par le mapper (id IMDb pour
   `Personne`/`Film`, `FilmMapper.cleDedoublonnage(nom)` pour les 4 entités lookup). Retient aussi ces
   clés dans les six `Set<String>` `xxxExistants(es)` de `caches`.
1. **Phase de mapping** — lit tout `films.json` en une seule fois (Jackson, `List<FilmJson>`, pas de
   streaming — fichier jugé trop petit pour le justifier), puis appelle `FilmMapper.toFilm(dto, caches)`
   pour chaque film. Aucun DAO n'est appelé pendant cette phase : tout s'accumule dans `caches`. Comme
   le mapper ne fait aucune distinction entre une entité préchargée à l'étape 0 et un doublon rencontré
   *dans* ce run (même mécanisme `computeIfAbsent`/get-ou-construire), une entité déjà en base est
   retrouvée telle quelle plutôt que recréée — et pour `Film`, la fusion `anneeDebut`/`anneeFin` déjà
   écrite pour les doublons intra-run (section 4.2) s'applique donc *aussi* aux doublons entre deux
   imports, sans code supplémentaire dans le mapper.
2. **Phase de persistance** — `persister(caches)` sauvegarde chaque collection de `caches`, dans
   l'ordre imposé par les clés étrangères, **en filtrant à chaque fois via les `Set` `xxxExistants(es)`**
   pour ne `saveAll` que ce qui est réellement nouveau ce run :
   - `pays`/`langues`/`genres`/`lieuxNaissance` — filtrés puis `saveAll` (jamais d'`update`, leur
     `nom`/`libelle` ne change jamais après création).
   - `personnes` — filtrées puis `saveAll` (même raison, aucune mise à jour possible).
   - `films` — séparés en deux groupes : les nouveaux vont dans `saveAll` ; ceux déjà en base sont
     collectés dans `filmsAMettreAJour` et passent par un seul `filmDao.updateAll(...)`, pour répercuter
     une éventuelle fusion `anneeDebut`/`anneeFin`.
   - `roles` — collectés et persistés **seulement pour les films nouveaux** (`nouveauxFilms`, pas
     `caches.films.values()`) : `Role` n'a ni clé métier ni contrainte unique en base (juste un id
     `IDENTITY`), impossible de savoir si "ce rôle précis" existe déjà. Hypothèse retenue : le casting
     d'un film ne change pas entre deux versions du JSON, donc pas besoin de le retoucher si le film
     existait déjà. Bénéfice collatéral : `nouveauxFilms` ne contient que des films jamais rechargés
     depuis la base, donc jamais concernés par le `LAZY` de `Film.roles` — itérer sur
     `caches.films.values()` ici aurait risqué une `LazyInitializationException` sur un film préchargé
     et détaché (son `EntityManager` de `findAll()` déjà refermé).

Sur un premier import (base vide), `chargerExistant` ne trouve rien : les `Set` restent vides, tout est
traité comme nouveau — comportement strictement identique à avant l'ajout de l'idempotence. Aucun
`cascade` explicite n'est nécessaire sur les relations (`@ManyToOne`/`@ManyToMany`) : au moment où
`filmDao.saveAll(...)` s'exécute, les `Pays`/`Langue`/`Genre`/`Personne` référencés ont déjà été
persistés et committés dans une transaction précédente (donc déjà un id réel en base) — Hibernate n'a
besoin que de cet id pour écrire les clés étrangères.

**Testé de bout en bout** : deux imports successifs sur la même base produisent des comptes de lignes
strictement identiques sur les 7 tables (voir `PLAN.md`, étape 19). La limite initiale (`update()` appelé
un par un pour les films déjà existants, même famille de problème que `save()` avant `saveAll`) a été
corrigée juste après avec `updateAll` (voir section 3).

### 4.4 `ImportApp`

Point d'entrée (`fr.diginamic.cinema.app.ImportApp`). Signature classique
`public static void main(String[] args)`. Chronomètre l'import et affiche un message de progression
avant/après (~95 s sur base vide, ~11 s sur une base déjà peuplée grâce à l'idempotence). Deux `catch`
distincts : `IOException` (ex. `films.json` absent) et `PersistenceException` (ex. MariaDB non
joignable, ou échec de persistance comme une contrainte violée). Le second message affiche
`e.getMessage()` plutôt qu'une cause supposée — `jakarta.persistence.RollbackException` (commit en
échec) hérite aussi de `PersistenceException`, donc ce `catch` peut recevoir des causes très
différentes ; mieux vaut rester honnête sur ce qu'on sait vraiment (voir bug #10 en section 7) que
d'afficher un diagnostic peut-être faux.

## 5. Le flux de consultation — de la saisie clavier à l'affichage

### 5.1 `RechercheService`

Couche de service très fine : huit méthodes publiques, chacune une délégation directe vers la méthode
DAO correspondante (`filmographieActeur` -> `FilmDao.findByActeurId`, `castingFilm` ->
`RoleDao.findByFilmId`, `rechercherActeursParNom` -> `PersonneDao.findByIdentiteLike`,
`rechercherFilmsParNom` -> `FilmDao.findByNomLike`, etc.). Aucune logique métier supplémentaire — tout
le filtrage/jointure vit déjà dans le JPQL des DAOs (section 3). Les DAOs sont des champs
`private final`, instanciés une seule fois (même pattern que `ImportService`).

### 5.2 Package `console`

Trois classes, séparant trois responsabilités bien distinctes (refactor appliqué après que `MenuApp`
ait grossi jusqu'à mélanger les trois dans un seul fichier) :

- **`Saisie`** — toute la lecture clavier. `lireTexte` lit une ligne brute ; `lireEntier` boucle tant
  que l'entrée n'est pas un entier valide (catch `NumberFormatException`) ; `lireChoix` réutilise
  `lireEntier` en ajoutant sa propre validation de plage (0 à 7). Toutes les lectures passent par
  `scanner.nextLine()` — jamais `scanner.nextInt()` — pour éviter un bug classique : `nextInt()` ne
  consomme pas le `\n` final tapé par l'utilisateur, qui reste dans le buffer et fait qu'un
  `nextLine()` suivant renvoie immédiatement une chaîne vide au lieu d'attendre une vraie saisie.
  Chaque `System.out.print(message)` est suivi d'un `System.out.flush()` explicite : sans lui, le
  texte du prompt peut rester bloqué dans le buffer du flux et n'apparaître qu'après que
  l'utilisateur ait déjà tapé sa réponse (constaté en conditions réelles, cf. `PLAN.md`).
- **`Affichage`** — tout l'affichage console : `afficherMenu` (les 8 lignes du menu, 7 recherches +
  quitter) et un helper par type d'entité affichée (`afficherFilm`, `afficherRole`, `afficherPersonne`).
  Ces helpers existent plutôt qu'un simple `System.out.println(entity)` parce qu'aucune entité n'a de
  `toString()` généré (`@ToString`), et que certains champs (`Film.genres`/`realisateurs`/`roles`,
  `Personne.lieuNaissance`) sont `LAZY` : un `toString()` auto-généré qui les toucherait risquerait un
  `LazyInitializationException`, l'`EntityManager` d'origine étant toujours fermé à ce stade. Chaque
  helper n'affiche donc que des champs sûrs. Notable : `afficherFilm` gère le cas où `anneeFin` est
  `null` (la grande majorité des films, qui n'ont qu'une seule année de sortie, pas un intervalle) en
  n'affichant qu'une seule année dans ce cas plutôt que `"1981-null"`. **`afficherResultats(List<T>,
  Consumer<T>)`** — méthode générique qui factorise l'affichage de toute liste de résultats entre deux
  barres de séparation (`SEPARATEUR`, constante), suivie du nombre trouvé (accord singulier/pluriel
  géré). Prend en second paramètre un `Consumer<T>` — le même principe que `Function<String, T> builder`
  dans `FilmMapper.dedupe` (section 4.2), mais pour "faire quelque chose" avec chaque élément plutôt que
  construire une valeur. Appelée avec une référence de méthode (`Affichage::afficherFilm`, etc.),
  centralise un format auparavant dupliqué (bloc `if (isEmpty) {...} else { for (...) {...} }`) dans
  chacune des 7 méthodes de `MenuActions`.
- **`MenuActions`** — les 7 méthodes `menuXxx` (une par option de menu), chacune : lit sa ou ses
  saisie(s) via `Saisie`, appelle la méthode `RechercheService` correspondante, puis affiche le résultat
  via `Affichage.afficherResultats`. `menuCastingFilm` affiche des `Role` et non des `Personne` : le
  casting d'un film, c'est le personnage joué (`characterName`, `principal`), pas juste l'identité de
  l'acteur. `menuRechercheParNom` (option 7) demande d'abord un sous-choix acteur/film, puis un nom
  (ou fragment), avant de déléguer à `rechercherActeursParNom`/`rechercherFilmsParNom`.

### 5.3 `MenuApp`

Point d'entrée (`fr.diginamic.cinema.app.MenuApp`), réduit après refactor à sa seule responsabilité
d'orchestration : construit un `Scanner`/`RechercheService`, boucle (`do/while`) tant que l'utilisateur
n'a pas choisi `0`, et dispatche vers `Affichage`/`Saisie`/`MenuActions` selon le choix. Numérotation du
menu : **1 à 7** pour les recherches, **0** pour quitter (plutôt que le dernier numéro utilisé) — choix
délibéré pour que le numéro de sortie reste stable si de nouvelles options sont ajoutées plus tard, sans
jamais avoir à le renuméroter (ce qui s'est déjà vérifié : l'option 7, recherche par nom, a été ajoutée
après coup sans toucher au `0`).

Le `switch` de dispatch est entouré d'un `try/catch (PersistenceException e)`, **à l'intérieur** de la
boucle `do/while` (pas autour) : si la base devient injoignable en cours de session, l'appli affiche un
message clair et redemande un choix plutôt que de planter entièrement — tolérant pour un programme
interactif. Ça ne fonctionne correctement que grâce à la construction paresseuse de
`EntityManagerProvider` (section 2) : sans elle, la reprise serait impossible après un premier échec.

## 6. Décisions transversales à garder en tête

- **Pas d'`equals`/`hashCode` custom sur les entités** — le dédoublonnage vit entièrement dans
  `DedupCaches` (côté import), jamais au niveau des entités elles-mêmes (voir section 1).
- **`LazyInitializationException` évitée par construction**, pas par un correctif générique : chaque
  DAO ouvre et referme son propre `EntityManager` par appel, donc toute association `LAZY` non
  explicitement rapatriée par un `JOIN FETCH` ciblé (`RoleDao.findByFilmId`) redeviendrait
  inaccessible après le retour de la méthode. Le code n'accède donc jamais, en dehors d'un DAO, qu'à
  des champs `EAGER` ou déjà chargés par un join explicite.
- **`BigDecimal`, jamais `double`**, pour toute colonne `DECIMAL` (`rating`, `taille`) — évite les
  imprécisions d'arrondi binaire sur des valeurs décimales exactes.
- **Import idempotent** : relancer l'import sur une base déjà peuplée ne duplique plus rien (section
  4.3) — `ImportService` précharge l'existant avant de mapper, et ne persiste que ce qui est
  réellement nouveau. `sql/reset_db.sql` (ou `DROP DATABASE` + `sql/schema.sql`) reste utile pour
  repartir d'une base *vide*, mais n'est plus une étape obligatoire entre deux imports.
- **Persistance en lot plutôt qu'entité par entité** : `saveAll` (section 3) regroupe toute une
  collection dans une seule transaction, plutôt qu'un `save` par entité (un commit par ligne). Le
  gain mesuré est net (~360 s -> ~95 s sur les 2748 films, voir `PLAN.md` étape 18), mais pas total :
  `GenerationType.IDENTITY` (5 entités sur 7) empêche de toute façon tout vrai batching réseau JDBC
  avec cette stratégie, et `hibernate.jdbc.batch_size` n'est pas configuré pour les 2 entités à clé
  naturelle qui pourraient en bénéficier.
- **`parseAnneeRange` ne renvoie jamais `null` lui-même** (renvoie `new AnneeRange(null, null)` plutôt
  que `null` si la valeur brute est absente) — corrige un risque de `NullPointerException` latent
  dans `toFilm`, qui déréférence `anneeRange.fin()` sans vérification. Jamais déclenché avec le
  dataset actuel (tous les films ont la clé `anneeSortie`), mais laissé volontairement défensif : même
  famille de bug que les clés `pays`/`langue`/`lieuTournage` manquantes, qui elles se manifestent
  réellement sur ce dataset (voir section suivante).

## 7. Bugs réels trouvés en testant contre les vraies données

La plupart des bugs listés ci-dessous n'étaient pas détectables par simple relecture du code — seule
l'exécution contre le vrai `films.json` (21.7 Mo, 2748 films) les a révélés. Ils expliquent pourquoi
certaines lignes du code existent (gardes `if (x != null)`, normalisations en apparence superflues,
etc.). Détail complet, chiffres exacts et diagnostics dans `PLAN.md` (étape 11) ; résumé ici :

1. `persistence.xml` sans `<class>` déclaré -> Hibernate ne voit aucune entité (RESOURCE_LOCAL hors
   conteneur ne scanne pas automatiquement). Fix : les 7 `<class>` listées explicitement.
2. Clés JSON parfois **absentes** (pas juste `null`) : `lieuTournage` (589 films sur 2748),
   `pays` (18), `langue` (19) -> `NullPointerException` dans `toFilm` avant l'ajout des gardes.
3. `Role.characterName` sans `@Column(name = "character_name")` -> Hibernate cherchait une colonne
   `characterName` (pas de conversion automatique camelCase -> snake_case sans stratégie de nommage
   configurée).
4. `Film.plot` en `@Lob` générait `LONGTEXT` au lieu du `TEXT` réel de la colonne (voir section 1).
5. `parseTaille` : bug de copier-coller (variable calculée mais jamais utilisée), puis découverte de
   l'espace fine insécable invisible (voir section 4.2).
6. **Dédoublonnage des entités lookup incomplet**, découvert en 3 vagues : espaces superflus, casse,
   accents — tous invisibles à l'œil sur les données, tous résolus par `cleDedoublonnage` (section 4.2).
7. **`Film.rating` toujours `null` en base** après le premier import, alors que `parseRating` était
   déjà validé isolément : `toFilm` appelait bien tous les autres `setXxx`, mais avait simplement
   oublié `film.setRating(parseRating(dto.getRating()))`. Trouvé seulement en interrogeant une vraie
   fiche film via `MenuApp` et en comparant avec le JSON source — a nécessité un second import complet
   une fois corrigé.
8. **Prompts console affichés dans le désordre** par rapport à la saisie utilisateur : un
   `System.out.print(message)` sans `\n` n'est pas garanti d'être flush avant qu'un
   `Scanner.nextLine()` ne bloque en attente de saisie. Fix : `System.out.flush()` explicite après
   chaque prompt (`Saisie.lireEntier`/`lireTexte`).
9. **`ExceptionInInitializerError` au lieu de `PersistenceException`** (étape 18) : `EntityManagerProvider`
   construisait `ENTITY_MANAGER_FACTORY` dans un champ `static final`, donc au chargement de la classe.
   Une exception dans un bloc d'initialisation statique est toujours enveloppée par la JVM (règle JLS),
   quel que soit son type d'origine — le `catch (PersistenceException e)` d'`ImportApp`/`MenuApp` ne
   pouvait donc jamais l'attraper. Pire : la classe reste marquée en échec pour le reste de l'exécution
   (`NoClassDefFoundError` ensuite), aucune reprise possible même si MariaDB redémarre. Fix : construction
   paresseuse (section 2).
10. **`RollbackException` confondue avec une coupure de connexion** (étape 18) : `jakarta.persistence.
    RollbackException` (levée sur un commit en échec, ex. contrainte unique violée) hérite aussi de
    `PersistenceException`. Le message initial d'`ImportApp` ("Impossible de se connecter...") s'affichait
    donc aussi sur une violation de contrainte (ex. ré-import sans vider la base, avant l'étape 19) alors
    que MariaDB tournait très bien. Fix : message honnête (`e.getMessage()`) plutôt qu'une cause supposée.
11. **Risque de `LazyInitializationException` introduit par l'import idempotent** (étape 19), jamais
    déclenché mais trouvé en revue avant test : `chargerExistant` précharge des `Film` via `findAll()`,
    détachés dès la fermeture de leur `EntityManager` ; `Film.roles` étant `LAZY`, y accéder ensuite
    aurait planté. Résolu par construction en limitant la collecte des rôles à `nouveauxFilms` (jamais
    rechargés depuis la base), qui répondait de toute façon déjà à la décision métier voulue (rôles
    persistés seulement pour les films nouveaux).

## 8. Les tests unitaires — `FilmMapperTest` (hors périmètre du projet)

⚠️ Contrairement à tout ce qui précède, cette partie n'est pas une exigence du projet école — c'est un
approfondissement personnel ajouté après la fin fonctionnelle du projet (voir `PLAN.md`, étape 13).

`FilmMapperTest` (`src/test/java/fr/diginamic/cinema/mapper/FilmMapperTest.java`) teste directement les
méthodes de `FilmMapper` décrites en 4.2. Pour ça, toutes ses méthodes sont passées de `private` à
package-private (sauf `toFilm`, déjà `public`) : un test dans `src/test/java` ne peut pas appeler une
méthode `private` d'une autre classe, même dans le même package — le passage à package-private est la
pratique standard en Java pour ce cas de figure, l'encapsulation restant intacte en dehors de
`fr.diginamic.cinema.mapper`.

38 tests JUnit 5, répartis en plusieurs familles :

- **Méthodes de parsing pures** (`parseDate`, `parseRating`, `parseTaille`, `parseAnneeRange`,
  `cleDedoublonnage`) — cas nominaux et cas limites (`null`, vide). Plusieurs cas sont des **tests de
  non-régression** directement issus des bugs réels de la section 7 : l'espace fine insécable dans
  `parseTaille` (bug #5), et l'insensibilité à la casse/aux accents/aux espaces superflues dans
  `cleDedoublonnage` (bug #6).
- **`toFilm` : fusion `anneeDebut`/`anneeFin` sur les doublons** — la règle métier la plus subtile du
  mapper, avec un cas repris directement de l'exemple réel documenté (`tt0072562`, 7 occurrences).
- **`toFilm` : gardes sur les clés optionnelles absentes** (`pays`/`langue`/`lieuTournage`) —
  non-régression sur le bug #2 de la section 7.
- **`toPersonne` : garde sur `lieuNaissance` vide** — non-régression sur le piège documenté en 4.2.
- **`toPays`/`toLangue`/`toGenre`/`toLieuNaissance`** : construction correcte des champs (`nom`/
  `libelle` trim).
- **`dedupe` en isolation** : le mécanisme de cache générique lui-même (deux clés équivalentes après
  normalisation renvoient la même instance sans reconstruire).
- **`toRole` / `isPrincipal`** : construction et dérivation du booléen `principal`.

Lancer avec `mvn test` (voir `README.md`).

## 9. Ordre de lecture suggéré si tu veux reparcourir le code toi-même

1. `entity/` — comprendre le modèle de données avant tout le reste.
2. `persistence/EntityManagerProvider` + `META-INF/persistence.xml` — comment on parle à la base.
3. `dao/Dao`, `dao/AbstractDao` — le socle générique, puis `dao/FilmDao`/`PersonneDao`/`RoleDao` pour
   les requêtes spécifiques aux recherches.
4. `json/` — les DTOs, en parallèle d'un coup d'œil à `films.json` lui-même.
5. `mapper/DedupCaches` puis `mapper/FilmMapper` (dans l'ordre du fichier : `toFilm` en premier, les
   `parseXxx` en dernier) — le cœur de la logique de conversion.
6. `service/ImportService` — comment tout s'assemble pour l'import.
7. `app/ImportApp` — le point d'entrée qui déclenche tout ça.
8. `service/RechercheService`, `console/Saisie`, `console/Affichage`, `console/MenuActions`,
   `app/MenuApp` — le second flux, plus simple, qui consomme les données déjà importées.
9. `mapper/FilmMapperTest` (optionnel, hors périmètre) — pour voir les méthodes de la section 4.2 mises
   à l'épreuve avec des cas concrets, y compris certains bugs historiques rejoués.

Pour l'historique complet des décisions, bugs et raisonnements derrière chaque choix, voir `PLAN.md`.
