package fr.diginamic.cinema.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Contrat générique d'accès aux données pour une entité de type T,
 * dont l'identifiant est de type ID.
 */
public interface Dao<T, ID> {

    /**
     * Persiste une nouvelle entité en base.
     *
     * @param entity l'entité à sauvegarder
     */
    void save(T entity);

    /**
     * Persiste plusieurs entités dans une seule transaction (un seul commit),
     * plutôt qu'une transaction par entité comme {@link #save(Object)} appelée en boucle,
     * nettement plus rapide sur de grosses collections.
     * Le contexte de persistance est vidé (flush + clear) à intervalles réguliers
     * pour ne pas grossir indéfiniment.
     *
     * @param entities les entités à persister
     */
    void saveAll(Collection<T> entities);

    /**
     * Recherche une entité par son identifiant.
     *
     * @param id l'identifiant de l'entité recherchée
     * @return l'entité si elle existe, sinon un Optional vide
     */
    Optional<T> findById(ID id);

    /**
     * @return toutes les entités présentes en base
     */
    List<T> findAll();

    /**
     * Met à jour une entité existante en base.
     *
     * @param entity l'entité à mettre à jour
     * @return l'entité mise à jour, rattachée au contexte de persistance
     */
    T update(T entity);

    /**
     * Met à jour plusieurs entités existantes dans une seule transaction (un seul commit),
     * plutôt qu'une transaction par entité comme {@link #update(Object)} appelée en boucle.
     *
     * @param entities les entités à mettre à jour
     */
    void updateAll(Collection<T> entities);

    /**
     * Supprime une entité de la base.
     *
     * @param entity l'entité à supprimer
     */
    void delete(T entity);
}
