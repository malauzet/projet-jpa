package fr.diginamic.cinema.dao;

import fr.diginamic.cinema.entity.Personne;

/**
 * DAO pour l'entité Personne.
 */
public class PersonneDao extends AbstractDao<Personne, String> {

    public PersonneDao() {
        super(Personne.class);
    }
}
