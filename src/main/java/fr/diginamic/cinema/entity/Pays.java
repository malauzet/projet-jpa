package fr.diginamic.cinema.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Pays d'origine du film.
 */
@Entity
@Table(name = "pays")
@Getter
@Setter
@NoArgsConstructor
public class Pays {

    /**
     * Identifiant technique auto-incrémenté.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Nom d'un pays. Unique en base.
     */
    @Column(length = 100, nullable = false, unique = true)
    private String nom;

    /**
     * URL IMDb de la page du pays.
     */
    private String url;
}
