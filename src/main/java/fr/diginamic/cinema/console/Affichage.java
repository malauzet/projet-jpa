package fr.diginamic.cinema.console;

import fr.diginamic.cinema.entity.Film;
import fr.diginamic.cinema.entity.Personne;
import fr.diginamic.cinema.entity.Role;

/**
 * Utilitaires d'affichage console pour les entités renvoyées par RechercheService.
 * N'affiche que des champs chargés EAGER, pour ne jamais déclencher de
 * LazyInitializationException une fois l'EntityManager de la requête refermé.
 */
public class Affichage {

    /**
     * Affiche le menu principal et ses 7 options (les 6 recherches + quitter).
     */
    public static void afficherMenu() {

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

    /**
     * Affiche un film : id, nom, années de sortie/diffusion, note.
     *
     * @param film film à afficher
     */
    public static void afficherFilm(Film film) {

        String annees = (film.getAnneeFin() == null)
                ? String.valueOf(film.getAnneeDebut())
                : film.getAnneeDebut() + "-" + film.getAnneeFin();

        System.out.println(film.getId() + " - " + film.getNom() + " (" + annees + ") " + "note : " + film.getRating());
    }

    /**
     * Affiche un rôle : identité de l'acteur, nom du personnage, indication si principal.
     *
     * @param role rôle à afficher
     */
    public static void afficherRole(Role role) {
        System.out.println(role.getPersonne().getIdentite() + " - "
                + role.getCharacterName() + (Boolean.TRUE.equals(role.getPrincipal()) ? " (principal)" : ""));
    }

    /**
     * Affiche une personne : id IMDb, identité.
     *
     * @param personne personne à afficher
     */
    public static void afficherPersonne(Personne personne) {
        System.out.println(personne.getId() + " - " + personne.getIdentite());
    }
}
