# PLAN.md — Projet Internet Movie DataBase (JPA)

> **Instruction pour Claude** : ce fichier, ainsi que `README.md` et `CODE_EXPLANATION.md`, sont les seuls fichiers du projet que tu modifies directement (Write/Edit) — tous les autres sont écrits par l'utilisateur (projet d'apprentissage, cf. mémoire `feedback_learning_mode`). À la fin de chaque session de travail significative, mets à jour ce même fichier : coche les cases faites, ajoute les décisions prises, mets à jour le pourcentage d'avancement et la section "Prochaines actions". Ne réécris pas l'historique déjà présent, complète-le.

## a) Plan de travail général

Projet école (Diginamic) : import d'un dataset IMDb (`films.json`, ~21.7 Mo, 2748 films) dans MariaDB via JPA/Hibernate, puis application console avec menu de recherche (7 opérations). Contraintes clés : contraintes uniques sur lieu de naissance/pays/langue/genre, `LocalDate`/`LocalDateTime` pour les dates, un DAO par entité, couche service, Javadoc partout, pas de code redondant.

Architecture en couches (périmètre du cahier des charges uniquement — voir section b, étapes 13+, pour les approfondissements personnels hors périmètre : tests, CI, Docker, import idempotent, présentation du dépôt...) :
1. Document de conception (diagrammes + DDL)
2. Base de données MariaDB
3. Entités JPA
4. Fournisseur d'`EntityManager`
5. Couche DAO (générique + un DAO par entité)
6. DTOs JSON (miroir brut de `films.json`)
7. Mapper (DTO → entités, avec dédoublonnage)
8. `ImportService` (orchestration import)
9. `RechercheService` (les opérations de recherche)
10. Application menu console
11. Application d'import (point d'entrée)
12. Documentation (`README.md`, `CODE_EXPLANATION.md`)

Règles de collaboration (mémoire) :
- Projet d'apprentissage : Claude guide/relit, n'écrit pas le code à la place de l'utilisateur sauf demande explicite.
- Claude ne modifie jamais les fichiers du projet directement — sauf `PLAN.md`, `README.md` et `CODE_EXPLANATION.md`.
- Dédoublonnage : les entités "lookup" gardent l'equals/hashCode par défaut ; le dédoublonnage se fait via des `Map<String, Entity>` dans la logique d'import, pas au niveau entité.

## b) Mise en œuvre par étapes

### Étape 1 — Conception ✅ terminé
- `conception/document_conception.md` : diagramme de classes, diagramme ER, script DDL complet (pays, langue, genre, lieu_naissance, personne, film, role, film_genre, film_realisateur).
- Constats sur les données : 2748 films, 38 ids dupliqués, notes au format français (virgule), années sous forme d'intervalle, champs optionnels manquants.

### Étape 2 — Base de données ✅ terminé
- MariaDB/XAMPP. DDL exécuté via `mysql.exe` en CLI (bug connu du linter phpMyAdmin sur la syntaxe `CHECK` nommée) :
  `Get-Content script.sql | & "C:\xampp\mysql\bin\mysql.exe" -u root`

### Étape 3 — Entités JPA ✅ terminé
- `Pays`, `Langue`, `Genre`, `LieuNaissance` : entités "lookup" simples, id `Integer` auto-incrémenté, colonne `nom`/`libelle` unique.
- `Personne` : clé naturelle `String` (id IMDb), `final` + `@Setter(AccessLevel.NONE)` + `@RequiredArgsConstructor` + `@NoArgsConstructor(force = true)`. `dateNaissance` en `LocalDate`, `taille` en `BigDecimal`, `lieuNaissance` en `@ManyToOne(fetch = LAZY)`.
- `Role` : id `Integer` auto-incrémenté, `film`/`personne` en `@ManyToOne` avec `@JoinColumn(nullable = false)`.
- `Film` : même pattern de clé naturelle que `Personne`. `rating` en `BigDecimal`, `plot` en `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` (l'incertitude notée ici s'est confirmée : `@Lob` générait `LONGTEXT` au lieu du `TEXT` réel de la DDL, corrigé pendant le premier import — voir étape 11), `anneeDebut`/`anneeFin` en `Integer` (nullable), `genres`/`realisateurs` en `@ManyToMany`, `roles` en `@OneToMany(mappedBy = "film")`.

### Étape 4 — Fournisseur d'EntityManager ✅ terminé
- `fr.diginamic.cinema.persistence.EntityManagerProvider` (`persistence.xml` avec `hibernate.hbm2ddl.auto=validate` puisque le schéma est écrit à la main).

