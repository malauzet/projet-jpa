package fr.diginamic.cinema.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Genre d'un film.
 */
@Entity
@Table(name = "genre")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Genre {

    /**
     * Identifiant technique auto-incrémenté.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Nom d'un genre de film. Unique en base.
     */
    @Column(length = 50, nullable = false, unique = true)
    private String nom;
}
