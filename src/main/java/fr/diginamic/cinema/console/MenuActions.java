package fr.diginamic.cinema.console;

import fr.diginamic.cinema.entity.Film;
import fr.diginamic.cinema.entity.Personne;
import fr.diginamic.cinema.entity.Role;
import fr.diginamic.cinema.service.RechercheService;

import java.util.List;
import java.util.Scanner;

/**
 * Actions du menu console : une méthode par option, chacune lisant sa saisie via Saisie,
 * appelant la recherche correspondante sur RechercheService, puis affichant le résultat via Affichage.
 */
public class MenuActions {

    /**
     * Option 1 : filmographie d'un acteur.
     *
     * @param scanner scanner ouvert sur l'entrée standard
     * @param service service de recherche
     */
    public static void menuFilmographieActeur(Scanner scanner, RechercheService service) {

        String personneId = Saisie.lireTexte(scanner, "Id IMDb de la personne : ");

        List<Film> films = service.filmographieActeur(personneId);

        if (films.isEmpty()) {
            System.out.println("Aucun film trouvé pour cet id.");
        } else {
            for (Film film : films) {
                Affichage.afficherFilm(film);
            }
        }
    }

    /**
     * Option 2 : casting d'un film.
     *
     * @param scanner scanner ouvert sur l'entrée standard
     * @param service service de recherche
     */
    public static void menuCastingFilm(Scanner scanner, RechercheService service) {

        String filmId = Saisie.lireTexte(scanner, "Id IMDb du film : ");

        List<Role> acteurs = service.castingFilm(filmId);

        if (acteurs.isEmpty()) {
            System.out.println("Aucun acteur trouvé pour cet id.");
        } else {
            for (Role acteur : acteurs) {
                Affichage.afficherRole(acteur);
            }
        }
    }

    /**
     * Option 3 : films sortis entre deux années.
     *
     * @param scanner scanner ouvert sur l'entrée standard
     * @param service service de recherche
     */
    public static void menuFilmsEntreAnnees(Scanner scanner, RechercheService service) {

        int debut = Saisie.lireEntier(scanner, "Entre : ");
        int fin = Saisie.lireEntier(scanner, "Et : ");

        List<Film> films = service.filmsEntreAnnees(debut, fin);

        if (films.isEmpty()) {
            System.out.println("Aucun film trouvé pour cette plage d'années.");
        } else {
            for (Film film : films) {
                Affichage.afficherFilm(film);
            }
        }
    }

    /**
     * Option 4 : films communs à deux acteurs.
     *
     * @param scanner scanner ouvert sur l'entrée standard
     * @param service service de recherche
     */
    public static void menuFilmsCommuns(Scanner scanner, RechercheService service) {

        String personneId1 = Saisie.lireTexte(scanner, "Id IMDb du premier acteur : ");
        String personneId2 = Saisie.lireTexte(scanner, "Id IMDb du second acteur : ");

        List<Film> films = service.filmsCommuns(personneId1, personneId2);

        if (films.isEmpty()) {
            System.out.println("Aucun film commun trouvé pour ces deux acteurs.");
        } else {
            for (Film film : films) {
                Affichage.afficherFilm(film);
            }
        }
    }

    /**
     * Option 5 : acteurs communs à deux films.
     *
     * @param scanner scanner ouvert sur l'entrée standard
     * @param service service de recherche
     */
    public static void menuActeursCommuns(Scanner scanner, RechercheService service) {

        String filmId1 = Saisie.lireTexte(scanner, "Id IMDb du premier film : ");
        String filmId2 = Saisie.lireTexte(scanner, "Id IMDb du second film : ");

        List<Personne> acteurs = service.acteursCommuns(filmId1, filmId2);

        if (acteurs.isEmpty()) {
            System.out.println("Aucun acteur commun trouvé pour ces deux films.");
        } else {
            for (Personne acteur : acteurs) {
                Affichage.afficherPersonne(acteur);
            }
        }
    }

    /**
     * Option 6 : films d'un acteur entre deux années.
     *
     * @param scanner scanner ouvert sur l'entrée standard
     * @param service service de recherche
     */
    public static void menuFilmsActeurEntreAnnees(Scanner scanner, RechercheService service) {

        String personneId = Saisie.lireTexte(scanner, "Id IMDb de la personne : ");

        int debut = Saisie.lireEntier(scanner, "Entre : ");
        int fin = Saisie.lireEntier(scanner, "Et : ");

        List<Film> films = service.filmsActeurEntreAnnees(personneId, debut, fin);

        if (films.isEmpty()) {
            System.out.println("Aucun film trouvé pour cet acteur sur cette plage d'années.");
        } else {
            for (Film film : films) {
                Affichage.afficherFilm(film);
            }
        }
    }
}
