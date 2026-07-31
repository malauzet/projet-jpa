package fr.diginamic.cinema.dao;

import fr.diginamic.cinema.persistence.EntityManagerProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Implémentation générique de {@link Dao}, factorisant la gestion de l'EntityManager
 * et des transactions communes à toutes les entités.
 *
 * @param <T>  le type de l'entité
 * @param <ID> le type de l'identifiant de l'entité
 */
public abstract class AbstractDao<T, ID> implements Dao<T, ID> {

    /**
     * Classe réelle de l'entité gérée par ce DAO (ex : Pays.class, Film.class...).
     * Chaque sous-classe concrète (ex : PaysDao)
     * transmet sa propre classe d'entité au constructeur de AbstractDao, qui la
     * conserve ici pour pouvoir l'utiliser partout où T.class serait nécessaire,
     * mais n'existe pas.
     */
    protected final Class<T> entityClass;

    /**
     * Nombre d'entités persistées entre deux flush/clear du contexte de persistance dans {@link #saveAll(Collection)},
     * pour éviter qu'il grossisse indéfiniment sur de grosses collections.
     */
    private static final int BATCH_SIZE = 100;

    /**
     * Constructeur appelé par les sous-classes.
     * @param entityClass la classe de l'entité gérée par ce DAO
     */
    protected AbstractDao(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    @Override
    public void save(T entity) {
        executeInTransaction(em -> {
            em.persist(entity);
        });
    }

    @Override
    public void saveAll(Collection<T> entities) {

        executeInTransaction(em -> {

            int compteur = 0;

            for (T entity : entities) {
                em.persist(entity);
                compteur++;

                if (compteur % BATCH_SIZE == 0) {
                    em.flush();
                    em.clear();
                }
            }
        });
    }

    @Override
    public Optional<T> findById(ID id) {
        // Pas de transaction : une simple lecture ne modifie rien en base.
        try (EntityManager em = EntityManagerProvider.getEntityManager()) {
            return Optional.ofNullable(em.find(entityClass, id));
        }
    }

    @Override
    public List<T> findAll() {
        // Pas de transaction : une simple lecture ne modifie rien en base.
        try (EntityManager em = EntityManagerProvider.getEntityManager()) {
            CriteriaBuilder cb = em.getCriteriaBuilder(); // On prend un query builder
            CriteriaQuery<T> query = cb.createQuery(entityClass); // Créer l'objet de query et lui donne un type de retour
            query.select(query.from(entityClass)); // SELECT 'les colonnes' FROM 'la table de la classe choisie'
            return em.createQuery(query).getResultList(); // Execute et renvoie le résultat
        }
    }

    @Override
    public T update(T entity) {
        return executeInTransaction(em -> {
            return em.merge(entity);
        });
    }

    @Override
    public void updateAll(Collection<T> entities) {

        executeInTransaction(em -> {

            int compteur = 0;

            for (T entity : entities) {
                em.merge(entity);
                compteur++;

                if (compteur % BATCH_SIZE == 0) {
                    em.flush();
                    em.clear();
                }
            }
        });
    }

    @Override
    public void delete(T entity) {
        executeInTransaction(em -> {
            // Si l'entité n'est pas déjà gérée par cet EntityManager
            // (ex : elle a été chargée via un autre EntityManager, aujourd'hui fermé),
            // merge() la rattache et renvoie une copie gérée qu'on peut ensuite supprimer.
            T managed = em.contains(entity) ? entity : em.merge(entity);
            em.remove(managed);
        });
    }

    /**
     * Exécute une action dans une transaction et retourne son résultat.
     * Ouvre un EntityManager dédié, gère le commit et le rollback en cas d'erreur.
     *
     * @param action l'action à exécuter, recevant l'EntityManager de la transaction
     * @param <R>    le type du résultat retourné par l'action
     * @return le résultat de l'action
     */
    private <R> R executeInTransaction(Function<EntityManager, R> action) {
        try (EntityManager em = EntityManagerProvider.getEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            try {
                transaction.begin();
                R result = action.apply(em);
                transaction.commit();
                return result;
            } catch (RuntimeException e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw e;
            }
        }
    }

    /**
     * Variante de {@link #executeInTransaction(Function)} pour les actions ne retournant rien.
     * (Ex : delete)
     *
     * @param action l'action à exécuter, recevant l'EntityManager de la transaction
     */
    private void executeInTransaction(Consumer<EntityManager> action) {
        executeInTransaction((Function<EntityManager, Void>) em -> {
            action.accept(em);
            return null;
        });
    }
}
