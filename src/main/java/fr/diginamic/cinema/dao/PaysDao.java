package fr.diginamic.cinema.dao;

import fr.diginamic.cinema.entity.Pays;

/**
 * DAO pour l'entité Pays.
 */
public class PaysDao extends AbstractDao<Pays, Integer> {

    /**
     * Construit le DAO pour l'entité Pays.
     */
    public PaysDao() {
        super(Pays.class);
    }
}
