package fr.diginamic.cinema.app;

import fr.diginamic.cinema.console.Affichage;
import fr.diginamic.cinema.console.MenuActions;
import fr.diginamic.cinema.console.Saisie;
import fr.diginamic.cinema.service.RechercheService;

import java.util.Scanner;

/**
 * Point d'entrée de l'application menu console.
 */
public class MenuApp {

    public static void main(String[] args) {

        RechercheService service = new RechercheService();
        Scanner scanner = new Scanner(System.in);
        int choix;

        do {
            Affichage.afficherMenu();

            choix = Saisie.lireChoix(scanner);

            switch (choix) {
                case 1 -> MenuActions.menuFilmographieActeur(scanner, service);
                case 2 -> MenuActions.menuCastingFilm(scanner, service);
                case 3 -> MenuActions.menuFilmsEntreAnnees(scanner, service);
                case 4 -> MenuActions.menuFilmsCommuns(scanner, service);
                case 5 -> MenuActions.menuActeursCommuns(scanner, service);
                case 6 -> MenuActions.menuFilmsActeurEntreAnnees(scanner, service);
                case 0 -> System.out.println("Au revoir.");
            }
        } while (choix != 0);

        scanner.close();
    }
}
