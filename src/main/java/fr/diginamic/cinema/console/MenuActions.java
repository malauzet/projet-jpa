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

        Affichage.afficherResultats(films, Affichage::afficherFilm);
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

        Affichage.afficherResultats(acteurs, Affichage::afficherRole);
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

        Affichage.afficherResultats(films, Affichage::afficherFilm);
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

        Affichage.afficherResultats(films, Affichage::afficherFilm);
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

        Affichage.afficherResultats(acteurs, Affichage::afficherPersonne);
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

        Affichage.afficherResultats(films, Affichage::afficherFilm);
    }

    /**
     * Option 7 : recherche d'acteurs ou de films par nom (partiel), pour retrouver leur id IMDb
     * sans avoir à le connaître d'avance.
     *
     * @param scanner scanner ouvert sur l'entrée standard
     * @param service service de recherche
     */
    public static void menuRechercheParNom(Scanner scanner, RechercheService service) {

        int type;

        do {
            type = Saisie.lireEntier(scanner, "1. Acteur  \n2. Film \nChoix : ");
            if (type != 1 && type != 2) {
                System.out.println("Choix invalide, entrez 1 ou 2.");
            }
        } while (type != 1 && type != 2);

        String nom = Saisie.lireTexte(scanner, "Nom (ou partie du nom) : ");

        if (type == 1) {
            List<Personne> personnes = service.rechercherActeursParNom(nom);
            Affichage.afficherResultats(personnes, Affichage::afficherPersonne);
        } else {
            List<Film> films = service.rechercherFilmsParNom(nom);
            Affichage.afficherResultats(films, Affichage::afficherFilm);
        }
    }
}
