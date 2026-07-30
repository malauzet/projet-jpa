package fr.diginamic.cinema.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Langue d'un film.
 */
@Entity
@Table(name = "langue")
@Getter
@Setter
@NoArgsConstructor
public class Langue {

    /**
     * Identifiant technique auto-incrémenté.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Nom de la langue. Unique en base.
     */
    @Column(length = 50, nullable = false, unique = true)
    private String nom;
}
