package fr.diginamic.cinema.dao;

import fr.diginamic.cinema.entity.Role;

/**
 * DAO pour l'entité Role.
 */
public class RoleDao extends AbstractDao<Role, Integer> {

    public RoleDao() {
        super(Role.class);
    }
}
