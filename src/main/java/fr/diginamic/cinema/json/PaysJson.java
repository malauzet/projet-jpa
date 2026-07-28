package fr.diginamic.cinema.json;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Représente le bloc "pays" imbriqué dans un film dans films.json.
 */
@Getter
@Setter
@NoArgsConstructor
public class PaysJson {

    /**
     * Nom du pays brut, tel qu'écrit dans le JSON.
     */
    private String nom;

    /**
     * Url du pays brut, tel qu'écrit dans le JSON.
     */
    private String url;
}
