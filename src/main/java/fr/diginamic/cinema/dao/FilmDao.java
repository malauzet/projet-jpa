package fr.diginamic.cinema.dao;

import fr.diginamic.cinema.entity.Film;

/**
 * DAO pour l'entité Film.
 */
public class FilmDao extends AbstractDao<Film, String> {

    public FilmDao() {
        super(Film.class);
    }
}
