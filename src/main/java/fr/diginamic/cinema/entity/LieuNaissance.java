package fr.diginamic.cinema.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Lieu de naissance d'une personne.
 */
@Entity
@Table(name = "lieu_naissance")
@Getter
@Setter
@NoArgsConstructor
public class LieuNaissance {

    /**
     * Identifiant technique auto-incrémenté.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Libelle d'un lieu de naissance. Unique en base.
     */
    @Column(nullable = false, unique = true)
    private String libelle;
}
