# Films IMDb — Import JPA/Hibernate + Console de recherche

Projet école (Diginamic) : import d'un dataset IMDb (`films.json`, ~21.7 Mo, 2748 films) dans une base
MariaDB via JPA/Hibernate, puis exploration des données via une application console (6 opérations de
recherche).

## Stack technique

- **Java 21** (Maven, `maven.compiler.source`/`target` = 21)
- **Hibernate ORM 7.4.4** (implémentation JPA)
- **MariaDB** (pilote `mariadb-java-client` 3.5.9)
- **Jackson** (`jackson-databind`) pour le parsing du JSON source
- **Lombok** pour réduire le boilerplate (getters/setters/constructeurs)

## Structure du projet

```
conception/                          document de conception initial (diagrammes, DDL)
sql/
  schema.sql                         DDL complet (base + 9 tables)
  reset_db.sql                       vide les données sans toucher au schéma
src/main/resources/
  films.json                         dataset source (non versionné, voir .gitignore)
  META-INF/persistence.xml           configuration JPA
src/main/java/fr/diginamic/cinema/
  entity/                            7 entités JPA
  persistence/                       EntityManagerProvider
  dao/                                couche DAO générique + 7 DAOs concrets
  json/                               DTOs miroir de films.json
  mapper/                             FilmMapper + DedupCaches (DTO -> entités)
  service/                            ImportService, RechercheService
  console/                            Saisie, Affichage, MenuActions
  app/                                 ImportApp, MenuApp (points d'entrée)
```

## Prérequis

- JDK 21
- Maven
- MariaDB (ex. via XAMPP), démarré sur `localhost:3306`
- Le fichier `films.json` placé dans `src/main/resources/` (non fourni dans le dépôt)

## Mise en place de la base de données

Le schéma est écrit à la main (`hibernate.hbm2ddl.auto=validate` dans `persistence.xml`, Hibernate ne
génère rien) : il faut donc l'exécuter manuellement avant tout import.

Créer la base et les tables :

```powershell
Get-Content sql\schema.sql | & "C:\xampp\mysql\bin\mysql.exe" -u root
```

Pour vider les données entre deux imports de test, sans recréer le schéma :

```powershell
Get-Content sql\reset_db.sql | & "C:\xampp\mysql\bin\mysql.exe" -u root cinema
```

Pour un reset complet (schéma + données) :

```powershell
& "C:\xampp\mysql\bin\mysql.exe" -u root -e "DROP DATABASE IF EXISTS cinema;"
Get-Content sql\schema.sql | & "C:\xampp\mysql\bin\mysql.exe" -u root
```

Connexion configurée dans `persistence.xml` : utilisateur `root`, pas de mot de passe, base `cinema` sur
`localhost:3306`.

## Lancer l'import

Exécuter `fr.diginamic.cinema.app.ImportApp` (point d'entrée). Lit `films.json`, dédoublonne les
entités répétées, puis persiste tout en base. Environ 317 s pour les 2748 films.

⚠️ **L'import n'est pas idempotent.** Le relancer sur une base déjà peuplée échoue sur les contraintes
uniques (aucune vérification d'existence avant `persist`). Vider la base d'abord (voir ci-dessus) avant
tout nouvel import.

## Lancer l'application de recherche

Exécuter `fr.diginamic.cinema.app.MenuApp` (point d'entrée). Menu console, options **1 à 6** pour les
recherches, **0** pour quitter :

1. Filmographie d'un acteur
2. Casting d'un film
3. Films sortis entre deux années
4. Films communs à deux acteurs
5. Acteurs communs à deux films
6. Films d'un acteur entre deux années

Les identifiants attendus sont les ids IMDb bruts (ex. `nm0000001` pour une personne, `tt0082449` pour
un film).

## Documentation complémentaire

- **`conception/document_conception.md`** — document de conception initial (diagrammes de classes/ER,
  script DDL, constats sur les données sources).
