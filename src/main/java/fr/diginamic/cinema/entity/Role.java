package fr.diginamic.cinema.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Rôle dans un film.
 */
@Entity
@Table(name = "role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    /**
     * Identifiant technique auto-incrémenté
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Nom du personnage dans le film.
     */
    @Column(name = "character_name", length = 150)
    private String characterName;

    /**
     * Boolean pour savoir si le personnage fait parti du casting principal.
     */
    @Column(nullable = false)
    private Boolean principal;

    /**
     * Film auquel le personnage est lié.
     */
    @ManyToOne
    @JoinColumn(name = "film_id", nullable = false)
    private Film film;

    /**
     * Personne qui joue le rôle.
     */
    @ManyToOne
    @JoinColumn(name = "personne_id", nullable = false)
    private Personne personne;
}
