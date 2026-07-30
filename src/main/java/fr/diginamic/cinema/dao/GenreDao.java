package fr.diginamic.cinema.dao;

import fr.diginamic.cinema.entity.Genre;

/**
 * DAO pour l'entité Genre.
 */
public class GenreDao extends AbstractDao<Genre, Integer> {

    /**
     * Construit le DAO pour l'entité Genre.
     */
    public GenreDao() {
        super(Genre.class);
    }
}
