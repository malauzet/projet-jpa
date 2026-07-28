package fr.diginamic.cinema.json;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Représente le bloc "lieuTournage" imbriqué dans un film dans films.json.
 */
@Getter
@Setter
@NoArgsConstructor
public class LieuTournageJson {

    /**
     * Nom de la ville de tournage brut, tel qu'écrit dans le JSON.
     */
    private String ville;

    /**
     * Nom de l'état ou département de tournage brut, tel qu'écrit dans le JSON.
     */
    private String etatDept;

    /**
     * Nom du pays de tournage brut, tel qu'écrit dans le JSON.
     */
    private String pays;
}
