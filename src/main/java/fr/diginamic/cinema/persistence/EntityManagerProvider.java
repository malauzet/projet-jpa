package fr.diginamic.cinema.persistence;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.util.HashMap;
import java.util.Map;

/**
 * Point d'accès unique à l'EntityManagerFactory de l'application.
 * Construite paresseusement au premier appel plutôt qu'au chargement de la classe :
 * si la connexion échoue (ex. base non démarrée),
 * l'échec reste une exception normale et l'appel suivant retentera la connexion,
 * au lieu de casser définitivement la classe pour le reste de l'exécution.
 * Les identifiants de connexion (utilisateur/mot de passe) sont lus depuis un fichier .env
 * à la racine du projet (non versionné) et injectés par-dessus persistence.xml.
 */
public final class EntityManagerProvider {

    private static final String PERSISTENCE_UNIT_NAME = "cinema";

    private static EntityManagerFactory entityManagerFactory;

    private EntityManagerProvider() {
        // Rend impossible d'écrire new EntityManagerProvider() depuis l'extérieur de la classe.
    }

    /**
     * Donne accès à l'EntityManagerFactory partagée de l'application,
     * en la construisant au premier appel (et en la réutilisant ensuite).
     * Charge le fichier .env (DB_USER/DB_PASSWORD) et passe ces valeurs en override
     * à Persistence.createEntityManagerFactory, où elles priment sur persistence.xml.
     * Si la construction échoue, rien n'est mis en cache : le prochain appel retentera.
     *
     * @return l'EntityManagerFactory partagée de l'application
     */
    public static EntityManagerFactory getEntityManagerFactory() {

        if (entityManagerFactory == null) {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

            Map<String, Object> overrides = new HashMap<>();
            overrides.put("jakarta.persistence.jdbc.user", dotenv.get("DB_USER"));
            overrides.put("jakarta.persistence.jdbc.password", dotenv.get("DB_PASSWORD"));

            entityManagerFactory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME, overrides);
        }
        return entityManagerFactory;
    }

    /**
     * Ouvre un nouvel EntityManager à partir de l'EntityManagerFactory partagée.
     *
     * @return un nouvel EntityManager, à fermer par l'appelant après utilisation
     */
    public static EntityManager getEntityManager() {
        return getEntityManagerFactory().createEntityManager();
    }

    /**
     * Ferme l'EntityManagerFactory. À appeler une seule fois, en fin de programme.
     */
    public static void close() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }
}
