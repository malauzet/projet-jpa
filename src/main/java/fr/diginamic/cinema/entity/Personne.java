package fr.diginamic.cinema.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Une personne qui peut être un acteur, un réalisateur ou les deux.
 */
@Entity
@Table(name = "personne")
@Getter
@Setter
@NoArgsConstructor(force = true)
@RequiredArgsConstructor
public class Personne {

    /**
     * Identifiant IMDb de la personne.
     */
    @Id
    @Setter(AccessLevel.NONE)
    @Column(length = 15)
    private final String id;

    /**
     * Identité (prénom et nom) de la personne.
     */
    @Column(nullable = false, length = 150)
    private String identite;

    /**
     * URL IMDb de la page de la personne.
     */
    private String url;

    /**
     * Date de naissance de la personne.
     */
    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    /**
     * Taille de la personne en mètres (ex : 1.75).
     */
    @Column(precision = 3, scale = 2)
    private BigDecimal taille;

    /**
     * Lieu de naissance de la personne.
     * Lazy fetch car on n'accède pas à cette donnée.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieu_naissance_id")
    private LieuNaissance lieuNaissance;

}
