package fr.diginamic.cinema.dao;

import fr.diginamic.cinema.entity.Role;
import fr.diginamic.cinema.persistence.EntityManagerProvider;
import jakarta.persistence.EntityManager;

import java.util.List;

/**
 * DAO pour l'entité Role.
 */
public class RoleDao extends AbstractDao<Role, Integer> {

    public RoleDao() {
        super(Role.class);
    }

    /**
     * Récupère le casting complet d'un film (tous ses rôles),
     * avec la Personne associée déjà chargée pour éviter une requête séparée par rôle.
     *
     * @param filmId id IMDb du film
     * @return la liste des rôles du film, chacun avec sa Personne déjà chargée
     */
    public List<Role> findByFilmId(String filmId) {

        // Pas de transaction : une simple lecture ne modifie rien en base.
        try (EntityManager em = EntityManagerProvider.getEntityManager()) {
            return em.createQuery("SELECT r FROM Role r " +
                            "JOIN FETCH r.personne " +
                            "WHERE r.film.id = :filmId", Role.class)
                    .setParameter("filmId", filmId)
                    .getResultList();
        }
    }
}
