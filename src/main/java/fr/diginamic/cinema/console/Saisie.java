package fr.diginamic.cinema.console;

import java.util.Scanner;

/**
 * Utilitaires de lecture au clavier pour l'application console (MenuApp).
 */
public class Saisie {

    /**
     * Lit un choix de menu valide (entre 0 et 6),
     * en reproposant tant que la saisie n'est pas un entier dans cette plage.
     *
     * @param scanner scanner ouvert sur l'entrée standard
     * @return le choix de menu, un entier entre 0 et 6 inclus
     */
    public static int lireChoix(Scanner scanner) {

        while (true) {

            int choix = lireEntier(scanner, "Votre choix : ");

            if (choix >= 0 && choix <= 6) {
                return choix;
            }

            System.out.println("Nombre invalide, entrez un nombre entre 0 et 6.");
        }
    }

    /**
     * Affiche un message puis lit un entier au clavier,
     * en reproposant tant que la saisie n'est pas un entier valide.
     *
     * @param scanner scanner ouvert sur l'entrée standard
     * @param message message à afficher avant la saisie
     * @return l'entier saisi par l'utilisateur
     */
    public static int lireEntier(Scanner scanner, String message) {

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

    /**
     * Affiche un message puis lit une ligne de texte au clavier.
     *
     * @param scanner scanner ouvert sur l'entrée standard
     * @param message message à afficher avant la saisie
     * @return la ligne saisie par l'utilisateur
     */
    public static String lireTexte(Scanner scanner, String message) {
        System.out.print(message);
        System.out.flush();
        return scanner.nextLine();
    }
}
