package fr.diginamic.cinema.service;

import fr.diginamic.cinema.dao.*;
import fr.diginamic.cinema.entity.Film;
import fr.diginamic.cinema.entity.Personne;
import fr.diginamic.cinema.entity.Role;

import java.util.List;

/**
 * Regroupe les 8 opérations de recherche exposées par MenuApp :
 * filmographie d'un acteur,
 * casting d'un film,
 * films entre deux années,
 * films communs à deux acteurs,
 * acteurs communs à deux films,
 * films d'un acteur entre deux années,
 * recherche d'acteurs par nom,
 * recherche de films par nom.
 * Chaque méthode délègue directement à la méthode DAO correspondante.
 */
public class RechercheService {

    private final PersonneDao personneDao = new PersonneDao();
    private final FilmDao filmDao = new FilmDao();
    private final RoleDao roleDao = new RoleDao();

    /**
     * Récupère la filmographie d'un acteur.
     *
     * @param personneId id IMDb de la personne
     * @return la liste des films où cette personne a au moins un rôle
     * @see FilmDao#findByActeurId(String)
     */
    public List<Film> filmographieActeur(String personneId) {
        return filmDao.findByActeurId(personneId);
    }

    /**
     * Récupère le casting complet d'un film.
     *
     * @param filmId id IMDb du film
     * @return la liste des rôles du film, chacun avec sa Personne déjà chargée
     * @see RoleDao#findByFilmId(String)
     */
    public List<Role> castingFilm(String filmId) {
        return roleDao.findByFilmId(filmId);
    }

    /**
     * Récupère les films sortis entre deux années données, bornes incluses.
     *
     * @param debut année de début de l'intervalle (incluse)
     * @param fin   année de fin de l'intervalle (incluse)
     * @return la liste des films dont l'année de sortie est dans cet intervalle
     * @see FilmDao#findByAnneeRange(int, int)
     */
    public List<Film> filmsEntreAnnees(int debut, int fin) {
        return filmDao.findByAnneeRange(debut, fin);
    }

    /**
     * Récupère les films communs à deux acteurs.
     *
     * @param personneId1 id IMDb du premier acteur
     * @param personneId2 id IMDb du second acteur
     * @return la liste des films où ces deux personnes ont chacune au moins un rôle
     * @see FilmDao#findCommunsEntreActeurs(String, String)
     */
    public List<Film> filmsCommuns(String personneId1, String personneId2) {
        return filmDao.findCommunsEntreActeurs(personneId1, personneId2);
    }

    /**
     * Récupère les acteurs communs à deux films.
     *
     * @param filmId1 id IMDb du premier film
     * @param filmId2 id IMDb du second film
     * @return la liste des personnes ayant au moins un rôle dans chacun des deux films
     * @see PersonneDao#findCommunsEntreFilms(String, String)
     */
    public List<Personne> acteursCommuns(String filmId1, String filmId2) {
        return personneDao.findCommunsEntreFilms(filmId1, filmId2);
    }

    /**
     * Récupère les films d'un acteur sortis entre deux années données, bornes incluses.
     *
     * @param personneId id IMDb de la personne
     * @param debut      année de début de l'intervalle (incluse)
     * @param fin        année de fin de l'intervalle (incluse)
     * @return la liste des films de cette personne dont l'année de sortie est dans cet intervalle
     * @see FilmDao#findByActeurIdAndAnneeRange(String, int, int)
     */
    public List<Film> filmsActeurEntreAnnees(String personneId, int debut, int fin) {
        return filmDao.findByActeurIdAndAnneeRange(personneId, debut, fin);
    }

    /**
     * Recherche des acteurs par nom partiel, pour retrouver leur id IMDb.
     *
     * @param nom fragment de nom recherché
     * @return la liste des personnes dont l'identité contient ce fragment
     * @see PersonneDao#findByIdentiteLike(String)
     */
    public List<Personne> rechercherActeursParNom(String nom) {
        return personneDao.findByIdentiteLike(nom);
    }

    /**
     * Recherche des films par nom partiel, pour retrouver leur id IMDb.
     *
     * @param nom fragment de nom recherché
     * @return la liste des films dont le nom contient ce fragment
     * @see FilmDao#findByNomLike(String)
     */
    public List<Film> rechercherFilmsParNom(String nom) {
        return filmDao.findByNomLike(nom);
    }
}
