package fr.diginamic.cinema.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Un film.
 */
@Entity
@Table(name = "film")
@Getter
@Setter
@NoArgsConstructor(force = true)
@RequiredArgsConstructor
public class Film {

    /**
     * Identifiant IMDb du film
     */
    @Id
    @Setter(AccessLevel.NONE)
    @Column(length = 15)
    private final String id;

    /**
     * Nom du film.
     */
    @Column(nullable = false)
    private String nom;

    /**
     * URL IMDb de la page du film.
     */
    private String url;

    /**
     * Rating du film.
     */
    @Column(precision = 3, scale = 1)
    private BigDecimal rating;

    /**
     * Synopsis du film.
     */
    @Lob
    private String plot;

    /**
     * Année de sortie du film ou année de début d'une série, show ...etc
     */
    @Column(name = "annee_debut")
    private Integer anneeDebut;

    /**
     * Année de fin d'une série, show ...etc
     */
    @Column(name = "annee_fin")
    private Integer anneeFin;

    /**
     * Ville du tournage.
     */
    @Column(name = "ville_tournage")
    private String villeTournage;

    /**
     * État ou département du tournage.
     */
    @Column(name = "etat_dept_tournage")
    private String etatDeptTournage;

    /**
     * Pays du tournage.
     */
    @Column(name = "pays_tournage")
    private String paysTournage;

    /**
     * Pays d'origine du film.
     */
    @ManyToOne
    @JoinColumn(name = "pays_id")
    private Pays pays;

    /**
     * Langue du film.
     */
    @ManyToOne
    @JoinColumn(name = "langue_id")
    private Langue langue;

    /**
     * Genres du film.
     */
    @ManyToMany
    @JoinTable(
            name = "film_genre",
            joinColumns = @JoinColumn(name = "film_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private Set<Genre> genres = new HashSet<>();

    /**
     * Réalisateurs du film.
     */
    @ManyToMany
    @JoinTable(
            name = "film_realisateur",
            joinColumns = @JoinColumn(name = "film_id"),
            inverseJoinColumns = @JoinColumn(name = "personne_id")
    )
    private Set<Personne> realisateurs = new HashSet<>();

    /**
     * Rôles dans le film.
     */
    @OneToMany(mappedBy = "film")
    private Set<Role> roles  = new HashSet<>();
}