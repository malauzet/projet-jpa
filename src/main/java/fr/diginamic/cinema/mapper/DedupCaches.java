package fr.diginamic.cinema.mapper;

import fr.diginamic.cinema.entity.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Regroupe les caches de dédoublonnage utilisées pendant un import.
 * Chaque map associe la clé naturelle d'une entité (nom, libellé ou id IMDb)
 * à l'instance déjà construite pour cet import, afin d'éviter de créer plusieurs fois
 * la même entité quand elle réapparaît dans plusieurs films du JSON source.
 */
public class DedupCaches {

    /**
     * Pays déjà rencontrés dans cet import, par nom.
     */
    public final Map<String, Pays> pays = new HashMap<>();

    /**
     * Langues déjà rencontrées dans cet import, par nom.
     */
    public final Map<String, Langue> langues = new HashMap<>();

    /**
     * Genres déjà rencontrés dans cet import, par nom.
     */
    public final Map<String, Genre> genres = new HashMap<>();

    /**
     * Lieux de naissance déjà rencontrés dans cet import, par libellé.
     */
    public final Map<String, LieuNaissance> lieuxNaissance = new HashMap<>();

    /**
     * Personnes (acteurs et réalisateurs) déjà rencontrées dans cet import, par id IMDb.
     */
    public final Map<String, Personne> personnes = new HashMap<>();

    /**
     * Films déjà rencontrés dans cet import, par id IMDb.
     */
    public final Map<String, Film> films = new HashMap<>();

}
