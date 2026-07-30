package fr.diginamic.cinema.app;

import fr.diginamic.cinema.entity.Film;
import fr.diginamic.cinema.entity.Personne;
import fr.diginamic.cinema.entity.Role;
import fr.diginamic.cinema.service.RechercheService;

import java.util.List;
import java.util.Scanner;

public class MenuApp {

    static void main() {

        RechercheService service = new RechercheService();
        Scanner scanner = new Scanner(System.in);
        int choix;

        do {
            afficherMenu();

            choix = lireChoix(scanner);

            switch (choix) {
                case 1 -> menuFilmographieActeur(scanner, service);
                case 2 -> menuCastingFilm(scanner, service);
                case 3 -> menuFilmsEntreAnnees(scanner, service);
                case 4 -> menuFilmsCommuns(scanner, service);
                case 5 -> menuActeursCommuns(scanner, service);
                case 6 -> menuFilmsActeurEntreAnnees(scanner, service);
                case 0 -> System.out.println("Au revoir.");
            }
        } while (choix != 0);

        scanner.close();
    }

    private static void afficherMenu() {

        System.out.println();
        System.out.println("=== Menu ===");
        System.out.println("1. Filmographie d'un acteur");
        System.out.println("2. Casting d'un film");
        System.out.println("3. Films sortis entre deux années");
        System.out.println("4. Films communs à deux acteurs");
        System.out.println("5. Acteurs communs à deux films");
        System.out.println("6. Films d'un acteur entre deux années");
        System.out.println("0. Quitter");
    }

    private static int lireChoix(Scanner scanner) {

        while (true) {

            int choix = lireEntier(scanner, "Votre choix : ");

            if (choix >= 0 && choix <= 6) {
                return choix;
            }

            System.out.println("Nombre invalide, entrez un nombre entre 0 et 6.");
        }
    }

    private static void afficherFilm(Film film) {

        String annees = (film.getAnneeFin() == null)
                ? String.valueOf(film.getAnneeDebut())
                : film.getAnneeDebut() + "-" + film.getAnneeFin();

        System.out.println(film.getId() + " - " + film.getNom() + " (" + annees + ") " + "note : " + film.getRating());
    }

    private static void menuFilmographieActeur(Scanner scanner, RechercheService service) {

        String personneId = lireTexte(scanner, "Id IMDb de la personne : ");

        List<Film> films = service.filmographieActeur(personneId);

        if (films.isEmpty()) {
            System.out.println("Aucun film trouvé pour cet id.");
        } else {
            for (Film film : films) {
                afficherFilm(film);
            }
        }
    }

    private static void afficherRole(Role role) {
        System.out.println(role.getPersonne().getIdentite() + " - "
                + role.getCharacterName() + (Boolean.TRUE.equals(role.getPrincipal()) ? " (principal)" : ""));
    }

    private static void menuCastingFilm(Scanner scanner, RechercheService service) {

        String filmId = lireTexte(scanner, "Id IMDb du film : ");

        List<Role> acteurs = service.castingFilm(filmId);

        if (acteurs.isEmpty()) {
            System.out.println("Aucun acteur trouvé pour cet id.");
        } else {
            for (Role acteur : acteurs) {
                afficherRole(acteur);
            }
        }
    }

    private static void menuFilmsEntreAnnees(Scanner scanner, RechercheService service) {

        int debut = lireEntier(scanner, "Entre : ");
        int fin = lireEntier(scanner, "Et : ");

        List<Film> films = service.filmsEntreAnnees(debut, fin);

        if (films.isEmpty()) {
            System.out.println("Aucun film trouvé pour cette plage d'années.");
        } else {
            for (Film film : films) {
                afficherFilm(film);
            }
        }
    }

    private static int lireEntier(Scanner scanner, String message) {

        while (true) {

            System.out.print(message);
            System.out.flush();

            String ligne = scanner.nextLine();

            try {
                return Integer.parseInt(ligne.trim());
            } catch (NumberFormatException e) {
                System.out.println("Nombre invalide, veuillez entrer un nombre entier.");
            }
        }
    }

    private static String lireTexte(Scanner scanner, String message) {
        System.out.print(message);
        System.out.flush();
        return scanner.nextLine();
    }

    private static void menuFilmsCommuns(Scanner scanner, RechercheService service) {

        String personneId1 = lireTexte(scanner, "Id IMDb du premier acteur : ");
        String personneId2 = lireTexte(scanner, "Id IMDb du second acteur : ");

        List<Film> films = service.filmsCommuns(personneId1, personneId2);

        if (films.isEmpty()) {
            System.out.println("Aucun film commun trouvé pour ces deux acteurs.");
        } else {
            for (Film film : films) {
                afficherFilm(film);
            }
        }
    }

    private static void afficherPersonne(Personne personne) {
        System.out.println(personne.getId() + " - " + personne.getIdentite());
    }

    private static void menuActeursCommuns(Scanner scanner, RechercheService service) {

        String filmId1 = lireTexte(scanner, "Id IMDb du premier film : ");
        String filmId2 = lireTexte(scanner, "Id IMDb du second film : ");

        List<Personne> acteurs = service.acteursCommuns(filmId1, filmId2);

        if (acteurs.isEmpty()) {
            System.out.println("Aucun acteur commun trouvé pour ces deux films.");
        } else {
            for (Personne acteur : acteurs) {
                afficherPersonne(acteur);
            }
        }
    }

    private static void menuFilmsActeurEntreAnnees(Scanner scanner, RechercheService service) {

        String personneId = lireTexte(scanner, "Id IMDb de la personne : ");

        int debut = lireEntier(scanner, "Entre : ");
        int fin = lireEntier(scanner, "Et : ");

        List<Film> films = service.filmsActeurEntreAnnees(personneId, debut, fin);

        if (films.isEmpty()) {
            System.out.println("Aucun film trouvé pour cet acteur sur cette plage d'années.");
        } else {
            for (Film film : films) {
                afficherFilm(film);
            }
        }
    }
}
