package fr.diginamic.cinema.dao;

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
     * @return l'entité persistée
     */
    T save(T entity);

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
     * Supprime une entité de la base.
     *
     * @param entity l'entité à supprimer
     */
    void delete(T entity);
}
