package fr.diginamic.cinema.dao;

import fr.diginamic.cinema.entity.LieuNaissance;

/**
 * DAO pour l'entité LieuNaissance.
 */
public class LieuNaissanceDao extends AbstractDao<LieuNaissance, Integer> {

    public LieuNaissanceDao() {
        super(LieuNaissance.class);
    }
}
