package fr.diginamic.cinema.dao;

import fr.diginamic.cinema.entity.Film;
import fr.diginamic.cinema.persistence.EntityManagerProvider;
import jakarta.persistence.EntityManager;

import java.util.List;

/**
 * DAO pour l'entité Film.
 */
public class FilmDao extends AbstractDao<Film, String> {

    /**
     * Construit le DAO pour l'entité Film.
     */
    public FilmDao() {
        super(Film.class);
    }

    /**
     * Récupère la filmographie d'un acteur (tous les films où il a un rôle).
     * DISTINCT est nécessaire, car un même acteur peut avoir plusieurs rôles dans un même film.
     *
     * @param personneId id IMDb de la personne
     * @return la liste des films où cette personne a au moins un rôle
     */
    public List<Film> findByActeurId(String personneId) {

        // Pas de transaction : une simple lecture ne modifie rien en base.
        try (EntityManager em = EntityManagerProvider.getEntityManager()) {
            return em.createQuery("SELECT DISTINCT f FROM Film f " +
                            "JOIN f.roles r " +
                            "WHERE r.personne.id = :personneId", Film.class)
                    .setParameter("personneId", personneId)
                    .getResultList();
        }
    }

    /**
     * Récupère les films dont l'année de sortie (anneeDebut) est comprise entre deux années données, bornes incluses.
     * N'utilise qu'anneeDebut, pas anneeFin (séries/shows non traités différemment des films simples).
     *
     * @param debut année de début de l'intervalle (incluse)
     * @param fin   année de fin de l'intervalle (incluse)
     * @return la liste des films dont l'année de sortie est dans cet intervalle
     */
    public List<Film> findByAnneeRange(int debut, int fin) {

        // Pas de transaction : une simple lecture ne modifie rien en base.
        try (EntityManager em = EntityManagerProvider.getEntityManager()) {
            return em.createQuery("SELECT f FROM Film f " +
                            "WHERE f.anneeDebut " +
                            "BETWEEN :debut AND :fin", Film.class)
                    .setParameter("debut", debut)
                    .setParameter("fin", fin)
                    .getResultList();
        }
    }

    /**
     * Récupère les films communs à deux acteurs (films où chacun des deux a au moins un rôle).
     * DISTINCT est nécessaire, car un acteur peut avoir plusieurs rôles dans un même film.
     *
     * @param personneId1 id IMDb du premier acteur
     * @param personneId2 id IMDb du second acteur
     * @return la liste des films où ces deux personnes ont chacune au moins un rôle
     */
    public List<Film> findCommunsEntreActeurs(String personneId1, String personneId2) {

        // Pas de transaction : une simple lecture ne modifie rien en base.
        try (EntityManager em = EntityManagerProvider.getEntityManager()) {
            return em.createQuery("SELECT DISTINCT f FROM Film f " +
                            "JOIN f.roles r1 " +
                            "JOIN f.roles r2 " +
                            "WHERE r1.personne.id = :personneId1 " +
                            "AND r2.personne.id = :personneId2", Film.class)
                    .setParameter("personneId1", personneId1)
                    .setParameter("personneId2", personneId2)
                    .getResultList();
        }
    }

    /**
     * Récupère les films d'un acteur dont l'année de sortie est comprise entre deux années données, bornes incluses.
     * DISTINCT est nécessaire, car l'acteur peut avoir plusieurs rôles dans un même film.
     *
     * @param personneId id IMDb de la personne
     * @param debut      année de début de l'intervalle (incluse)
     * @param fin        année de fin de l'intervalle (incluse)
     * @return la liste des films de cette personne dont l'année de sortie est dans cet intervalle
     */
    public List<Film> findByActeurIdAndAnneeRange(String personneId, int debut, int fin) {

        // Pas de transaction : une simple lecture ne modifie rien en base.
        try (EntityManager em = EntityManagerProvider.getEntityManager()) {
            return em.createQuery("SELECT DISTINCT f FROM Film f " +
                            "JOIN f.roles r " +
                            "WHERE r.personne.id = :personneId " +
                            "AND f.anneeDebut BETWEEN :debut AND :fin", Film.class)
                    .setParameter("personneId", personneId)
                    .setParameter("debut", debut)
                    .setParameter("fin", fin)
                    .getResultList();
        }
    }
}
