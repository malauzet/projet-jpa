package fr.diginamic.cinema.mapper;

import fr.diginamic.cinema.entity.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    /**
     * Ids IMDb des personnes déjà présentes en base avant cet import (préchargés par
     * ImportService.chargerExistant), pour ne pas les réinsérer lors d'un ré-import.
     */
    public final Set<String> personnesExistantes = new HashSet<>();

    /**
     * Ids IMDb des films déjà présents en base avant cet import, même usage que personnesExistantes.
     */
    public final Set<String> filmsExistants = new HashSet<>();

    /**
     * Clés de dédoublonnage (FilmMapper.cleDedoublonnage) des pays déjà présents en base avant cet import.
     */
    public final Set<String> paysExistants = new HashSet<>();

    /**
     * Clés de dédoublonnage des langues déjà présentes en base avant cet import.
     */
    public final Set<String> languesExistantes = new HashSet<>();

    /**
     * Clés de dédoublonnage des genres déjà présents en base avant cet import.
     */
    public final Set<String> genresExistants = new HashSet<>();

    /**
     * Clés de dédoublonnage des lieux de naissance déjà présents en base avant cet import.
     */
    public final Set<String> lieuxNaissanceExistants = new HashSet<>();

}