### Étape 5 — Couche DAO ✅ terminé
- `Dao<T, ID>` (interface générique) + `AbstractDao<T, ID>` (implémentation générique, `Class<T> entityClass` stocké pour contourner l'effacement de type).
- Gestion transactionnelle factorisée via `executeInTransaction` (deux surcharges : `Function<EntityManager, R>` pour les retours, `Consumer<EntityManager>` pour `delete`).
- 7 DAOs concrets : `PaysDao`, `LangueDao`, `GenreDao`, `LieuNaissanceDao`, `PersonneDao`, `FilmDao`, `RoleDao`.
- ✅ **Point résolu à l'étape 9** : le risque de `LazyInitializationException` sur les associations `LAZY` (chaque appel du DAO ouvre/ferme son propre `EntityManager`) a été traité au cas par cas une fois les besoins réels des 6 recherches connus — `RoleDao.findByFilmId` utilise un `JOIN FETCH r.personne` ciblé, et le reste du code n'accède jamais à un champ `LAZY` en dehors d'un DAO. Pas de correctif générique, comme prévu ici.

### Étape 6 — DTOs JSON ✅ terminé
- Package `fr.diginamic.cinema.json`, suffixe `Json` pour ne pas entrer en conflit avec les entités.
- `PaysJson`, `LieuTournageJson`, `NaissanceJson`, `PersonneJson`, `RoleJson`, `FilmJson` — tous en champs bruts (`String`/DTO imbriqué/`List`) qui miroitent exactement les clés du JSON, pour le binding Jackson automatique.

### Étape 7 — Mapper (DTO → entités) ✅ terminé
- Squelette créé : `fr.diginamic.cinema.mapper.FilmMapper` (méthodes statiques, aucune persistance) + `fr.diginamic.cinema.mapper.DedupCaches` (regroupe les `Map<String, Entity>` de dédoublonnage : `pays`, `langues`, `genres`, `lieuxNaissance`, `personnes`, bientôt `films`).
- Décision : le mapper est un pur transformateur (pas d'accès DB) ; `DedupCaches` est créé et possédé par `ImportService`, passé en paramètre au mapper.
- ✅ `parseDate` : écrit et validé. Essaie une liste de `DateTimeFormatter` (anglais `Locale.ENGLISH`, français `Locale.FRENCH`) dans une boucle, catch `DateTimeParseException` (pas `Exception` généraliste) pour passer au suivant, `null` si aucun ne correspond.
  - Formats réels trouvés dans `films.json` : `"Month Day Year "` (majoritaire, ~50 169), `"Month Day "` sans année (~1 441), `"Year "` seule (~666), `"Month Year "` (~67), une valeur en français `"17 août 1943 "` (une même personne récurrente), et ~10 568 chaînes vides.
  - Décision utilisateur : ne jamais inventer un jour/mois manquant → retourner `null` pour tout ce qui n'est pas une date complète.
- ✅ `parseRating` : écrit et validé. Normalise virgule → point (no-op sur valeurs à point) avant `new BigDecimal(...)` ; vide/`null` → `null`. Pas de try/catch superflu (formats réels vérifiés exhaustivement : point, virgule, vide, rien d'autre).
- ✅ `parseAnneeRange` : écrit et validé. Sépare sur `–` (tiret demi-cadratin, **pas** `-`) ; une seule partie → `debut` seul (`fin = null`) ; deux parties → `debut`/`fin`. Retourne le record `AnneeRange(Integer debut, Integer fin)`.
- ✅ `toPays`, `toLangue`, `toGenre`, `toLieuNaissance` : écrits et validés, pattern identique `caches.xxx.computeIfAbsent(clé, k -> { construire + retourner })`.
- ✅ `toPersonne` : écrit et validé. Clé de dédoublonnage = id IMDb. Piège trouvé et corrigé : `lieuNaissance` est une chaîne vide dans ~10 840 cas dans le JSON → il faut vérifier qu'elle n'est pas vide avant d'appeler `toLieuNaissance`, sinon toutes les personnes sans lieu de naissance connu se retrouvent dédoublonnées sur un faux `LieuNaissance` de libellé `""`. Utilise aussi un nouveau helper `parseTaille` (écrit et validé : formats `"1.70 m"` / `"1,70 m"`, même technique de normalisation que `parseRating`).
- ✅ `toRole` + `isPrincipal` : écrits et validés. `toRole` ne dédoublonne pas (chaque rôle est une ligne de casting distincte), seule la `Personne` associée l'est via `toPersonne`. `isPrincipal` compare les id (`PersonneJson.getId()`) entre l'acteur du rôle et la liste `castingPrincipal`.
- ✅ `toFilm` : écrit et validé. Structure `get` + `if (film == null) { construire tout } else { fusionner anneeDebut/anneeFin }` (voir décision Option 2 ci-dessous). Bug corrigé au passage : un appel à `film.setXxx(...)` avait été laissé avant le `if (film == null)`, provoquant un `NullPointerException` sur tout id rencontré pour la première fois.

**Mapper terminé à 100 %.**

**⚠️ Découverte importante — doublons de films (38 ids, cf. conception) :**
Diff complet effectué entre plusieurs occurrences du même id (ex. `tt0072562` "Saturday Night Live", trouvé **7 fois**). Résultat : `nom`, `rating`, `plot`, `langue`, casting sont identiques entre doublons ; seuls `url` (paramètre de tracking `?ref_=nm_flmg_t_N_act`) et surtout **`anneeSortie` diffèrent systématiquement** entre toutes les occurrences des 38 ids dupliqués (ex. `tt0072562` a 7 valeurs différentes : 1975–1979, 2019, 2020, 2013–2019, 2010, 2011–2018, 2022). Explication probable : le JSON a été scrapé une fois par page de filmographie d'acteur, et `anneeSortie` reflète les années de présence de CET acteur sur un show/série au long cours, pas la plage de diffusion globale — donc aucune des valeurs n'est "la bonne", ce n'est pas un bug de données à corriger.

**Décision utilisateur (2026-07-29)** : Option 2 retenue — dédoublonner les films par id comme les autres entités, mais fusionner l'intervalle d'années à chaque nouvelle rencontre du même id : `anneeDebut = min` des débuts vus, `anneeFin = max` des fins vues (une année seule sans `fin` compte aussi comme candidate pour le max, pas seulement pour le min).

Conséquences architecturales à ne pas oublier :
- `DedupCaches` a besoin d'un nouveau `Map<String, Film> films` (n'existe pas encore).
- `toFilm` ne peut pas utiliser `computeIfAbsent` comme les autres builders (qui ignore le lambda si la clé existe déjà) : il faut un `get` explicite puis une branche `if (film == null) { construire tout } else { fusionner seulement anneeDebut/anneeFin }`. Sur un doublon, les autres champs (pays, langue, genres, realisateurs, roles) de cette occurrence-là sont ignorés.
- `ImportService` doit éviter d'insérer deux fois le même `Film` en base : comme `toFilm` renvoie la même instance pour un id déjà vu, il faut n'ajouter le film à la liste "à persister" que la première fois qu'on le rencontre (pas à chaque doublon).

### Étape 8 — ImportService ✅ terminé
- ✅ `importer(Path jsonFile)` : lit le JSON, boucle sur chaque `FilmJson` en appelant `FilmMapper.toFilm(dto, caches)` (phase de mapping pure, aucune persistance), puis appelle `persister(caches)` une seule fois à la fin.
- ✅ `lireFilms` : `Files.readString` + Jackson `ObjectMapper.readValue` avec un `CollectionType` (`List<FilmJson>`), pas de streaming (fichier de 21.7 Mo, jugé trop petit pour le justifier).
- ✅ `persister(DedupCaches caches)` : persiste chaque map de `caches` via son DAO (`save`, qui appelle `em.persist(...)`), dans l'ordre imposé par les FK : `pays`/`langues`/`genres`/`lieuxNaissance` → `personnes` → `films` → `roles` (extraits de chaque `film.getRoles()` dans une liste à part, persistés en dernier).
- ✅ **Point ouvert résolu** : la question "neuf vs déjà persisté" ne se pose plus, car le design a été restructuré en deux phases bien séparées au lieu d'un persist-au-fil-de-l'eau :
  1. **Phase de mapping** : tous les `FilmJson` sont convertis via `toFilm`, en accumulant uniquement dans `caches` (aucun DAO appelé). À la fin de cette phase, chaque map de `caches` contient déjà exactement une instance par clé unique (grâce à `computeIfAbsent`/au dédoublonnage par id pour `toFilm`).
  2. **Phase de persistance** : chaque `.values()` de `caches` est persisté une seule fois, dans l'ordre FK. Comme aucune entité n'a jamais été persistée avant cette phase, tout est un `save`/`persist`, jamais un `update` — plus besoin de distinguer "neuf" de "déjà vu".
  - Vérifié que ça fonctionne sans `cascade` explicite sur les relations (`@ManyToOne`/`@ManyToMany`) : au moment où `filmDao.save(film)` s'exécute, les `Pays`/`Langue`/`Genre`/`Personne` référencés sont déjà persistés et committés (dans une transaction précédente, via `executeInTransaction`) et ont donc déjà un id réel — Hibernate n'a besoin que de cet id pour écrire les FK/lignes de jointure, pas d'une entité "managed" par l'EntityManager courant. Pas de risque de `LazyInitializationException` non plus ici (ce risque ne concerne que des entités chargées depuis la DB puis accédées après fermeture de leur EntityManager, ce qui n'arrive pas dans ce flux).
- Javadoc complétée sur la classe et les 3 méthodes.

### Étape 9 — RechercheService ✅ terminé
6 opérations du menu à couvrir : filmographie d'un acteur, casting d'un film, films entre 2 années, films communs à 2 acteurs, acteurs communs à 2 films, films entre années + acteur donné.

**✅ Les 6 méthodes de requête DAO sont écrites et validées** (toutes en JPQL, pattern `try (EntityManager em = ...)` sans transaction, cohérent avec `findAll`/`findById`) :
- `RoleDao.findByFilmId(String filmId)` — casting d'un film. `JOIN FETCH r.personne` : pas nécessaire pour éviter un crash (`Role.personne` est `@ManyToOne` donc `EAGER` par défaut, pas `LAZY`), mais évite le problème des N+1 requêtes (une requête séparée par rôle sinon).
- `FilmDao.findByActeurId(String personneId)` — filmographie d'un acteur. `JOIN f.roles` + `DISTINCT` (un acteur peut avoir plusieurs rôles dans un même film).
- `FilmDao.findByAnneeRange(int debut, int fin)` — films entre 2 années. **Décision (2026-07-29)** : filtre uniquement sur `anneeDebut` (Option A, plus simple), ignore `anneeFin` — ne traite pas différemment les séries/shows dont la présence s'étend sur plusieurs années.
- `FilmDao.findCommunsEntreActeurs(String personneId1, String personneId2)` — films communs à 2 acteurs. Double `JOIN f.roles` (deux alias `r1`/`r2`, un par acteur) + `DISTINCT`.
- `PersonneDao.findCommunsEntreFilms(String filmId1, String filmId2)` — acteurs communs à 2 films. **Point d'architecture découvert** : `Personne` n'a pas de collection `roles` (contrairement à `Film.roles`), donc la requête part de `Role` plutôt que de `Personne`, avec une sous-requête (`r.personne IN (SELECT r2.personne FROM Role r2 WHERE r2.film.id = :filmId2)`) plutôt qu'un double join classique.
- `FilmDao.findByActeurIdAndAnneeRange(String personneId, int debut, int fin)` — films entre années + acteur donné. Combine le join de `findByActeurId` et le filtre de `findByAnneeRange`.

- ✅ `RechercheService` écrit : 6 méthodes publiques (`filmographieActeur`, `castingFilm`, `filmsEntreAnnees`, `filmsCommuns`, `acteursCommuns`, `filmsActeurEntreAnnees`), chacune une simple délégation à la méthode DAO correspondante (DAOs en champs `private final`, même pattern que `ImportService`). Javadoc volontairement courte (pas de duplication du raisonnement déjà documenté sur la méthode DAO) + `@see` pointant vers la méthode DAO correspondante.

### Étape 10 — Application menu ✅ terminé
- `MenuApp` : boucle `do/while` avec `Scanner`, menu numéroté **1 à 6** pour les recherches, **0 pour quitter** (décision : 0 plutôt que 7, pour que le numéro de sortie reste stable si de nouvelles options sont ajoutées plus tard — pas besoin de renuméroter).
- `lireChoix`/`lireEntier` : lecture systématique via `scanner.nextLine()` (jamais `scanner.nextInt()`) partout dans la classe, pour éviter le bug classique du `\n` résiduel dans le buffer quand on mélange `nextInt()`/`nextLine()`. `lireEntier(Scanner, String message)` est un helper générique (reprompt en boucle tant que l'entrée n'est pas un entier valide) réutilisé à la fois par `lireChoix` (avec en plus sa propre validation de plage 0–6) et par tous les prompts d'année.
- Helpers d'affichage (`afficherFilm`, `afficherRole`, `afficherPersonne`) plutôt que `toString()` sur les entités : aucune entité n'a de `@ToString`, et certains champs (`Film.genres`/`realisateurs`/`roles`, `Personne.lieuNaissance`) sont `LAZY` — un `toString()` auto-généré qui y toucherait risquerait un `LazyInitializationException` une fois l'`EntityManager` fermé (ce qui est toujours le cas au moment où `MenuApp` reçoit les résultats de `RechercheService`). Les helpers n'affichent donc que les champs sûrs (chargés `EAGER` ou colonnes simples).
- `menuCastingFilm` affiche des `Role` (pas des `Personne`) : le casting d'un film, c'est le personnage joué (`characterName`, `principal`), pas juste l'acteur — l'info vient de `Role`, `role.getPersonne()` étant sûr d'accès grâce au `JOIN FETCH` déjà présent dans `RoleDao.findByFilmId`.

**🔧 Refactor (2026-07-30)** — `MenuApp` initialement une seule classe (~200 lignes) mélangeant trois responsabilités (orchestration, lecture console, affichage). Séparé en un nouveau package `fr.diginamic.cinema.console`, sur le même principe qu'un package par responsabilité déjà utilisé ailleurs (`dao`, `mapper`, `service`...) :
- `Saisie` — `lireChoix`, `lireEntier`, `lireTexte` (lecture clavier).
- `Affichage` — `afficherMenu`, `afficherFilm`, `afficherRole`, `afficherPersonne` (tout l'affichage console).
- `MenuActions` — les 6 méthodes `menuXxx` (une par option), chacune lisant sa saisie via `Saisie`, appelant `RechercheService`, puis affichant via `Affichage`.
- `MenuApp` (reste dans `app`) : réduit à `main()` seul — construit `Scanner`/`RechercheService`, boucle et dispatch vers `Affichage`/`Saisie`/`MenuActions`, plus aucune logique propre.

**✅ Les 6 options testées manuellement une par une contre la vraie base (2026-07-30)**, avec des données croisées et vérifiables entre elles (toutes construites autour de `nm0000001` / Fred Astaire et de ses films avec un vrai rôle, pas juste `castingPrincipal`) :
- Filmographie d'un acteur : `nm0000001` → `tt0082449`, `tt0077898`, `tt0077536`, `tt0076851`, `tt0075971` (5 films, un de plus que prévu au départ — prédiction initiale incomplète, pas un bug : `tt0075971` avait été manqué en parcourant le JSON à la main).
- Casting d'un film : `tt0082449` → casting conforme au JSON.
- Films entre deux années : 1977–1981 → au moins les 5 films ci-dessus.
- Films communs à deux acteurs : `nm0000001` + `nm0913738` → `tt0082449`.
- Acteurs communs à deux films : `tt0082449` + `tt0077898` → `nm0000001` présent.
- Films d'un acteur entre deux années : `nm0000001`, 1977–1981 → les 5 mêmes films que la filmographie complète, correctement restreints à la plage.

Plus aucun bug connu ouvert sur `MenuApp`/`RechercheService`.

**🔍 Revue de code complète du projet (2026-07-30)**, une fois le projet fonctionnellement terminé : relecture exhaustive des 31 fichiers Java, comparés aux exigences de départ (section a — Javadoc partout, un DAO par entité, contraintes uniques, pas de code redondant), aux bonnes pratiques, et vérification des hypothèses de nullabilité directement contre `films.json` (comptages `grep` plutôt que suppositions). 6 points trouvés, tous corrigés :

1. **Javadoc de classe manquante sur `RechercheService`** — seule classe du projet sans doc de classe, incohérent avec sa sœur `ImportService` qui en a une. Fix : doc de classe ajoutée, résumant les 6 opérations exposées.
2. **`@AllArgsConstructor` inutilisé sur `Pays`/`Langue`/`Genre`/`LieuNaissance`/`Role`** — vérifié en recherchant tous les `new Xxx(...)` du projet : ces 5 entités ne sont construites que via leur constructeur vide + setters, jamais via l'all-args généré par Lombok. Code mort. Fix : annotation retirée des 5 classes. (`Film`/`Personne` ne sont pas concernés : leur `@RequiredArgsConstructor` sur l'id final est bien utilisé dans `FilmMapper`.)
3. **Risque de `NullPointerException` latent dans `FilmMapper.toFilm`/`parseAnneeRange`** — `parseAnneeRange` renvoyait `null` (l'objet `AnneeRange` entier, pas juste ses champs) quand `dto.getAnneeSortie()` est `null`, mais `toFilm` déréférençait ensuite `anneeRange.fin()` sans vérification. Jamais déclenché avec le JSON actuel (vérifié : les 2748 films ont tous la clé `anneeSortie`, comptage `grep` exact), mais même famille de bug que les clés manquantes `pays`/`langue`/`lieuTournage` déjà trouvées et corrigées à l'étape 11 (bugs #2-3) — celles-là avaient été protégées par une garde, celle-ci non, uniquement parce qu'elle n'avait jamais été mise en défaut par les vraies données. Fix : `parseAnneeRange` renvoie désormais `new AnneeRange(null, null)` au lieu de `null` quand la valeur brute est absente, cohérent avec le fait que les champs de `AnneeRange` sont déjà conçus pour tolérer `null` individuellement.
4. **Javadoc manquante sur les 7 constructeurs de DAO** (`PaysDao`, `LangueDao`, `GenreDao`, `LieuNaissanceDao`, `PersonneDao`, `FilmDao`, `RoleDao`) — gap identique sur les 7 (pas une incohérence entre eux, un vrai manque vis-à-vis de la règle « Javadoc partout »). Fix : une ligne de Javadoc ajoutée à chacun.
5. **Style de Javadoc incohérent sur `EntityManagerProvider`** — `getEntityManagerFactory()`/`getEntityManager()` n'avaient qu'un tag `@return`, sans phrase descriptive au-dessus, contrairement à la convention utilisée partout ailleurs dans le projet (DAOs/services : toujours une phrase descriptive suivie de `@param`/`@return`). Fix : phrase descriptive ajoutée aux deux méthodes.
6. **Duplication mineure dans `FilmMapper`** — `toPays`/`toLangue`/`toGenre`/`toLieuNaissance` suivaient un pattern identique (trim → `cleDedoublonnage` → `computeIfAbsent` → construction + set). Fix : extraction d'un helper générique `dedupe(Map<String, T> cache, String raw, Function<String, T> builder)` ; les 4 méthodes n'en sont plus que des appels d'une ligne avec leur lambda de construction propre à chaque entité.

Le fichier réordonné a été collé par-dessus une copie de sauvegarde temporaire (`FilmMapperOld.java`, le temps de vérifier l'équivalence) ; une fois `ImportService` reprécâblé sur `FilmMapper.toFilm` et `FilmMapperOld` confirmé sans plus aucune référence, ce fichier de sauvegarde a été supprimé (2026-07-30).

**Aucun autre écart trouvé** : les 4 contraintes uniques (pays/langue/genre/lieu_naissance) sont cohérentes entre les entités et `sql/schema.sql`, un DAO par entité respecté (7/7), `LocalDate` utilisé correctement pour `dateNaissance`, et tous les blocs JSON potentiellement absents (`genres`/`realisateurs`/`roles`/`castingPrincipal`/`naissance`) vérifiés présents dans les 2748 films du fichier actuel — donc sans risque avec ce jeu de données précis, même si pas tous gardés explicitement dans le code comme le sont `pays`/`langue`/`lieuTournage`/`anneeSortie` (point 3 ci-dessus).

### Étape 11 — Application d'import ✅ terminé (ImportApp fonctionnel)
Package `fr.diginamic.cinema.app` créé (choix : package dédié aux points d'entrée plutôt que racine, car il y en aura deux — import et menu). `ImportApp` a un `main` qui appelle `new ImportService().importer(...)`, avec un message de progression et un chronométrage. Signature initiale `static void main()` sans `public`/`String[] args` (JEP 512, finalisé en Java 25, pas juste preview) — fonctionnait mais forme non conventionnelle. **Revenu à la signature classique `public static void main(String[] args)` le 2026-07-30** (voir décision de rétrogradation Java 25 → 21 ci-dessous), qui n'a besoin d'aucune fonctionnalité spécifique à une version récente. (Note historique : au moment de l'écriture d'`ImportApp`, `MenuApp` était encore une classe vide — voir étape 10, complétée depuis, pour son implémentation.)

**✅ Premier import complet réussi le 2026-07-29, en 317 s (2748 films).** Tout un lot de bugs réels trouvés et corrigés en testant contre les vraies données (voir liste ci-dessous) — la plupart n'étaient pas détectables par simple relecture de code, seulement en important pour de vrai.

**🐛 Bugs trouvés et corrigés pendant le premier import réel :**
1. `persistence.xml` sans aucun `<class>` déclaré → Hibernate ne scanne pas les entités automatiquement sur une persistence-unit `RESOURCE_LOCAL` hors conteneur Jakarta EE. Fix : lister les 7 `<class>` explicitement.
2. `dto.getLieuTournage()` peut être `null` (**589 films sur 2748** n'ont pas du tout la clé `lieuTournage` dans le JSON, pas juste une valeur `null` explicite) → `NullPointerException` dans `toFilm`. Fix : garde `if (lieuTournage != null)`.
3. Même chose pour `dto.getPays()` (**18 films** sans la clé) et `dto.getLangue()` (**19 films** sans la clé) → gardes ajoutées. Leçon retenue : vérifier l'*absence de clé*, pas seulement une valeur `null` explicite, quand on sonde le JSON avant d'écrire le mapper.
4. `Role.characterName` sans `@Column(name = "character_name")` → Hibernate cherchait une colonne `characterName` (pas de conversion automatique camelCase → snake_case sans stratégie de nommage configurée) alors que la colonne DDL est `character_name`. Fix : ajout du `name` explicite.
5. `Film.plot` en `@Lob` générait `LONGTEXT` (incertitude déjà notée à l'étape 3, confirmée) alors que la colonne DDL est `TEXT` → échec de la validation de schéma au démarrage. Fix : remplacé `@Lob` par `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` (import `org.hibernate.annotations.JdbcTypeCode` / `org.hibernate.type.SqlTypes`), qui correspond exactement au type réel de la colonne.
6. `parseTaille` : la parenthèse d'extraction (`valeurMetrique`) était calculée mais jamais utilisée (bug de copier-coller, ligne utilisait encore `trimmed`) → corrigé.
7. `parseTaille` : ~1294 tailles en virgule utilisent un **espace fine insécable** (` `, convention typographique française) entre la valeur et l'unité, pas une espace normale → invisible à l'œil, cassait le `.replace(" m", "")`. Fix : normalisation ` ` → espace normale en tout début de méthode.
8. **Dédoublonnage des entités "lookup" (`Pays`/`Langue`/`Genre`/`LieuNaissance`) incomplet** — 3 vagues de correctifs successives, toutes découvertes en conditions réelles (impossible à prévoir par simple lecture du JSON) :
   - Espaces superflus non retirées avant utilisation comme clé (`"Magog, Québec, Canada "` avec espace finale vs sans) → `Map` Java les traite comme deux clés différentes, mais la collation MariaDB (`utf8mb4_general_ci`) les considère comme la même valeur pour la contrainte unique → `ConstraintViolationException` à l'insertion de la deuxième.
   - Différences de casse (`"Newcastle upon Tyne..."` vs `"Newcastle Upon Tyne..."`) → même souci, la collation `_ci` (case-insensitive) les traite comme identiques.
   - Différences d'accents (`"Montreal, Quebec, Canada"` vs `"Montréal, Québec, Canada"`) → la collation `_general_ci` de MariaDB est *aussi* insensible aux accents.
   - **Fix final** : helper partagé `cleDedoublonnage(String)` (trim + suppression des diacritiques via `Normalizer.normalize(..., NFD)` + regex `\p{M}` + minuscules), utilisé comme clé de `computeIfAbsent` dans les 4 builders, tout en gardant la valeur d'origine (première rencontrée) comme `nom`/`libelle` réellement stocké.
   - **Leçon générale retenue** : une clé de dédoublonnage en mémoire doit respecter la même notion d'égalité que la contrainte unique en base (collation), sinon des doublons "invisibles" pour Java passent au travers et cassent au moment de la persistance.
9. Nettoyage de la base entre chaque tentative : `TRUNCATE` échoue tant qu'une contrainte FK référence la table (même vide) et `SET FOREIGN_KEY_CHECKS=0` ne semble pas persister entre les requêtes dans phpMyAdmin → utilisé `DELETE FROM` dans l'ordre des dépendances (enfants avant parents) à la place, qui n'a pas ce problème.

**🐛 Bug trouvé en testant `MenuApp` (2026-07-30), après le premier import :**
10. `Film.rating` toujours `null` en base, alors que `parseRating` est écrit et validé depuis l'étape 7 → le bug n'était pas dans `parseRating` mais dans `toFilm` (`FilmMapper.java`), qui appelait bien `setNom`/`setUrl`/`setPlot`/`setAnneeDebut`/`setAnneeFin`/etc. mais oubliait tout simplement l'appel `film.setRating(parseRating(dto.getRating()))`. Invisible à la relecture du mapper (chaque `parseXxx` avait été testé isolément), trouvé seulement en interrogeant une vraie fiche film via `MenuApp` et en comparant avec le JSON source. Fix : ajout de la ligne manquante dans le bloc `if (film == null)` de `toFilm`.
    - Conséquence : un simple correctif de mapper ne suffit pas, il faut aussi ré-importer — mais comme `ImportService` ne fait que des `persist()` (jamais de vérification d'existence), relancer l'import sans vider la base fait échouer sur les contraintes uniques des entités déjà présentes (pays, etc.) dès le premier lot inséré. Décision : accepter ce coût pour l'instant (garder le design persist-only, plus simple) plutôt que de rendre l'import idempotent (aurait demandé de pré-charger `DedupCaches` depuis la base existante et de distinguer `save`/`update` pour `Film`/`Personne`) — complexité jugée non justifiée en phase de dev/test, un simple script de reset suffit.
    - **Scripts SQL ajoutés en conséquence** : dossier `sql/` à la racine (pas dans `src/main/resources`, car ces scripts ne sont pas consommés par l'appli au runtime — ils s'exécutent en externe via `mysql.exe`, contrairement à `persistence.xml` qui doit être sur le classpath) :
      - `sql/schema.sql` — DDL complet extrait de `conception/document_conception.md` (bug de frappe corrigé au passage : `ONSTRAINT chk_film_annees` → `CONSTRAINT chk_film_annees`, faute de copie qui cassait `CREATE TABLE film`).
      - `sql/reset_db.sql` — 9 `DELETE FROM` dans l'ordre des dépendances FK (`role`/`film_genre`/`film_realisateur` → `film` → `personne` → `lieu_naissance`/`langue`/`genre`/`pays`), pour vider les données sans toucher au schéma entre deux imports de test.
    - **✅ Deuxième import réussi le 2026-07-30** après `DROP DATABASE` + réexécution de `schema.sql`, avec le fix `rating` en place — vérifié directement via `MenuApp` (`tt0082449` affiche maintenant `note : 6.3`, conforme au JSON, au lieu de `null`).

**🐛 Bug trouvé en testant l'option 6 de `MenuApp` (2026-07-30) :**
11. Prompts affichés dans le désordre par rapport à la saisie utilisateur (ex. `"Entre : "` n'apparaissait qu'après que l'utilisateur ait déjà tapé sa réponse, regroupé avec le prompt suivant `"Et : "`). Cause : `System.out.print(message)` (sans `\n`) n'est pas garanti d'être flush avant que `Scanner.nextLine()` ne bloque en attente de saisie — le texte reste dans le buffer du flux jusqu'à ce qu'un autre flush survienne (un `println` ailleurs, ou la fin du programme). Fix : `System.out.flush()` ajouté juste après chaque `System.out.print(message)` utilisé comme prompt, centralisé dans `lireEntier` (déjà existant) et dans un nouveau helper `lireTexte(Scanner, String message)` qui remplace les paires `System.out.print(...)` + `scanner.nextLine()` jusque-là dupliquées dans les 5 handlers à saisie de texte (`menuFilmographieActeur`, `menuCastingFilm`, `menuFilmsCommuns` ×2, `menuActeursCommuns` ×2, `menuFilmsActeurEntreAnnees`).
    - **✅ Revérifié après fix** : option 6 testée à nouveau (`nm0000481`, `1980`–`1983`), prompts affichés dans le bon ordre.

**✅ Les 6 options de `MenuApp` ont maintenant chacune été testées manuellement contre la vraie base de données** (filmographie d'un acteur, casting d'un film, films entre deux années, films communs à deux acteurs — testé avec `tt0082449`/`tt0077898`, acteur commun attendu `nm0000001` —, acteurs communs à deux films, films d'un acteur entre deux années). Plus aucun bug connu ouvert.

### Étape 12 — Documentation ✅ terminé
- **`README.md`** : stack technique, structure du projet, prérequis, mise en place de la base (`sql/schema.sql`/`sql/reset_db.sql`), comment lancer `ImportApp` puis `MenuApp`.
- **`CODE_EXPLANATION.md`** : explication détaillée de tout le code, organisée dans l'ordre du flux de données réel (entités → config JPA → DAOs → DTOs JSON → mapper → `ImportService`/`ImportApp` → `RechercheService` → `console/` → `MenuApp`), avec le *pourquoi* de chaque décision (clés naturelles vs techniques, LAZY/EAGER, dédoublonnage par clé normalisée, design en deux phases de l'import...), un résumé des bugs réels trouvés en testant, et un ordre de lecture suggéré du code.
- Décision : les deux documents sont en français, cohérent avec `PLAN.md`, la Javadoc et les noms de classes/méthodes du reste du projet.

### Étape 13 — Tests unitaires (hors périmètre du projet) ✅ terminé
⚠️ **Cette étape n'est pas une exigence du projet école** (cf. section a, « Contraintes clés » : aucune mention de tests unitaires parmi les livrables attendus). C'est un approfondissement personnel repris après la fin fonctionnelle du projet (toutes les exigences initiales sont déjà couvertes par les étapes 1 à 12), pour pratiquer JUnit sur du code réel plutôt que par nécessité du cahier des charges.

`FilmMapper` contient plusieurs méthodes statiques pures (aucune dépendance à la base), idéales pour du test unitaire classique — contrairement aux DAOs/`RechercheService`, qui demanderaient une vraie base (écartés pour l'instant : H2 en mémoire ne reproduirait pas la collation MariaDB `utf8mb4_general_ci` qui justifie `cleDedoublonnage`, coût de mise en place jugé disproportionné).

- **Dépendances ajoutées à `pom.xml`** : `org.junit.jupiter:junit-jupiter` (agrégateur incluant api + engine + params en une seule dépendance `test`), et `maven-surefire-plugin` explicitement épinglé (3.5.2) pour garantir la découverte des tests JUnit 5 (le support natif n'existe que depuis Surefire ≥ 2.22.0 ; sans version explicite, le projet dépendait de la version héritée de l'installation Maven locale).
- **Changement de visibilité sur `FilmMapper`** : à terme, toutes les méthodes de la classe sont passées de `private` à package-private (retrait du modificateur, `static` conservé) — `parseDate`, `parseRating`, `parseTaille`, `parseAnneeRange`, `cleDedoublonnage`, le record `AnneeRange`, `toPersonne`, `toPays`, `toLangue`, `toGenre`, `toLieuNaissance`, `dedupe`, `toRole`, `isPrincipal`. Seul `toFilm` reste `public` (déjà le cas, point d'entrée du mapper). Nécessaire car un test dans `src/test/java` ne peut pas appeler une méthode `private` d'une autre classe, même dans le même package — pratique standard en Java pour ce cas de figure (encapsulation déplacée au niveau du package plutôt que de la classe, toujours invisible en dehors de `fr.diginamic.cinema.mapper`).
- **`FilmMapperTest`** (`src/test/java/fr/diginamic/cinema/mapper/FilmMapperTest.java`), Javadoc de classe incluse. Deux familles de tests :
  1. **Tests des méthodes de parsing pures** (`parseDate`, `parseRating`, `parseTaille`, `parseAnneeRange`, `cleDedoublonnage`) — cas nominaux (formats point/virgule, anglais/français, année seule/intervalle) et cas limites (`null`, vide). Plusieurs cas correspondent directement à des bugs réels déjà trouvés et corrigés (`PLAN.md` étape 11) et sont donc des **tests de non-régression** sur des incidents précis : l'espace fine insécable dans `parseTaille` (bug #7), et l'insensibilité à la casse/aux accents/aux espaces superflues dans `cleDedoublonnage` (bug #8).
  2. **Tests de `toFilm` sur la fusion `anneeDebut`/`anneeFin` des doublons** — la règle métier la plus subtile du mapper (Option 2, étape 7), non couverte jusque-là : élargissement de `anneeFin` (max), réduction de `anneeDebut` (min), un cas combinant intervalle + année seule repris directement de l'exemple réel documenté (`tt0072562`, 7 occurrences), vérification que les autres champs (`nom`, etc.) restent ceux de la première occurrence sur un doublon, et que la même instance de `Film` est renvoyée (dédoublonnage par id). Un helper privé `creerFilmJson(id, nom, anneeSortie)` factorise la construction d'un `FilmJson` minimal valide (listes `genres`/`realisateurs`/`roles` vides plutôt que `null`, pour éviter une `NullPointerException` dans les boucles `for` de `toFilm` qui les parcourent directement).
  3. **Test de `toFilm` sur les gardes clé-absente** (`pays`/`langue`/`lieuTournage` jamais renseignés dans le `FilmJson`) — non-régression directe sur les bugs #2/#3 de l'étape 11 : vérifie qu'aucune `NullPointerException` n'est levée et que les champs correspondants du `Film` restent `null`.
  4. **Test de `toPersonne` sur la garde `lieuNaissance` vide** (`""`) — non-régression sur le piège documenté à l'étape 7 (~10 840 cas dans le vrai JSON) : un helper `creerPersonneJson(id, identite, lieuNaissance)` construit le `PersonneJson`/`NaissanceJson` minimal ; vérifie que `personne.getLieuNaissance()` reste `null` plutôt que de créer un `LieuNaissance` de libellé vide.
  5. **Tests de construction de `toPays`/`toLangue`/`toGenre`/`toLieuNaissance`** — un test par méthode, vérifiant uniquement que le champ (`nom`/`libelle`) est bien trim et assigné (pas de test de dédoublonnage répété quatre fois : ce mécanisme est déjà couvert une seule fois, directement, par le test de `dedupe` ci-dessous — le retester à chaque wrapper aurait été redondant).
  6. **Test de `dedupe` en isolation** — le mécanisme de cache générique lui-même, jusque-là seulement vérifié indirectement (via `cleDedoublonnage`, ou via le dédoublonnage par id de `toFilm`/`toPersonne`). Utilise un compteur d'appels au `builder` pour prouver que deux clés équivalentes après normalisation (`"Montreal"` puis `"MONTREAL"`) renvoient la même instance **sans reconstruire** (le builder n'est appelé qu'une fois).
  7. **Tests de `isPrincipal`** (acteur présent/absent de `castingPrincipal`) **et de `toRole`** (construction complète : `characterName`, `principal`, `film`, `personne` dédoublonnée).
- **✅ Tous les tests passent (38 tests au total).**

### Étape 14 — CI GitHub Actions (hors périmètre du projet) ✅ terminé
⚠️ Comme l'étape 13, ce n'est pas une exigence du projet école — approfondissement personnel pour la présentation du dépôt GitHub.

- **`.github/workflows/ci.yml`** : se déclenche sur `push`/`pull_request` vers `master`. Job unique sur `ubuntu-latest` : `actions/checkout@v4` (récupère le code), `actions/setup-java@v4` (JDK 21, distribution `temurin`, cache Maven activé), puis `mvn test`.
- Pas besoin de service MariaDB dans la CI : `FilmMapperTest` (les 38 tests, étape 13) ne teste que `FilmMapper`, qui ne touche jamais `EntityManagerProvider` ni aucune base — `mvn test` compile tout et fait tourner les tests sans dépendance externe.
- **✅ Premier run réussi le 2026-07-30** (succès, ~4 min 49 s).
- Badge de statut ajouté en haut de `README.md` (`https://github.com/malauzet/projet-jpa/actions/workflows/ci.yml/badge.svg`).

### Étape 15 — Recherche par nom (hors périmètre du projet) ✅ terminé
⚠️ Comme les étapes 13-14, ce n'est pas une exigence du projet école — approfondissement personnel (backlog section g), premier point traité le 2026-07-31), pour ne plus avoir à connaître l'id IMDb de mémoire.

- **`PersonneDao.findByIdentiteLike(String nom)` / `FilmDao.findByNomLike(String nom)`** : nouvelles méthodes JPQL, motif `"%" + nom.trim() + "%"` sur `LIKE`, `ORDER BY` pour la lisibilité. Pas de `LOWER()` ni de gestion manuelle des accents : `LIKE` respecte la même collation `utf8mb4_general_ci` que `=`, déjà exploitée pour `cleDedoublonnage` (section f).
- **`RechercheService.rechercherActeursParNom` / `rechercherFilmsParNom`** : délégation directe, même pattern que les 6 méthodes existantes.
- **Option 7 de `MenuApp`** (« Rechercher un acteur ou un film par nom ») plutôt qu'une intégration dans les 6 options existantes (décision utilisateur, 2026-07-31) : liste les ids correspondants à un nom partiel, à réutiliser ensuite dans les options 1-6. `MenuActions.menuRechercheParNom` demande d'abord un sous-choix acteur/film, puis le nom (partiel), puis affiche via `Affichage.afficherPersonne`/`afficherFilm`. `Saisie.lireChoix` étendu à la plage 0-7.
- **✅ Testé manuellement (2026-07-31)** contre la vraie base : `"astaire"` (acteur) retrouve `nm0000001` (Fred Astaire) et `nm3013608` (Fred Astaire Jr.) ; `"star wars"` (film) retrouve 9 films dont les épisodes numérotés, résultats triés alphabétiquement.
- **Process de collaboration** : cette étape a servi de rappel concret sur la règle « Claude guide/relit, n'écrit pas le code » (mémoire `feedback_learning_mode`) — Claude avait collé du code complet non sollicité en proposant la conception initiale ; corrigé en repassant en mode guidage conceptuel pour la suite. Deux points mineurs trouvés en revue (Javadoc de `Affichage.afficherMenu` non mise à jour après l'ajout de l'option 7, ligne blanche superflue dans `FilmDao`) et corrigés par l'utilisateur.

### Étape 16 — Affichage uniforme des résultats (hors périmètre du projet) ✅ terminé
⚠️ Comme les étapes 13-15, ce n'est pas une exigence du projet école — approfondissement personnel, traité le 2026-07-31 juste après l'étape 15 (recherche par nom), qui a fait ressortir le besoin d'un format lisible pour des listes de résultats parfois longues.

- Nouveau design de la CLI : barres de séparation (`————`) autour du menu principal (`afficherMenu`, déjà en place au démarrage de cette étape) et, désormais, autour de toute liste de résultats de recherche : barre, liste, barre, nombre de résultats trouvés (accord singulier/pluriel géré), barre de fin.
- **`Affichage.afficherResultats(List<T> resultats, Consumer<T> afficheur)`** : nouvelle méthode générique dans `Affichage`, qui centralise ce format une seule fois plutôt que de le dupliquer dans les 7 méthodes de `MenuActions`. Reprend le principe déjà utilisé dans `FilmMapper.dedupe` (paramètre fonctionnel qui dit *comment* traiter chaque élément, section f) du PLAN) — ici un `Consumer<T>` plutôt qu'un `Function`, passé comme référence de méthode (`Affichage::afficherFilm`, `::afficherRole`, `::afficherPersonne`).
- Constante `SEPARATEUR` extraite dans `Affichage`, pour ne pas dupliquer la chaîne de tirets une quatrième fois.
- Les 7 méthodes de `MenuActions` refactorées : leur bloc `if (resultats.isEmpty()) {...} else { for (...) {...} }` remplacé par un seul appel à `Affichage.afficherResultats(...)`.
- Process de collaboration : code entièrement écrit par l'utilisateur, Claude en guidage conceptuel (rappel de la règle `feedback_learning_mode` déjà noté à l'étape 15). Un exemple concret donné sur une seule méthode (`menuFilmographieActeur`), puis les 6 autres appliquées par l'utilisateur seul. Deux points relevés en revue sur `afficherResultats` et corrigés par l'utilisateur : compteur manuel redondant avec `resultats.size()` (simplifiable), Javadoc manquante (texte fourni par Claude, collé par l'utilisateur).

### Étape 17 — Dockerisation de MariaDB (hors périmètre du projet) ✅ terminé
⚠️ Comme les étapes 13-16, pas une exigence du projet école — accessibilité du dépôt pour un tiers (ex. recruteur) qui voudrait tester le projet sans installer XAMPP manuellement.

- `docker-compose.yml` à la racine : service unique `mariadb` (image `mariadb:12`), variables d'environnement `MARIADB_DATABASE=cinema` et `MARIADB_ALLOW_EMPTY_ROOT_PASSWORD=yes` (cohérent avec `persistence.xml`, root sans mot de passe), port `3306` mappé (aucune modification de `persistence.xml` nécessaire), volume nommé `mariadb_data` pour la persistance, bind-mount de `sql/schema.sql` vers `/docker-entrypoint-initdb.d/schema.sql` (exécuté automatiquement par l'image officielle au tout premier démarrage du volume).
- Décision Docker vs H2 déjà actée en section f) : Docker garde le vrai moteur MariaDB (et sa collation `utf8mb4_general_ci`), contrairement à H2 qui compare les chaînes différemment (`java.text.Collator`/ICU4J).
- Bug corrigé avant le premier lancement : la clé racine `volumes:` (déclaration du volume nommé) avait été collée avec une indentation de 2 espaces, la plaçant par erreur à l'intérieur de `services:` au lieu d'être une clé de premier niveau — trouvé en revue, corrigé par l'utilisateur.
- Installation de Docker Desktop faite au passage (absent jusque-là sur la machine) ; PATH non rafraîchi dans les terminaux déjà ouverts après l'installation (PowerShell et cmd.exe) — résolu en ouvrant une nouvelle fenêtre de terminal, sans besoin d'un redémarrage complet de Windows.
- **✅ Validé de bout en bout (2026-07-31)** : `docker compose config` (validation syntaxe), `docker compose up -d` (démarrage + exécution automatique de `schema.sql`, confirmée dans les logs du conteneur), puis `ImportApp` relancé contre cette nouvelle base et `MenuApp` retesté par l'utilisateur — fonctionnels.
- **✅ README.md mis à jour (2026-07-31)** avec l'option Docker en complément de l'installation manuelle XAMPP (`## Mise en place de la base de données` scindée en Option A Docker / Option B manuelle). À cette occasion, relecture complète du README comparée à l'état réel du projet : plusieurs décalages trouvés et corrigés (comptage "6 opérations" → 7 depuis l'étape 15, mention de `films.json` "non versionné" devenue fausse depuis son ajout au dépôt, menu "options 1 à 6" → 1 à 7 avec l'entrée manquante de l'option 7, section "Documentation complémentaire" qui ne référençait pas `CODE_EXPLANATION.md`).
- **⚠️ Point trouvé en marge de cette relecture (pas encore traité)** : `CODE_EXPLANATION.md` et `PLAN.md` sont tous les deux non trackés par Git (`git status` → `??`) — ils existent sur le disque mais n'ont jamais été commit. Le README pointe maintenant vers `CODE_EXPLANATION.md` : tant qu'il n'est pas commit, ce lien sera mort pour quelqu'un qui consulte le dépôt sur GitHub.

### Étape 18 — Vitesse d'import et gestion d'erreur au démarrage (hors périmètre du projet) ✅ terminé
⚠️ Comme les étapes 13-17, pas une exigence du projet école. Déclenché en creusant pourquoi l'import (Phase 1) et en testant la robustesse au démarrage (Phase 2), le 2026-07-31.

**Phase 1 — Vitesse d'import.** Cause identifiée en lisant le code (pas en devinant) : `ImportService.persister()` appelait `dao.save(entity)` en boucle, et `AbstractDao.save()` ouvrait une transaction + committait à *chaque* entité — des dizaines de milliers de commits/fsync individuels pour l'ensemble des 7 tables, alors que le fichier JSON lui-même (21.7 Mo) est trivial à lire. Aggravé par `GenerationType.IDENTITY` sur 5 des 7 entités (`Pays`/`Langue`/`Genre`/`LieuNaissance`/`Role`), qui empêche de toute façon tout batching JDBC réel avec cette stratégie.
- **`Dao.saveAll(Collection<T> entities)`** (+ implémentation dans `AbstractDao`) : persiste toute une collection dans **une seule transaction**, avec `flush()`/`clear()` du contexte de persistance tous les `BATCH_SIZE` (100) éléments pour ne pas le laisser grossir indéfiniment. Réutilise la surcharge `Consumer<EntityManager>` déjà existante d'`executeInTransaction`.
- **`ImportService.persister()`** : les 7 boucles `for (X x : ...) { dao.save(x); }` remplacées par 7 appels `dao.saveAll(...)`.
- **`save(T entity)` passé de `T` à `void`** (dans `Dao` et `AbstractDao`) au passage : les 7 appels dans `ImportService` jetaient déjà systématiquement la valeur de retour — trouvé en `grep`, aucun appelant ni override ailleurs dans le projet. `update(T entity)` gardé tel quel (jamais appelée aujourd'hui, mais anticipée pour l'import idempotent, prochaine étape du backlog).
- **✅ Mesuré (2026-07-31)** : 98 s pour les 2748 films (Docker), contre ~360 s avant — gain d'environ 3,6x. Le reste du temps s'explique par les inserts encore individuels au niveau JDBC (pas de vrai batching réseau possible sur les entités `IDENTITY`, et `hibernate.jdbc.batch_size` non configuré pour les entités à clé naturelle) — piste d'optimisation possible supplémentaire, non poursuivie ici.
- **Commit** `56852b8` — *"Optimisation de la vitesse d'import : persistance en lot (saveAll)"*. Anecdote de session : le premier essai de correction du message de ce commit (`git commit --amend`) a été fait *après* un premier push, créant un commit de merge parasite sur GitHub une fois repoussé sans y penser ; corrigé via `git reset --hard` sur le bon commit local puis `git push --force-with-lease` (sans risque ici, dépôt solo, rien poussé entre-temps par quelqu'un d'autre).

**Phase 2 — Gestion d'erreur au démarrage.** En creusant le point du backlog ("stack trace brute si MariaDB non lancé"), constat que la moitié du problème (`films.json` absent) était **déjà gérée** (`catch (IOException e)` existant dans `ImportApp`) ; seul le cas "MariaDB non démarré" restait ouvert.
- `ImportApp.main` : ajout d'un `catch (PersistenceException e)` à côté du `catch (IOException e)` existant, message clair.
- `MenuApp.main` : `try/catch (PersistenceException e)` placé **autour du `switch` à l'intérieur de la boucle**, pas autour de tout le `do/while` — pour que l'appli affiche l'erreur et redemande un choix plutôt que de s'arrêter, tolérant pour un programme interactif.
- **🐛 Bug trouvé en testant réellement (Docker coupé)** : le premier essai ne fonctionnait pas — l'exception réellement levée était `ExceptionInInitializerError`, pas `PersistenceException`, donc jamais attrapée. Cause : `EntityManagerProvider.ENTITY_MANAGER_FACTORY` était un champ `static final` construit au chargement de la classe (`<clinit>`) ; toute exception levée dans un bloc d'initialisation statique est enveloppée par la JVM (règle JLS), quel que soit son type d'origine. Plus grave : une fois l'échec survenu, la JVM marque la classe en échec **définitivement** pour le reste de l'exécution (`NoClassDefFoundError` ensuite) — aucune reprise possible même si MariaDB redémarre, quel que soit le `catch` écrit dans `MenuApp`.
  - **Fix** : `EntityManagerProvider` passé en construction paresseuse — `entityManagerFactory` n'est plus `final`, construit à la demande dans `getEntityManagerFactory()` (vérification `== null`), `getEntityManager()` passe désormais par cette méthode plutôt que par le champ directement, `close()` protégé par un null-check. Résultat : un échec de connexion redevient une `PersistenceException` normale, capturable, et **rien n'est mis en cache en cas d'échec** — l'appel suivant retente réellement la connexion.
- **✅ Revalidé de bout en bout (2026-07-31)** : `MenuApp` lancé avec Docker coupé → message propre affiché, menu qui continue (pas de crash) ; Docker relancé en cours de session → recherche retentée avec succès (`"marion"`, 12 résultats) sans redémarrer l'appli. Confirme que la reprise fonctionne réellement, pas seulement l'absence de crash immédiat.
- **🐛 Second bug trouvé en testant `ImportApp` sur une base déjà peuplée** : `jakarta.persistence.RollbackException` (levée sur un commit en échec, ex. contrainte unique violée) **hérite de `PersistenceException`** — le `catch (PersistenceException e)` d'`ImportApp` attrapait donc aussi bien une vraie coupure de connexion qu'une violation de contrainte (ex. relancer l'import sans vider la base, limitation déjà connue), avec le même message trompeur *"Impossible de se connecter..."* affiché même quand MariaDB tournait très bien. **Fix** : message générique et honnête (`"Échec de la persistance en base : " + e.getMessage()`) plutôt que d'affirmer une cause précise qu'on ne connaît pas — affiche désormais le vrai message d'erreur (ex. `Duplicate entry 'Spain' for key 'uk_pays_nom'`). `MenuApp` gardé tel quel (lecture seule sur les 7 options, donc un `PersistenceException` là-bas reste réellement une coupure de connexion).
- **✅ Import réel retesté sur base vide (2026-07-31)** : 92 s (cohérent avec les 98 s de la Phase 1), confirme que rien n'a cassé.
- **Commit** `bd9a359` — *"Gestion d'erreur propre au démarrage (base indisponible)"* (`ImportApp.java`, `MenuApp.java`, `EntityManagerProvider.java`).
- **README.md** : temps d'import mis à jour dans "Lancer l'import" (317 s → ~95 s), mention du passage en persistance par lot.

### Étape 19 — Import idempotent (hors périmètre du projet) ✅ terminé
⚠️ Comme les étapes 13-18, pas une exigence du projet école. Design décidé le 2026-07-31 (cf. section g pour le détail des 3 niveaux et le choix de l'option (c) pour `Role`), implémenté et testé le même jour.

- **Niveau 1 (`Personne`/`Film`)** : `ImportService.chargerExistant(DedupCaches caches)` — nouvelle méthode, appelée avant la boucle de mapping dans `importer()`, précharge `personneDao.findAll()`/`filmDao.findAll()` dans `caches.personnes`/`caches.films`, et retient leurs ids dans deux nouveaux `Set<String>` de `DedupCaches` (`personnesExistantes`, `filmsExistants`). Aucun changement dans `FilmMapper` : son `computeIfAbsent` (`Personne`) et sa logique get-ou-construire (`Film`, avec la fusion `anneeDebut`/`anneeFin` de l'étape 7) retrouvent ces entités préchargées exactement comme un doublon rencontré dans le run, la fusion s'applique donc automatiquement aux doublons *entre* deux runs. `persister()` filtre les `Map.values()` via ces `Set` avant `saveAll` (nouveaux) ; les `Film` déjà existants passent par `filmDao.update(...)` (jamais utilisée jusque-là, anticipée dès l'étape 13) pour répercuter une éventuelle fusion ; les `Personne` déjà existantes n'ont besoin d'aucune action (leurs champs ne changent jamais après création).
- **Niveau 2 (`Pays`/`Langue`/`Genre`/`LieuNaissance`)** : même principe, mais la clé métier est `nom`/`libelle` normalisé (`FilmMapper.cleDedoublonnage`), pas l'id `IDENTITY`. `cleDedoublonnage` passée de package-private à `public` (comme `toFilm`) pour être appelable depuis `ImportService`, package différent — évite de dupliquer la logique de normalisation plutôt que de la réécrire. Quatre `Set<String>` de plus dans `DedupCaches` (`paysExistants`, `languesExistantes`, `genresExistants`, `lieuxNaissanceExistants`), même préchargement/filtrage que le niveau 1, sans `update` nécessaire (ces champs ne changent jamais).
- **Niveau 3 (`Role`)** : décision (c) retenue (section g) — les rôles ne sont collectés/persistés que pour les films **nouveaux** ce run (`nouveauxFilms`, déjà construite pour le `filmDao.saveAll`), pas pour `caches.films.values()` dans son ensemble. Ce seul changement (une ligne) résout à la fois la décision métier *et* un bug latent découvert en cours de route : `Film.roles` est `@OneToMany` `LAZY` par défaut, et un `Film` préchargé par `chargerExistant` est détaché (son `EntityManager` fermé dès `findAll()` terminé, `try`-with-resources) — y accéder aurait levé une `LazyInitializationException`. En limitant la collecte à `nouveauxFilms` (jamais rechargés depuis la base), ce risque disparaît par construction.
- **✅ Testé de bout en bout (2026-07-31)** : import sur base déjà peuplée (sans vider au préalable) → pas de crash, 11 s (contre ~95 s à froid, cohérent avec le fait que presque tout est filtré). Relancé une seconde fois pour comparer : compte de lignes strictement identique sur les 7 tables entre les deux runs (`personne` 29091, `film` 2689, `pays` 39, `langue` 25, `genre` 27, `lieu_naissance` 6007, `role` 44488) — preuve directe qu'aucune donnée n'est dupliquée, pas seulement l'absence de crash.
- **Commit** `5d4525a` — *"Import idempotent : ré-importer sur une base déjà peuplée ne duplique plus rien"* (`DedupCaches.java`, `FilmMapper.java`, `ImportService.java`, `README.md`).
- **`CODE_EXPLANATION.md` mis à jour (2026-07-31)** pour refléter les étapes 15-19 (n'avait pas suivi depuis l'étape 12) : `saveAll`/`save` en `void`, recherche par nom, `afficherResultats`, construction paresseuse d'`EntityManagerProvider`, gestion d'erreur, design complet de l'import idempotent (section 4.3 réécrite), 3 nouveaux bugs ajoutés en section 7, numérotation des sections corrigée (saut 7→9 comblé).

### Étape 20 — `updateAll` (hors périmètre du projet) ✅ terminé
⚠️ Comme les étapes 13-19, pas une exigence du projet école. Point noté dès l'étape 18/19 (`filmDao.update(film)` appelé un par un dans `persister()`, même famille de problème que `save()` avant `saveAll`), traité le 2026-07-31.

- **`Dao.updateAll(Collection<T> entities)`** (+ implémentation dans `AbstractDao`) : même pattern exact que `saveAll` — une seule transaction pour toute la collection, `em.merge(entity)` en boucle, `flush()`/`clear()` tous les `BATCH_SIZE` éléments. `void` (pas de retour), cohérent avec la décision déjà prise sur `save`/`saveAll` : la copie managée renvoyée par `merge()` n'est utilisée nulle part.
- **`ImportService.persister()`** : la boucle sur les films ne fait plus qu'un seul appel `filmDao.update(film)` par film déjà existant — collecte désormais ces films dans une liste `filmsAMettreAJour`, puis un seul appel `filmDao.updateAll(filmsAMettreAJour)` après la boucle (à côté de `filmDao.saveAll(nouveauxFilms)`, inchangé).
- **Petite incohérence de Javadoc trouvée et corrigée** : `updateAll` référençait `update(Object)` en texte brut au lieu du `{@link #update(Object)}` utilisé par `saveAll` pour `{@link #save(Object)}` — corrigé pour rester cohérent.
- **Décision annexe (2026-07-31)** : `save`, `update`, `findById`, `delete` (les versions "une seule entité") sont **tous** aujourd'hui sans aucun appelant dans le projet (vérifié par `grep`, seuls `saveAll`/`updateAll`/`findAll` sont réellement utilisés, tous depuis `ImportService`). Décision de les garder malgré tout, contrairement au retrait du `@AllArgsConstructor` mort à l'étape 10 : ils font partie du contrat CRUD générique de `Dao<T, ID>` (documenté comme tel dans `CODE_EXPLANATION.md`), cohérent avec l'exigence "un DAO par entité" du cahier des charges — les retirer transformerait la couche DAO générique en un helper sur mesure pour les seuls besoins d'`ImportService`, perdant sa généricité pédagogique. Différent du cas `update()` avant l'étape 19, qui était gardée pour un usage concret déjà identifié : ici c'est un choix d'architecture assumé, pas une anticipation.

### Étape 21 — Config DB externalisée via `.env` (hors périmètre du projet) ✅ terminé
⚠️ Comme les étapes 13-20, pas une exigence du projet école. Choix explicite d'apprentissage (2026-07-31) : `.env` plutôt qu'une simple variable d'environnement système, jugé plus proche de ce qui se pratique réellement en entreprise.

- **Dépendance `io.github.cdimascio:dotenv-java` 3.2.0** ajoutée au `pom.xml`.
- **`.env`** (identifiants réels, `DB_USER`/`DB_PASSWORD`, jamais commit — ajouté au `.gitignore`) et **`.env.example`** (template commit, mêmes clés, valeurs vides) — pattern standard pour qu'un tiers qui clone le repo sache quoi renseigner sans jamais voir de vraies valeurs.
- **`persistence.xml`** : les deux `<property>` `jakarta.persistence.jdbc.user`/`password` retirées (ne restent que `driver`/`url`/`dialect`/`hbm2ddl.auto`, aucun secret).
- **`EntityManagerProvider.getEntityManagerFactory()`** : charge le `.env` (`Dotenv.configure().ignoreIfMissing().load()` — le `.ignoreIfMissing()` est nécessaire pour ne pas casser la CI GitHub Actions, qui n'a pas de `.env`) et construit une `Map<String, Object>` d'override, passée à la surcharge à deux arguments `Persistence.createEntityManagerFactory(String, Map)` — ces valeurs écrasent celles (absentes désormais) de `persistence.xml`.
- **🐛 Bug trouvé en revue avant test** : le premier essai avait perdu la construction paresseuse de l'étape 18 — le test `if (entityManagerFactory == null)` avait été déplacé *après* l'assignation au lieu d'avant, rendant `Persistence.createEntityManagerFactory(...)` appelée à *chaque* appel de `getEntityManagerFactory()` (donc à chaque appel DAO) au lieu d'une seule fois, avec les anciennes `EntityManagerFactory` jamais fermées. Fix : test remis avant la construction, bloc `.env`/overrides déplacé à l'intérieur du `if`.
- **✅ Testé de bout en bout (2026-07-31)** : `MenuApp` relancé, connexion réussie via les valeurs du `.env`, option 7 (`"marion"`) retrouve les mêmes 12 résultats qu'avant.

### Étape 22 — Revue complète du code et de la documentation (hors périmètre du projet) ✅ terminé
⚠️ Comme les étapes 13-21, pas une exigence du projet école. Deuxième revue de code du projet (la première datant de l'étape 10, 2026-07-30) — celle-ci couvre en plus les 31 fichiers Java de `src/main` + `FilmMapperTest.java`, les 4 `.md` et `sql/schema.sql`, demandée explicitement le 2026-07-31 après l'accumulation des étapes 15-21.

**9 points trouvés, tous corrigés :**
1. **`EntityManagerProvider`** — ni la Javadoc de classe ni celle de `getEntityManagerFactory()` ne mentionnaient le `.env` (étape 21) : les deux ne parlaient que de la construction paresseuse (étape 18). Fix : Javadoc complétée sur les deux.
2. **`RechercheService`** — Javadoc de classe toujours *"les 6 opérations de recherche..."*, jamais mise à jour depuis l'ajout de la recherche par nom (étape 15, 2 méthodes de plus). Fix : "8 opérations", liste complétée.
3. **`conception/document_conception.md`, diagramme de classes** — types Java obsolètes (`Long` pour les id `IDENTITY`, `Double` pour `rating`/`taille`) alors que le code utilise `Integer`/`BigDecimal` depuis les décisions documentées en section f). Incohérent même avec la section SQL du même document, qui elle était correcte. Fix : types resynchronisés (la plupart déjà corrigés avant ma relecture, seul `Personne.taille` restait en `Double`).
4. **`README.md`, section Stack technique** — ne listait pas `dotenv-java`, pourtant une vraie dépendance depuis l'étape 21. Fix : ligne ajoutée.
5. **`CODE_EXPLANATION.md`** — à jour jusqu'à l'étape 19 seulement, ne reflétait ni `updateAll` (étape 20) ni la config `.env` (étape 21). Fix : sections 2, 3 et 4.3 complétées.
6. **`Film.java:124`** — double espace (`private Set<Role> roles  = new HashSet<>();`). Corrigé par l'utilisateur.
7. **`FilmDao.java`** — ligne blanche superflue avant l'accolade fermante de la classe (même point déjà relevé et censé corrigé à l'étape 15, réapparu). Corrigé par l'utilisateur.
8. **`FilmJson.java`** — ligne vide avec espaces superflues avant l'accolade fermante. Corrigé par l'utilisateur.
9. **`FilmMapperTest.java`** — les commentaires référençaient "bug historique #7"/"#8" (numérotation de `PLAN.md`, étape 11), alors que `CODE_EXPLANATION.md` numérote ces mêmes bugs #5/#6 indépendamment — confusion possible pour qui recoupe via `CODE_EXPLANATION.md`. Décision (2026-07-31) : références retirées des commentaires **pour l'instant**, tant qu'aucun des deux documents n'est commit (donc pas encore consultable/recoupable publiquement) ; à revoir si on veut réintroduire une référence croisée plus tard.

**Aucune incohérence fonctionnelle trouvée** — uniquement de la doc en retard et des détails cosmétiques, rien qui affecte le comportement du code.

- **Commit** `3ef4778` — *"Revue de code complète : doc manquante, incohérences, correctifs mineurs"* (`README.md`, `conception/document_conception.md`, `FilmDao.java`, `Film.java`, `FilmJson.java`, `EntityManagerProvider.java`, `RechercheService.java`, `FilmMapperTest.java`).

### Étape 23 — Présentation du dépôt GitHub (hors périmètre du projet) ✅ terminé
⚠️ Comme les étapes 13-22, pas une exigence du projet école. Objectif : rendre le repo plus présentable pour un tiers (recruteur), inspiré d'un repo similaire montré par l'utilisateur (description "About" courte et technique, topics, doc publiée en ligne, licence).

- **✅ Description "About"** rédigée (courte, technique, dans le style de l'inspiration) : *"Application console Java 21 d'import et de recherche cinématographique : JPA/Hibernate, MariaDB, import idempotent, dédoublonnage, tests JUnit, CI GitHub Actions et déploiement via Docker Compose."*
- **✅ Topics** proposés : `java`, `jpa`, `hibernate`, `mariadb`, `jackson`, `docker-compose`, `junit5`, `maven` (l'utilisateur avait d'abord fusionné `jpa`+`hibernate` en un seul tag `jpa-hibernate` — corrigé : deux tags séparés, plus discoverable individuellement sur GitHub).
- **❌ GitHub Pages — abandonné (2026-07-31)** : plan initial (source `/root`, `CODE_EXPLANATION.md` auto-converti en `.html`, `index.md` de redirection) exploré en détail mais jugé finalement sans intérêt réel pour ce projet — décision de l'utilisateur. `CODE_EXPLANATION.md`/`PLAN.md` seront simplement commit et référencés dans le README (section "Documentation complémentaire"), même traitement que `conception/document_conception.md` — pas de site publié séparément.
- **✅ Licence MIT ajoutée** : fichier `LICENSE` (copyright "Marius Alauzet (malauzet)", 2026), section "Licence" ajoutée au `README.md` précisant que `films.json` (données scrapées IMDb) n'est pas couvert par cette licence — fourni à des fins pédagogiques uniquement. Commit `b613410`.
- **✅ `README.md`, section "Documentation complémentaire"** : `CODE_EXPLANATION.md` et `PLAN.md` ajoutés à côté de `conception/document_conception.md` (simples liens vers les fichiers du repo, pas de site publié).
- **✅ Badge de licence ajouté (2026-07-31)** : `[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)` (shields.io), à côté du badge CI en tête du `README.md`, cliquable vers `LICENSE`.
- **✅ Captures d'écran ajoutées (2026-07-31)** : `docs/screenshots/menu.png` (menu seul) et `docs/screenshots/menu_resultats.png` (parcours complet option 7, recherche "star wars", 9 résultats) — section "Aperçu" ajoutée au `README.md`, juste après l'intro, tableau côte à côte. Logs Hibernate rendus silencieux le temps des captures via `Logger.getLogger("org.hibernate").setLevel(Level.SEVERE)` ajouté temporairement dans `MenuApp.main()` puis retiré (jamais commit).
- **Pistes notées, pas encore faites** : tag/release `v1.0.0`, image de preview sociale, épingler le repo sur le profil GitHub.

## c) Liste de contrôle

- [x] Document de conception
- [x] Base de données MariaDB créée (DDL exécuté)
- [x] Entités JPA (7/7)
- [x] `EntityManagerProvider`
- [x] `Dao<T, ID>` + `AbstractDao<T, ID>`
- [x] 7 DAOs concrets
- [x] 6 DTOs JSON
- [x] Squelette `FilmMapper` + `DedupCaches`
- [x] `parseDate`
- [x] `parseRating`
- [x] `parseAnneeRange`
- [x] `parseTaille`
- [x] `toPays` / `toLangue` / `toGenre` / `toLieuNaissance`
- [x] `toPersonne` (+ dédoublonnage, garde sur `lieuNaissance` vide)
- [x] `toRole` + `isPrincipal` (dérivation de `principal`)
- [x] Ajouter `Map<String, Film> films` à `DedupCaches`
- [x] `toFilm` (assemblage complet + fusion `anneeDebut`/`anneeFin` sur doublons, cf. étape 7)
- [x] `ImportService` (lecture JSON + persistance en deux phases : mapping puis persist)
- [x] `ImportApp` (point d'entrée fonctionnel)
- [x] Premier import complet réussi et vérifié (2748 films, 317 s)
- [x] Méthodes de requête personnalisées sur les DAOs pour les recherches (6/6)
- [x] `RechercheService` (6 opérations)
- [x] Application menu console (`MenuApp`)
- [x] `README.md` + `CODE_EXPLANATION.md`
- [x] `FilmMapperTest` : tests des méthodes de parsing (`parseDate`/`parseRating`/`parseTaille`/`parseAnneeRange`/`cleDedoublonnage`)
- [x] `FilmMapperTest` : tests de la fusion `anneeDebut`/`anneeFin` sur les doublons (`toFilm`)
- [x] `FilmMapperTest` : tests des gardes clé-absente de `toFilm` (`pays`/`langue`/`lieuTournage`)
- [x] `FilmMapperTest` : test de la garde `lieuNaissance` vide de `toPersonne`
- [x] `FilmMapperTest` : tests de construction `toPays`/`toLangue`/`toGenre`/`toLieuNaissance`, `dedupe` en isolation, `isPrincipal`/`toRole`
- [x] CI GitHub Actions (`.github/workflows/ci.yml`) + badge de statut dans `README.md`
- [x] Recherche par nom (`findByIdentiteLike`/`findByNomLike`, option 7 de `MenuApp`), testée manuellement
- [x] Affichage uniforme des résultats (`Affichage.afficherResultats`, barres de séparation + compte), les 7 méthodes de `MenuActions` refactorées
- [x] `docker-compose.yml` (MariaDB + auto-init `schema.sql`), validé de bout en bout (import + menu)
- [x] Documenter l'option Docker dans `README.md` (+ relecture complète, plusieurs décalages corrigés)
- [x] `Dao.saveAll`/`AbstractDao.saveAll` (persistance en lot), `save()` en `void` — import réduit de ~360 s à 98 s
- [x] Gestion d'erreur au démarrage (`PersistenceException` dans `ImportApp`/`MenuApp`, `EntityManagerProvider` en lazy init pour permettre une reprise réelle), revalidée de bout en bout
- [x] Import idempotent (3 niveaux : `Personne`/`Film`, entités lookup, `Role`), testé bout en bout — comptes de lignes identiques sur 2 imports successifs
- [x] `Dao.updateAll`/`AbstractDao.updateAll` (mise à jour en lot des films déjà existants), `ImportService.persister()` mis à jour
- [x] Config DB externalisée via `.env` (`dotenv-java`), `persistence.xml` nettoyé des identifiants, testée bout en bout
- [x] Revue complète code + doc (31 fichiers Java, 4 `.md`, `sql/schema.sql`) : 9 points trouvés, tous corrigés
- [x] Licence MIT (`LICENSE` + section dans `README.md`)
- [x] Présentation du dépôt GitHub (About, topics, licence) — GitHub Pages exploré puis abandonné, `CODE_EXPLANATION.md`/`PLAN.md` référencés dans le README à la place

## d) Pourcentage de progression

| Bloc | Avancement |
|---|---|
| Conception | 100 % |
| Base de données | 100 % |
| Entités | 100 % |
| Fournisseur EntityManager | 100 % |
| Couche DAO | 100 % (socle + 6 méthodes de requête pour les recherches) |
| DTOs JSON | 100 % |
| Mapper | 100 % |
| ImportService | 100 % |
| RechercheService | 100 % |
| Application menu | 100 % |
| Application d'import | 100 % (import réel réussi) |
| **Global (exigences du projet, étapes 1 à 12)** | **100 %** |
| Tests unitaires — *hors périmètre, bonus* (étape 13) | 100 % (38 tests, tous passent) |
| CI GitHub Actions — *hors périmètre, bonus* (étape 14) | 100 % (workflow fonctionnel, badge dans le README) |
| Recherche par nom — *hors périmètre, bonus* (étape 15) | 100 % (testée manuellement contre la vraie base) |
| Affichage uniforme des résultats — *hors périmètre, bonus* (étape 16) | 100 % (`afficherResultats` + refactor des 7 méthodes de `MenuActions`) |
| Dockerisation MariaDB — *hors périmètre, bonus* (étape 17) | 100 % (fonctionnel de bout en bout, README à jour) |
| Vitesse d'import + gestion d'erreur au démarrage — *hors périmètre, bonus* (étape 18) | 100 % (92-98 s à l'import, reprise après coupure DB validée, commit `56852b8` + `bd9a359`) |
| Import idempotent — *hors périmètre, bonus* (étape 19) | 100 % (3 niveaux, testé bout en bout, commit `5d4525a`) |
| `updateAll` — *hors périmètre, bonus* (étape 20) | 100 % (même pattern que `saveAll`, `persister()` mis à jour) |
| Config DB externalisée via `.env` — *hors périmètre, bonus* (étape 21) | 100 % (testée bout en bout) |
| Revue complète code + doc — *hors périmètre, bonus* (étape 22) | 100 % (9 points corrigés, commit `3ef4778`) |
| Présentation du dépôt GitHub — *hors périmètre, bonus* (étape 23) | 100 % (About/topics/licence faits, GitHub Pages exploré puis abandonné) |

## e) Prochaines actions à mettre en œuvre

Le projet (étapes 1 à 12, exigences du cahier des charges) et les approfondissements personnels hors périmètre (étapes 13 à 23 : tests, CI, recherche par nom, affichage uniforme, Docker, vitesse d'import + gestion d'erreur, import idempotent, `updateAll`, config DB externalisée, revue complète, présentation du dépôt) sont ✅ terminés à 100 %. Reste :

1. **Commit de `CODE_EXPLANATION.md` et `PLAN.md`** — existent sur le disque depuis le début du projet, jamais passés par Git. Le README les référence désormais dans "Documentation complémentaire" : lien mort sur GitHub tant que ce n'est pas fait. Dernier point réellement ouvert.
2. **Étendre la couverture de tests aux DAOs/`RechercheService`** — plus de mise en place (nécessite une vraie base ou d'accepter que H2 ne reproduise pas la collation MariaDB qui justifie `cleDedoublonnage`) pour une valeur ajoutée moindre que ce qui a déjà été fait sur `FilmMapper`.

Backlog initial (section g) entièrement épuisé (recherche par nom, résumé de résultats, gestion d'erreurs, import idempotent, `updateAll`, config DB) — le projet est fonctionnellement stable, les prochaines étapes concernent uniquement la présentation du dépôt et la documentation.

Aucune date cible sur ces points : ce sont des idées à vision long terme, documentées ici pour ne pas les perdre, pas des engagements. Détail complet de chacune en section g).

## f) Décisions de conception à retenir

- `hibernate.hbm2ddl.auto=validate` (schéma écrit à la main, pas de génération auto).
- Clés naturelles (`Personne.id`, `Film.id` en `String`, id IMDb) vs clés techniques auto-incrémentées (`Integer`, `IDENTITY`) pour les entités lookup.
- Pattern id immuable : `final` + `@Setter(AccessLevel.NONE)` + `@RequiredArgsConstructor` + `@NoArgsConstructor(force = true)`.
- `BigDecimal` (pas `double`) pour les colonnes `DECIMAL` (`rating`, `taille`).
- `Film.plot` en `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` plutôt que `@Lob` — confirmé que `@Lob` générait `LONGTEXT` (CLOB) alors que la colonne DDL est `TEXT`, cassant la validation de schéma.
- Dédoublonnage des entités lookup basé sur une clé normalisée (trim + minuscules + sans accents via `Normalizer`), pas la valeur brute — pour matcher la collation `utf8mb4_general_ci` de MariaDB (insensible à la casse et aux accents), sinon des doublons "égaux" pour la base mais "différents" pour Java passent à travers le cache et cassent à l'insertion.
- Stratégies de fetch choisies au cas par cas (ex. `Personne.lieuNaissance` en `LAZY` explicite, jamais requêté dans ce projet).
- Pas de redéfinition d'`equals`/`hashCode` sur les entités (pièges classiques avec `IDENTITY` + lazy-loading) → dédoublonnage géré entièrement via `Map<String, Entity>` côté import.
- Couche service organisée par responsabilité, pas une classe par entité : `ImportService` (import uniquement) + `FilmMapper` (transformation pure) + `RechercheService` (recherches).
- `FilmMapper` et `DedupCaches` séparés : le mapper transforme, `ImportService` possède les caches et décide de la persistance.
- Import JSON : lecture simple via Jackson `ObjectMapper` en un seul `List<FilmJson>` (pas de streaming) — fichier de 21.7 Mo, taille jugée trop petite pour justifier la complexité du streaming.
- `parseDate` : ne jamais fabriquer une date partielle → `null` si le jour/mois/année complet n'est pas disponible.
- `parseRating` : gérer les deux formats (point ET virgule) déjà mélangés dans le fichier source.
- `jackson-datatype-jsr310` jugé non pertinent : les dates du JSON ne sont pas dans un format stable/ISO, donc pas de binding Jackson direct vers `LocalDate` — le parsing reste manuel dans le mapper.
- 38 ids de films dupliqués dans `films.json` (probablement des shows/séries au long cours scrapés une fois par acteur) : seuls `url` et `anneeSortie` diffèrent réellement entre occurrences d'un même id, le reste est identique. Décision (2026-07-29) : fusionner `anneeDebut`/`anneeFin` (min/max) sur les doublons plutôt que garder juste la première occurrence — accepté malgré la complexité ajoutée (`toFilm` ne peut plus utiliser `computeIfAbsent`, `ImportService` doit éviter les doubles insertions), le délai du projet le permettant.
- `ImportService` en deux phases (mapping complet en mémoire d'abord, persistance ensuite) plutôt que persist-au-fil-de-l'eau : élimine le besoin de distinguer "entité neuve" de "déjà persistée", puisque chaque cache ne contient qu'une instance par clé à la fin du mapping, et que toute la phase de persistance ne fait que des `save`/`persist`.
- Package `fr.diginamic.cinema.app` (plutôt que la racine) pour les points d'entrée (`ImportApp`, `MenuApp`) : cohérent avec le découpage par responsabilité du reste du projet, et plus clair qu'un seul `Main` vu qu'il y a deux points d'entrée distincts.
- `MenuApp` : menu numéroté 1–6 pour les recherches, 0 pour quitter (pas 7) — le numéro de sortie reste stable si de nouvelles recherches sont ajoutées plus tard.
- `MenuApp` : lecture exclusivement via `scanner.nextLine()` (jamais `scanner.nextInt()`), avec parsing manuel (`Integer.parseInt`) et reprompt en boucle sur entrée invalide — évite le bug classique du `\n` résiduel laissé par `nextInt()` qui casse l'appel `nextLine()` suivant.
- `MenuApp` : helpers d'affichage dédiés (`afficherFilm`/`afficherRole`/`afficherPersonne`) plutôt que `toString()` sur les entités, pour ne jamais toucher un champ `LAZY` (risque de `LazyInitializationException` une fois l'`EntityManager` de la requête refermé).
- Scripts SQL (`schema.sql`, `reset_db.sql`) dans un dossier `sql/` à la racine plutôt que `src/main/resources` : ce ne sont pas des ressources consommées par l'appli au runtime (exécutés en externe via `mysql.exe`), donc pas de raison de les embarquer dans le build Maven.
- Import non idempotent assumé (pas de vérification d'existence avant `persist`) : plus simple à écrire et suffisant pour la durée du projet, au prix de devoir vider la base (`sql/reset_db.sql` ou `DROP DATABASE` + `sql/schema.sql`) avant tout ré-import après un correctif du mapper.
- `FilmMapper.dedupe(Map<String, T>, String, Function<String, T>)` : helper générique factorisant le pattern commun à `toPays`/`toLangue`/`toGenre`/`toLieuNaissance` (trim → `cleDedoublonnage` → `computeIfAbsent`), trouvé lors de la revue de code du 2026-07-30.
- `parseAnneeRange` ne renvoie jamais `null` (renvoie `new AnneeRange(null, null)` si la valeur brute est absente) : évite un `NullPointerException` latent dans `toFilm`, cohérent avec le fait que les champs d'`AnneeRange` tolèrent déjà `null` individuellement.
- **Rétrogradation Java 25 → 21 (2026-07-30)**, pour l'accessibilité du dépôt GitHub (des recruteurs/correcteurs ont plus de chances d'avoir Java 21 (LTS, largement répandu) sous la main que Java 25 (récent)) : `pom.xml` (`maven.compiler.source`/`target`) passé à 21, et `ImportApp`/`MenuApp` revenus à la signature `public static void main(String[] args)` classique — leur signature `static void main()` sans arguments s'appuyait sur JEP 512, finalisée seulement en Java 25 (simple *preview* sur 21, aurait demandé `--enable-preview` à la compilation *et* à l'exécution). Aucune autre fonctionnalité du projet ne dépendait de Java 25 : les `record` et le switch en flèches déjà utilisés (`AnneeRange`, `MenuApp`) sont finalisés depuis Java 14/16, et les versions de Hibernate/Jackson/MariaDB utilisées n'ont pas d'exigence Java 25 particulière.
- **`films.json` désormais suivi par Git (2026-07-31, commit `d9863f7`)** — ligne retirée du `.gitignore`. Exclu à l'origine uniquement pour la taille (21.7 Mo), à une époque où le projet était pensé comme purement scolaire (professeur + utilisateur, fichier déjà en leur possession). Revu maintenant que le dépôt GitHub a une vocation plus publique (ex. un recruteur qui veut tester le projet sans se procurer le dataset ailleurs) : 21.7 Mo reste largement sous les seuils de Git/GitHub (pas besoin de Git LFS, limite dure à 100 Mo). `target/classes/films.json` (copie de build Maven) reste ignoré via la règle `target/`.
- **Docker Compose pour MariaDB plutôt que H2 (décidé 2026-07-31)**, même motivation d'accessibilité pour un tiers qui clone le repo (éviter l'installation manuelle de XAMPP/MariaDB). H2 explicitement écarté : `utf8mb4_general_ci` (MariaDB) est une table de correspondance figée propre au moteur, pas un algorithme Unicode générique — H2 compare les chaînes via `java.text.Collator`/ICU4J, un moteur différent qui ne garantit pas les mêmes décisions d'égalité sur tous les cas limites (déjà le problème identifié à l'étape 13 pour les tests DAO). Un `docker-compose.yml` qui lance une vraie image MariaDB (version pinée, `sql/schema.sql` monté dans `/docker-entrypoint-initdb.d/` pour l'auto-init au premier démarrage, port `3306` mappé pour rester compatible avec `persistence.xml` sans le modifier) garde ce comportement réel tout en simplifiant l'installation à un `docker compose up -d`. Reste purement additif : l'installation manuelle XAMPP déjà documentée dans le README continue de fonctionner en parallèle. **Écrit et validé à l'étape 17.**

## g) Pistes d'amélioration identifiées (backlog, hors périmètre du projet)

Idées discutées le 2026-07-30, une fois le projet (étapes 1 à 12) et les deux approfondissements personnels (étapes 13-14, tests + CI) terminés. Aucune n'est une exigence du projet école.

**Fait :**
- ~~Recherche par nom plutôt que id IMDb brut~~ → traité à l'étape 15 (2026-07-31).
- ~~Résumé du nombre de résultats~~ → traité à l'étape 16 (2026-07-31), sous une forme légèrement différente de l'idée initiale (barres de séparation + compte après la liste plutôt qu'un simple en-tête avant).
- ~~Gestion d'erreurs au démarrage~~ → traité à l'étape 18 (2026-07-31, Phase 2) : `PersistenceException` capturée dans `ImportApp`/`MenuApp`, et bug plus profond trouvé/corrigé au passage (`EntityManagerProvider` en construction paresseuse, sinon aucune reprise possible après une première coupure de la base).
- ~~Import idempotent~~ → traité à l'étape 19 (2026-07-31), 3 niveaux (`Personne`/`Film` via préchargement + `findById`/`update` ; `Pays`/`Langue`/`Genre`/`LieuNaissance` via préchargement + `cleDedoublonnage` rendue `public` — pas besoin de `findByNom` finalement, `findAll()` en bulk a suffi, plus simple que prévu initialement ; `Role` persisté seulement pour les films nouveaux, option (c)). Piste (b) pour `Role` (contrainte unique + vérification par rôle) explicitement non retenue, notée pour étude future si l'hypothèse "casting figé entre deux JSON" ne tenait plus.
- ~~`updateAll` pour `Dao`/`AbstractDao`~~ → traité à l'étape 20 (2026-07-31), même pattern que `saveAll`.
- ~~Config DB externalisée~~ → traité à l'étape 21 (2026-07-31), via `.env`/`dotenv-java` plutôt qu'une simple variable d'environnement système (choix explicite d'apprentissage, plus proche des pratiques d'entreprise).

**Tests (si on veut pousser encore plus loin) :**
- **Étendre la couverture aux DAOs/`RechercheService`** — resterait limité par le même problème identifié à l'étape 13 : H2 en mémoire ne reproduirait pas la collation MariaDB `utf8mb4_general_ci` qui justifie `cleDedoublonnage`, coût de mise en place jugé disproportionné pour la valeur ajoutée.