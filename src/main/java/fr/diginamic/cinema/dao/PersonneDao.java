package fr.diginamic.cinema.dao;

import fr.diginamic.cinema.entity.Personne;
import fr.diginamic.cinema.persistence.EntityManagerProvider;
import jakarta.persistence.EntityManager;

import java.util.List;

/**
 * DAO pour l'entité Personne.
 */
public class PersonneDao extends AbstractDao<Personne, String> {

    /**
     * Construit le DAO pour l'entité Personne.
     */
    public PersonneDao() {
        super(Personne.class);
    }

    /**
     * Récupère les acteurs communs à deux films (personnes ayant un rôle dans chacun des deux).
     * DISTINCT est nécessaire, car une personne peut avoir plusieurs rôles dans un même film.
     *
     * @param filmId1 id IMDb du premier film
     * @param filmId2 id IMDb du second film
     * @return la liste des personnes ayant au moins un rôle dans chacun des deux films
     */
    public List<Personne> findCommunsEntreFilms(String filmId1, String filmId2) {

        // Pas de transaction : une simple lecture ne modifie rien en base.
        try (EntityManager em = EntityManagerProvider.getEntityManager()) {
            return em.createQuery("SELECT DISTINCT r.personne FROM Role r " +
                            "WHERE r.film.id = :filmId1 " +
                            "AND r.personne " +
                            "IN (SELECT r2.personne FROM Role r2 WHERE r2.film.id = :filmId2)", Personne.class)
                    .setParameter("filmId1", filmId1)
                    .setParameter("filmId2", filmId2)
                    .getResultList();
        }
    }

    /**
     * Recherche les personnes dont l'identité contient la chaîne donnée
     * (recherche partielle, insensible à la casse et
     * aux accents grâce à la collation utf8mb4_general_ci de la base).
     *
     * @param nom fragment de nom recherché
     * @return la liste des personnes dont l'identité contient ce fragment
     */
    public List<Personne> findByIdentiteLike(String nom) {

        String motif = "%" + nom.trim() + "%";

        // Pas de transaction : une simple lecture ne modifie rien en base.
        try (EntityManager em = EntityManagerProvider.getEntityManager()) {
            return em.createQuery("SELECT p FROM Personne p " +
                            "WHERE p.identite LIKE :motif " +
                            "ORDER BY p.identite", Personne.class)
                    .setParameter("motif", motif)
                    .getResultList();
        }
    }
}
