package fr.diginamic.cinema.json;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Représente un rôle tel que décrit dans films.json.
 */
@Getter
@Setter
@NoArgsConstructor
public class RoleJson {

    /**
     * Nom brut du personnage, tel qu'écrit dans le JSON.
     */
    private String characterName;

    /**
     * Bloc "acteur" dans un rôle.
     */
    private PersonneJson acteur;
}
