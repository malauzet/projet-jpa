package fr.diginamic.cinema.app;

import fr.diginamic.cinema.service.ImportService;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Point d'entrée pour importer films.json en base de données.
 */
public class ImportApp {

    public static void main(String[] args) {

        Path jsonFile = Path.of("src/main/resources/films.json");

        System.out.println("Import en cours depuis " + jsonFile + " ...");

        long debut = System.currentTimeMillis();

        try {
            new ImportService().importer(jsonFile);

            long dureeSecondes = (System.currentTimeMillis() - debut) / 1000;

            System.out.println("Import terminé en " + dureeSecondes + " s.");

        } catch (IOException e) {
            System.err.println("Échec de la lecture du fichier JSON : " + e.getMessage());
        }
    }
}
