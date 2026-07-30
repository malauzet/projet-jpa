-- Création de la base de données.
-- On vérifie qu'elle n'existe pas.
CREATE DATABASE IF NOT EXISTS cinema
    COLLATE utf8mb4_general_ci;

-- On se déplace sur la base pour la suite du script.
USE cinema;

-- Force la connexion client/serveur à encoder les échanges en UTF-8 (4 octets)
-- pour la session en cours
SET NAMES utf8mb4;

-- Désactive temporairement la vérification des clés étrangères.
-- Utile si les tables ne sont pas créés dans le bon ordre.
SET FOREIGN_KEY_CHECKS = 0;

-- Création de la table pays, les pays doivent être uniques.
CREATE TABLE pays (
    id  INT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(100) NOT NULL,
    url VARCHAR(255),

    CONSTRAINT uk_pays_nom UNIQUE (nom)
);

-- Création de la table langue, les langues doivent être uniques.
CREATE TABLE langue (
    id  INT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(50) NOT NULL,

    CONSTRAINT uk_langue_nom UNIQUE (nom)
);

-- Création de la table genre, les genres doivent être uniques.
CREATE TABLE genre (
    id  INT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(50) NOT NULL,

    CONSTRAINT uk_genre_nom UNIQUE (nom)
);

-- Création de la table lieu_naissance, les lieux de naissance doivent être uniques.
CREATE TABLE lieu_naissance (
    id      INT AUTO_INCREMENT PRIMARY KEY,
    libelle VARCHAR(255) NOT NULL,

    CONSTRAINT uk_lieu_naissance_libelle UNIQUE (libelle)
);

-- Création de la table personne.
CREATE TABLE personne (
    id                  VARCHAR(15) PRIMARY KEY,   -- identifiant IMDb, ex. nm0000001
    identite            VARCHAR(150) NOT NULL,
    url                 VARCHAR(255),
    date_naissance      DATE,
    taille              DECIMAL(3,2),
    lieu_naissance_id   INT,

    CONSTRAINT fk_personne_lieu_naissance
        FOREIGN KEY (lieu_naissance_id)
            REFERENCES lieu_naissance(id)
);

-- Création de la table film.
CREATE TABLE film (
    id                  VARCHAR(15) PRIMARY KEY,  -- identifiant IMDb, ex. tt0082449
    nom                 VARCHAR(255) NOT NULL,
    url                 VARCHAR(255),
    rating              DECIMAL(3,1),
    plot                TEXT,
    annee_debut         INT,
    annee_fin           INT,
    ville_tournage      VARCHAR(255),
    etat_dept_tournage  VARCHAR(255),
    pays_tournage       VARCHAR(255),
    pays_id             INT,
    langue_id           INT,

    CONSTRAINT fk_film_pays
        FOREIGN KEY (pays_id)
            REFERENCES pays(id),

    CONSTRAINT fk_film_langue
        FOREIGN KEY (langue_id)
            REFERENCES langue(id),

    -- On fait attention que l'année de fin est supérieure ou égale à l'année de début.
    CONSTRAINT chk_film_annees
        CHECK (annee_fin IS NULL OR annee_fin >= annee_debut),

    -- On garde la note du film entre 0 et 10 inclus.
    CONSTRAINT chk_film_rating
        CHECK (rating IS NULL OR (rating >= 0 AND rating <= 10))
);

-- Création de l'index pour éviter de scanner toute la table
-- quand l'année de début est une condition de recherche.
CREATE INDEX idx_film_annee_debut ON film(annee_debut);

-- Création de la table role.
CREATE TABLE role (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    character_name  VARCHAR(150),
    principal       BOOLEAN NOT NULL DEFAULT FALSE,
    film_id         VARCHAR(15) NOT NULL,
    personne_id     VARCHAR(15) NOT NULL,

    CONSTRAINT fk_role_film
        FOREIGN KEY (film_id)
            REFERENCES film(id),

    CONSTRAINT fk_role_personne
        FOREIGN KEY (personne_id)
            REFERENCES personne(id)
);

-- Création de la table de jointure entre film et genre.
CREATE TABLE film_genre (
    film_id     VARCHAR(15) NOT NULL,
    genre_id    INT NOT NULL,
    PRIMARY KEY (film_id, genre_id),

    CONSTRAINT fk_film_genre_film
        FOREIGN KEY (film_id)
            REFERENCES film(id),

    CONSTRAINT fk_film_genre_genre
        FOREIGN KEY (genre_id)
            REFERENCES genre(id)
);

-- Création de la table de jointure entre film et personne.
CREATE TABLE film_realisateur (
    film_id     VARCHAR(15) NOT NULL,
    personne_id VARCHAR(15) NOT NULL,
    PRIMARY KEY (film_id, personne_id),

    CONSTRAINT fk_film_real_film
        FOREIGN KEY (film_id)
            REFERENCES film(id),

    CONSTRAINT fk_film_real_personne
        FOREIGN KEY (personne_id)
            REFERENCES personne(id)
);

-- Maintenant que la base et les tables sont créées,
-- on réactive la vérification des clés étrangères.
SET FOREIGN_KEY_CHECKS = 1;