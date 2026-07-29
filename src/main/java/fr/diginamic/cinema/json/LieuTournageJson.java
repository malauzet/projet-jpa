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
     * Nom brut de la ville de tournage, tel qu'écrit dans le JSON.
     */
    private String ville;

    /**
     * Nom brut de l'état ou département de tournage, tel qu'écrit dans le JSON.
     */
    private String etatDept;

    /**
     * Nom brut du pays de tournage, tel qu'écrit dans le JSON.
     */
    private String pays;
}
