package fr.diginamic.cinema.console;

import fr.diginamic.cinema.entity.Film;
import fr.diginamic.cinema.entity.Personne;
import fr.diginamic.cinema.entity.Role;

import java.util.List;
import java.util.function.Consumer;

/**
 * Utilitaires d'affichage console pour les entités renvoyées par RechercheService.
 * N'affiche que des champs chargés EAGER, pour ne jamais déclencher de
 * LazyInitializationException une fois l'EntityManager de la requête refermé.
 */
public class Affichage {

    private static final String SEPARATEUR = "——————————————————————————————————————————————";

    /**
     * Affiche le menu principal et ses 8 options (les 7 recherches + quitter).
     */
    public static void afficherMenu() {

        System.out.println();
        System.out.println("———————————————————— Menu ————————————————————");
        System.out.println("1. Filmographie d'un acteur");
        System.out.println("2. Casting d'un film");
        System.out.println("3. Films sortis entre deux années");
        System.out.println("4. Films communs à deux acteurs");
        System.out.println("5. Acteurs communs à deux films");
        System.out.println("6. Films d'un acteur entre deux années");
        System.out.println("7. Rechercher un acteur ou un film par nom");
        System.out.println("0. Quitter");
        System.out.println("——————————————————————————————————————————————");
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

    /**
     * Affiche une liste de résultats entre deux barres de séparation,
     * suivie du nombre de résultats trouvés (accord singulier/pluriel géré).
     *
     * @param resultats liste des résultats à afficher (peut être vide)
     * @param afficheur méthode d'affichage à appliquer à chaque résultat (ex. {@code Affichage::afficherFilm})
     * @param <T>       type des résultats affichés
     */
    public static <T> void afficherResultats(List<T> resultats, Consumer<T> afficheur) {

        System.out.println(SEPARATEUR);

        for (T resultat : resultats) {
            afficheur.accept(resultat);
        }

        System.out.println(SEPARATEUR);

        if (resultats.isEmpty()) {
            System.out.println("Aucun résultat n'a été trouvé.");
        } else if (resultats.size() == 1) {
            System.out.println("1 résultat a été trouvé.");
        } else {
            System.out.println(resultats.size() + " résultats ont été trouvés.");
        }

        System.out.println(SEPARATEUR);
    }
}
