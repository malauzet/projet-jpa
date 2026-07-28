package fr.diginamic.cinema.json;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Représente un acteur ou un réalisateur tel que décrit dans films.json.
 */
@Getter
@Setter
@NoArgsConstructor
public class PersonneJson {

    /**
     * Id IMDb brut de la personne, tel qu'écrit dans le JSON.
     */
    private String id;

    /**
     * Identité (nom et prénom) brute de la personne, telle qu'écrite dans le JSON.
     */
    private String identite;

    /**
     * Bloc "naissance" dans une personne.
     */
    private NaissanceJson naissance;

    /**
     * Url de la personne brute, telle qu'écrite dans le JSON.
     */
    private String url;

    /**
     * Height (taille) de la personne brute, telle qu'écrite dans le JSON.
     */
    private String height;
}
