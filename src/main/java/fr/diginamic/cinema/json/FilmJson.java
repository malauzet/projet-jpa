package fr.diginamic.cinema.json;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Représente un film dans films.json.
 */
@Getter
@Setter
@NoArgsConstructor
public class FilmJson {

    /**
     * Id IMDb brut du film, tel qu'écrit dans le JSON.
     */
    private String id;

    /**
     * Bloc "pays" dans un film.
     */
    private PaysJson pays;

    /**
     * Nom brut du film, tel qu'écrit dans le JSON.
     */
    private String nom;

    /**
     * Url IMDb brute du film, telle qu'écrite dans le JSON.
     */
    private String url;

    /**
     * Rating (note) brut du film, tel qu'écrit dans le JSON.
     */
    private String rating;

    /**
     * Plot (Synopsis) brute du film, telle qu'écrite dans le JSON.
     */
    private String plot;

    /**
     * Langue brute du film, telle qu'écrite dans le JSON.
     */
    private String langue;

    /**
     * Bloc "lieuTournage" dans un film.
     */
    private LieuTournageJson lieuTournage;

    /**
     * Bloc "realisateurs" dans un film.
     */
    private List<PersonneJson> realisateurs;

    /**
     * Bloc "castingPrincipal" dans un film.
     */
    private List<PersonneJson> castingPrincipal;

    /**
     * Année de sortie brute du film, telle qu'écrite dans le JSON.
     */
    private String anneeSortie;

    /**
     * Bloc "roles" dans un film.
     */
    private List<RoleJson> roles;

    /**
     * Genres bruts du film, tels qu'écrits dans le JSON.
     */
    private List<String> genres;
}