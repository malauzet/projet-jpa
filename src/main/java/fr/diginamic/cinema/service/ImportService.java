package fr.diginamic.cinema.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import fr.diginamic.cinema.dao.*;
import fr.diginamic.cinema.entity.*;
import fr.diginamic.cinema.json.FilmJson;
import fr.diginamic.cinema.mapper.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestre l'import de films.json en base : lit le fichier JSON, le convertit en
 * entités dédoublonnées via FilmMapper, puis persiste le tout via les DAOs.
 */
public class ImportService {

    private final PaysDao paysDao = new PaysDao();
    private final LangueDao langueDao = new LangueDao();
    private final GenreDao genreDao = new GenreDao();
    private final LieuNaissanceDao lieuNaissanceDao = new LieuNaissanceDao();
    private final PersonneDao personneDao = new PersonneDao();
    private final FilmDao filmDao = new FilmDao();
    private final RoleDao roleDao = new RoleDao();

    /**
     * Importe le fichier JSON donné en base de données.
     *
     * @param jsonFile chemin vers le fichier films.json à importer
     * @throws IOException si le fichier ne peut pas être lu
     */
    public void importer(Path jsonFile) throws IOException {

        // 1. lireFilms
        List<FilmJson> filmsJson = lireFilms(jsonFile);

        // 2. create DedupCaches
        DedupCaches caches = new DedupCaches();

        // 3. for each FilmJson: FilmMapper.toFilm(dto, caches), then persister(film, caches)
        for (FilmJson dto : filmsJson) {
            FilmMapper.toFilm(dto, caches);
        }
        persister(caches);
    }

    /**
     * Lit et désérialise le fichier JSON en une liste de FilmJson.
     *
     * @param jsonFile chemin vers le fichier JSON à lire
     * @return la liste des films bruts tels que décrits dans le JSON
     * @throws IOException si le fichier ne peut pas être lu
     */
    private List<FilmJson> lireFilms(Path jsonFile) throws IOException {

        // ObjectMapper -> List<FilmJson>
        ObjectMapper mapper = new ObjectMapper();

        CollectionType listType = mapper.getTypeFactory().constructCollectionType(List.class, FilmJson.class);

        String content = Files.readString(jsonFile);

        return mapper.readValue(content, listType);
    }

    /**
     * Persiste toutes les entités dédoublonnées accumulées dans caches, dans l'ordre imposé
     * par les contraintes de clé étrangère : entités de lookup (pays, langue, genre, lieu de
     * naissance) d'abord, puis personnes, puis films, puis rôles.
     *
     * @param caches caches de dédoublonnage remplies pendant le mapping de tous les films
     */
    private void persister(DedupCaches caches) {

        for (Pays pays : caches.pays.values()) {
            paysDao.save(pays);
        }
        for (Langue langue : caches.langues.values()) {
            langueDao.save(langue);
        }
        for (Genre genre : caches.genres.values()) {
            genreDao.save(genre);
        }
        for (LieuNaissance lieu : caches.lieuxNaissance.values()) {
            lieuNaissanceDao.save(lieu);
        }

        for (Personne personne : caches.personnes.values()) {
            personneDao.save(personne);
        }

        for (Film film : caches.films.values()) {
            filmDao.save(film);
        }

        List<Role> roles = new ArrayList<>();
        for (Film film : caches.films.values()) {
            roles.addAll(film.getRoles());
        }
        for (Role role : roles) {
            roleDao.save(role);
        }
    }
}