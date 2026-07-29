package fr.diginamic.cinema.dao;

import fr.diginamic.cinema.entity.Personne;
import fr.diginamic.cinema.persistence.EntityManagerProvider;
import jakarta.persistence.EntityManager;

import java.util.List;

/**
 * DAO pour l'entité Personne.
 */
public class PersonneDao extends AbstractDao<Personne, String> {

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
}
