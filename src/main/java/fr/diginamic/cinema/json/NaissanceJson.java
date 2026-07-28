package fr.diginamic.cinema.json;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Représente le bloc "naissance" imbriqué dans un acteur ou un réalisateur
 * dans films.json.
 */
@Getter
@Setter
@NoArgsConstructor
public class NaissanceJson {

    /**
     * Date de naissance brute, telle qu'écrite dans le JSON (ex : "May 7 1940 ").
     */
    private String dateNaissance;

    /**
     * Lieu de naissance brut, tel qu'écrit dans le JSON.
     */
    private String lieuNaissance;
}
