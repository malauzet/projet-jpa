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
     * Précharge dans les caches toutes les entités déjà présentes en base
     * (pays, langues, genres, lieux de naissance, personnes, films),
     * avant le mapping du JSON, pour que FilmMapper les retrouve comme des doublons
     * plutôt que d'en recréer des instances qui casseraient les contraintes uniques à la persistance.
     * Les ensembles xxxExistants(es) de caches retiennent les clés déjà présentes avant ce run,
     * pour que persister() sache lesquelles ne pas réinsérer.
     *
     * @param caches caches de dédoublonnage à préremplir avec l'état actuel de la base
     */
    private void chargerExistant(DedupCaches caches) {

        for (Personne personne : personneDao.findAll()) {
            caches.personnes.put(personne.getId(), personne);
            caches.personnesExistantes.add(personne.getId());
        }

        for (Film film : filmDao.findAll()) {
            caches.films.put(film.getId(), film);
            caches.filmsExistants.add(film.getId());
        }

        for (Pays pays : paysDao.findAll()) {
            String key = FilmMapper.cleDedoublonnage(pays.getNom());
            caches.pays.put(key, pays);
            caches.paysExistants.add(key);
        }

        for (Langue langue : langueDao.findAll()) {
            String key = FilmMapper.cleDedoublonnage(langue.getNom());
            caches.langues.put(key, langue);
            caches.languesExistantes.add(key);
        }

        for (Genre genre : genreDao.findAll()) {
            String key = FilmMapper.cleDedoublonnage(genre.getNom());
            caches.genres.put(key, genre);
            caches.genresExistants.add(key);
        }

        for (LieuNaissance lieuNaissance : lieuNaissanceDao.findAll()) {
            String key = FilmMapper.cleDedoublonnage(lieuNaissance.getLibelle());
            caches.lieuxNaissance.put(key, lieuNaissance);
            caches.lieuxNaissanceExistants.add(key);
        }
    }

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

        // // 2bis. précharger l'existant en base pour dédoublonnage éventuel
        chargerExistant(caches);

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

        List<Pays> nouveauxPays = new ArrayList<>();
        for (Pays pays : caches.pays.values()) {
            String key = FilmMapper.cleDedoublonnage(pays.getNom());
            if (!caches.paysExistants.contains(key)) {
                nouveauxPays.add(pays);
            }
        }
        paysDao.saveAll(nouveauxPays);

        List<Langue> nouvellesLangues = new ArrayList<>();
        for (Langue langue : caches.langues.values()) {
            String key = FilmMapper.cleDedoublonnage(langue.getNom());
            if (!caches.languesExistantes.contains(key)) {
                nouvellesLangues.add(langue);
            }
        }
        langueDao.saveAll(nouvellesLangues);

        List<Genre> nouveauxGenres = new ArrayList<>();
        for (Genre genre : caches.genres.values()) {
            String key = FilmMapper.cleDedoublonnage(genre.getNom());
            if (!caches.genresExistants.contains(key)) {
                nouveauxGenres.add(genre);
            }
        }
        genreDao.saveAll(nouveauxGenres);

        List<LieuNaissance> nouveauxLieuNaissance = new ArrayList<>();
        for (LieuNaissance lieuNaissance : caches.lieuxNaissance.values()) {
            String key = FilmMapper.cleDedoublonnage(lieuNaissance.getLibelle());
            if (!caches.lieuxNaissanceExistants.contains(key)) {
                nouveauxLieuNaissance.add(lieuNaissance);
            }
        }
        lieuNaissanceDao.saveAll(nouveauxLieuNaissance);

        List<Personne> nouvellesPersonnes = new ArrayList<>();
        for (Personne personne : caches.personnes.values()) {
            if (!caches.personnesExistantes.contains(personne.getId())) {
                nouvellesPersonnes.add(personne);
            }
        }
        personneDao.saveAll(nouvellesPersonnes);

        List<Film> nouveauxFilms = new ArrayList<>();
        List<Film> filmsAMettreAJour = new ArrayList<>();
        for (Film film : caches.films.values()) {
            if (caches.filmsExistants.contains(film.getId())) {
                filmsAMettreAJour.add(film);
            } else {
                nouveauxFilms.add(film);
            }
        }
        filmDao.saveAll(nouveauxFilms);
        filmDao.updateAll(filmsAMettreAJour);

        List<Role> roles = new ArrayList<>();
        for (Film film : nouveauxFilms) {
            roles.addAll(film.getRoles());
        }
        roleDao.saveAll(roles);
    }
}