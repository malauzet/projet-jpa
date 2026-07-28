package fr.diginamic.cinema.dao;

import fr.diginamic.cinema.entity.Langue;

/**
 * DAO pour l'entité Langue.
 */
public class LangueDao extends AbstractDao<Langue, Integer> {

    public LangueDao() {
        super(Langue.class);
    }
}
