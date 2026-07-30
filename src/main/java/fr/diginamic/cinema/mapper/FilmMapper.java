package fr.diginamic.cinema.mapper;

import fr.diginamic.cinema.entity.*;
import fr.diginamic.cinema.json.*;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Convertit les FilmJson (et leurs DTOs imbriqués) du JSON source en entités JPA prêtes à être persistées,
 * en dédoublonnant au passage les entités qui se répètent naturellement d'un film à l'autre
 * (pays, langue, genre, lieu de naissance, personne, film).
 */
public class FilmMapper {

    /**
     * Convertit un FilmJson en entité Film, en dédoublonnant par id IMDb.
     * Au premier film rencontré pour un id donné, construit l'entité complète (pays, langue, genres, réalisateurs, rôles).
     * Sur un doublon (même id déjà vu, les ~38 ids dupliqués du JSON source, correspondant à des séries/shows scrapés une fois par acteur),
     * seul l'intervalle d'années est élargi (anneeDebut = min, anneeFin = max des valeurs vues),
     * les autres champs de l'occurrence en doublon sont ignorés.
     *
     * @param dto    film brut tel qu'écrit dans le JSON
     * @param caches caches de dédoublonnage de l'import en cours, le film est ajouté à caches.films au premier passage.
     * @return l'entité Film correspondante, existante (avec années éventuellement élargies) ou nouvellement créée.
     */
    public static Film toFilm(FilmJson dto, DedupCaches caches) {

        String key = dto.getId();
        AnneeRange anneeRange = parseAnneeRange(dto.getAnneeSortie());
        Integer finCandidat = anneeRange.fin() != null ? anneeRange.fin() : anneeRange.debut();

        Film film = caches.films.get(key);

        if (film == null) {

            // Premier film rencontré avec cet id : on construit tout normalement.
            film = new Film(key);
            film.setNom(dto.getNom());
            film.setUrl(dto.getUrl());
            film.setPlot(dto.getPlot());
            film.setRating(parseRating(dto.getRating()));
            film.setAnneeDebut(anneeRange.debut());
            film.setAnneeFin(finCandidat);

            LieuTournageJson lieuTournage = dto.getLieuTournage();

            if (lieuTournage != null) {
                film.setVilleTournage(lieuTournage.getVille());
                film.setEtatDeptTournage(lieuTournage.getEtatDept());
                film.setPaysTournage(lieuTournage.getPays());
            }

            if (dto.getPays() != null) {
                film.setPays(toPays(dto.getPays(), caches));
            }

            if (dto.getLangue() != null) {
                film.setLangue(toLangue(dto.getLangue(), caches));
            }

            for (String genre : dto.getGenres()) {
                film.getGenres().add(toGenre(genre, caches));
            }

            for (PersonneJson realisateur : dto.getRealisateurs()) {
                film.getRealisateurs().add(toPersonne(realisateur, caches));
            }

            for (RoleJson roleDto : dto.getRoles()) {
                boolean principal = isPrincipal(roleDto.getActeur(), dto.getCastingPrincipal());
                film.getRoles().add(toRole(roleDto, film, principal, caches));
            }

            caches.films.put(key, film);
        } else {
            // Doublon (même id déjà vu) : on élargit seulement l'intervalle d'années.
            film.setAnneeDebut(Math.min(film.getAnneeDebut(), anneeRange.debut()));
            film.setAnneeFin(Math.max(film.getAnneeFin(), finCandidat));
        }

        return film;
    }

    /**
     * Convertit un PersonneJson en entité Personne, en réutilisant l'instance déjà connue
     * si cette personne (même id IMDb) a déjà été rencontrée dans cet import (dédoublonnage),
     * pour éviter de créer deux fois la même personne en base.
     * Le lieu de naissance n'est renseigné que si le JSON en fournit un (sinon null, plutôt que de créer un lieu de naissance vide).
     *
     * @param dto    acteur ou réalisateur brut tel qu'écrit dans le JSON.
     * @param caches caches de dédoublonnage de l'import en cours,
     *               la personne (et son lieu de naissance le cas échéant) sont ajoutés aux caches correspondants.
     * @return l'entité Personne correspondante, existante ou nouvellement créée.
     */
    private static Personne toPersonne(PersonneJson dto, DedupCaches caches) {

        String key = dto.getId();

        return caches.personnes.computeIfAbsent(key, k -> {
            Personne personne = new Personne(dto.getId());
            personne.setIdentite(dto.getIdentite());
            personne.setUrl(dto.getUrl());
            personne.setDateNaissance(parseDate(dto.getNaissance().getDateNaissance()));

            String lieu = dto.getNaissance().getLieuNaissance();

            if (lieu != null && !lieu.trim().isEmpty()) {
                personne.setLieuNaissance(toLieuNaissance(lieu, caches));
            }

            personne.setTaille(parseTaille(dto.getHeight()));

            return personne;
        });
    }

    /**
     * Convertit un RoleJson en entité Role pour un film donné.
     * Contrairement aux entités de lookup, aucun dédoublonnage n'est fait ici :
     * chaque rôle correspond à une ligne de casting distincte.
     * La personne associée est en revanche dédoublonnée via toPersonne.
     *
     * @param dto       rôle brut (personnage + acteur) tel qu'écrit dans le JSON
     * @param film      film auquel ce rôle appartient
     * @param principal true si cette personne fait partie du casting principal du film
     * @param caches    caches de dédoublonnage de l'import en cours, transmis à toPersonne
     * @return l'entité Role nouvellement créée
     */
    private static Role toRole(RoleJson dto, Film film, boolean principal, DedupCaches caches) {

        Role role = new Role();

        role.setCharacterName(dto.getCharacterName());
        role.setPrincipal(principal);
        role.setFilm(film);
        role.setPersonne(toPersonne(dto.getActeur(), caches));

        return role;
    }

    /**
     * Détermine si un acteur fait partie du casting principal d'un film,
     * en comparant son id à ceux présents dans castingPrincipal.
     *
     * @param acteur           acteur dont on veut savoir s'il est un rôle principal.
     * @param castingPrincipal liste des acteurs principaux du film, telle qu'écrite dans le JSON.
     * @return true si l'acteur figure dans castingPrincipal, false sinon.
     */
    private static boolean isPrincipal(PersonneJson acteur, List<PersonneJson> castingPrincipal) {

        return castingPrincipal.stream()
                .anyMatch(p -> p.getId().equals(acteur.getId()));
    }

    /**
     * Convertit un PaysJson en entité Pays, en réutilisant l'instance déjà connue
     * si un pays du même nom a déjà été rencontré dans cet import (dédoublonnage),
     * pour éviter de créer deux fois le même pays en base.
     *
     * @param dto    pays brut tel qu'écrit dans le JSON
     * @param caches caches de dédoublonnage de l'import en cours, le pays est ajouté à caches.pays s'il n'y était pas déjà.
     * @return l'entité Pays correspondante, existante ou nouvellement créée
     */
    private static Pays toPays(PaysJson dto, DedupCaches caches) {
        return dedupe(caches.pays, dto.getNom(), trimmed -> {
            Pays pays = new Pays();
            pays.setNom(trimmed);
            pays.setUrl(dto.getUrl());
            return pays;
        });
    }

    /**
     * Convertit un nom de langue brut en entité Langue, en réutilisant l'instance déjà connue
     * si cette langue a déjà été rencontrée dans cet import (dédoublonnage),
     * pour éviter de créer deux fois la même langue en base.
     *
     * @param nom    nom de la langue tel qu'écrit dans le JSON
     * @param caches caches de dédoublonnage de l'import en cours, la langue est ajoutée à caches.langues si elle n'y était pas déjà.
     * @return l'entité Langue correspondante, existante ou nouvellement créée.
     */
    private static Langue toLangue(String nom, DedupCaches caches) {
        return dedupe(caches.langues, nom, trimmed -> {
            Langue langue = new Langue();
            langue.setNom(trimmed);
            return langue;
        });
    }

    /**
     * Convertit un nom de genre brut en entité Genre, en réutilisant l'instance déjà connue si
     * ce genre a déjà été rencontré dans cet import (dédoublonnage),
     * pour éviter de créer eux fois le même genre en base.
     *
     * @param nom    nom du genre tel qu'écrit dans le JSON
     * @param caches caches de dédoublonnage de l'import en cours, le genre est ajouté à caches.genres s'il n'y était pas déjà
     * @return l'entité Genre correspondante, existante ou nouvellement créée
     */
    private static Genre toGenre(String nom, DedupCaches caches) {
        return dedupe(caches.genres, nom, trimmed -> {
            Genre genre = new Genre();
            genre.setNom(trimmed);
            return genre;
        });
    }

    /**
     * Convertit un libellé de lieu de naissance brut en entité LieuNaissance,
     * en réutilisant l'instance déjà connue si ce lieu a déjà été rencontré dans cet import (dédoublonnage),
     * pour éviter de créer deux fois le même lieu en base.
     *
     * @param libelle libelle du lieu de naissance tel qu'écrit dans le JSON
     * @param caches  caches de dédoublonnage de l'import en cours, le lieu est ajouté à caches.lieuxNaissance s'il n'y était pas déjà
     * @return l'entité LieuNaissance correspondante, existante ou nouvellement créée
     */
    private static LieuNaissance toLieuNaissance(String libelle, DedupCaches caches) {
        return dedupe(caches.lieuxNaissance, libelle, trimmed -> {
            LieuNaissance lieuNaissance = new LieuNaissance();
            lieuNaissance.setLibelle(trimmed);
            return lieuNaissance;
        });
    }

    /**
     * Dédoublonne une valeur brute dans un cache, en construisant une nouvelle entité
     * seulement si aucune entrée équivalente (selon cleDedoublonnage) n'existe déjà.
     *
     * @param cache   cache de dédoublonnage de l'entité concernée
     * @param raw     valeur brute telle qu'écrite dans le JSON
     * @param builder construit l'entité à partir de la valeur déjà nettoyée (trim), appelé uniquement si la clé est absente
     * @param <T>     type de l'entité dédoublonnée
     * @return l'entité correspondante, existante ou nouvellement créée
     */
    private static <T> T dedupe(Map<String, T> cache, String raw, Function<String, T> builder) {

        String trimmed = raw.trim();
        String key = cleDedoublonnage(trimmed);

        return cache.computeIfAbsent(key, k -> builder.apply(trimmed));
    }

    /**
     * Normalise une valeur pour servir de clé de dédoublonnage :
     * retire les espaces superflus,
     * la casse et les accents/diacritiques,
     * pour correspondre au comportement de comparaison de la collation utf8mb4_general_ci utilisée en base
     * (insensible à la casse et aux accents, ex. "Montreal" et "Montréal" y sont considérés comme identiques).
     *
     * @param valeur valeur brute à normaliser
     * @return la clé normalisée, utilisée uniquement pour la comparaison, jamais stockée telle quelle
     */
    private static String cleDedoublonnage(String valeur) {

        String sansAccents = Normalizer.normalize(valeur.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");

        return sansAccents.toLowerCase();
    }

    /**
     * Parse une date de sortie brute du JSON en AnneeRange.
     * On sépare avec '–' utilisé dans le JSON.
     * Si on a une seule partie, on la renvoie en date de début.
     * Si on a deux parties, on renvoie la date de début et de fin,
     * comme pour une série par exemple.
     *
     * @param raw date brute telle qu'écrite dans le JSON ("1994", "1969–1970")
     * @return la date de sortie du film, l'interval de diffusion d'une émission ou série, ou null si absente.
     */
    private static AnneeRange parseAnneeRange(String raw) {

        if (raw == null) {
            return new AnneeRange(null, null);
        }

        String trimmed = raw.trim();

        String[] parts = trimmed.split("–");

        if (parts.length == 1) {
            Integer debut = Integer.valueOf(parts[0].trim());
            return new AnneeRange(debut, null); // "1994" -> debut = 1994, fin = null
        }

        Integer debut = Integer.valueOf(parts[0].trim());
        Integer fin = Integer.valueOf(parts[1].trim());

        return new AnneeRange(debut, fin); // "1969-1970" -> debut = 1969, fin = 1970
    }

    /**
     * Parse une note brute du JSON en BigDecimal.
     *
     * @param raw note brute telle qu'écrite dans le JSON (X.X, X,X, "vide")
     * @return la note parsée ou null si elle est absente.
     */
    private static BigDecimal parseRating(String raw) {

        if (raw == null) {
            return null;
        }

        String trimmed = raw.trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        String normalized = trimmed.replace(',', '.');

        return new BigDecimal(normalized);
    }

    /**
     * Parse une date de naissance brute du JSON en LocalDate.
     * Essaie successivement le format anglais complet puis le format français complet.
     * Si aucun des deux ne correspond (date absente, partielle, ou format inattendu),
     * retourne null plutôt que de fabriquer un jour/mois/année.
     *
     * @param raw date brute telle qu'écrite dans le JSON ("May 7 1940 ", "17 août 1943 ").
     * @return la date parsée, ou null si elle est absente, incomplète ou dans un format non géré.
     */
    private static LocalDate parseDate(String raw) {

        if (raw == null) {
            return null;
        }

        String trimmed = raw.trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        DateTimeFormatter engFormatter = DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.ENGLISH);
        DateTimeFormatter frFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE);

        List<DateTimeFormatter> formatters = new ArrayList<>();
        formatters.add(engFormatter);
        formatters.add(frFormatter);

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException e) {
                // Si mauvais format, essaye le prochain.
            }
        }

        return null;
    }

    /**
     * Parse une taille brute du JSON en BigDecimal, en mètres.
     * Si la valeur inclut la mesure impériale (ex. "6′ 2½″ (1.89 m)"),
     * seule la valeur entre parenthèses est conservée.
     * Retire l'unité (" m") et normalise la virgule décimale avant de parser.
     *
     * @param raw taille brute telle qu'écrite dans le JSON ("1,70 m", "1.70 m" "6′ 2½″ (1.89 m)")
     * @return la taille parsée, ou null si elle est absente
     */
    private static BigDecimal parseTaille(String raw) {

        if (raw == null) {
            return null;
        }

        // Remplace l'espace fine insécable de convention entre valeur + unité de mesure.
        String trimmed = raw.trim().replace(' ', ' ');

        if (trimmed.isEmpty()) {
            return null;
        }

        // Certaines tailles incluent la mesure impériale, ex. "6′ 2½″ (1.89 m)".
        int parenIndex = trimmed.indexOf('(');

        String valeurMetrique = parenIndex != -1
                ? trimmed.substring(parenIndex + 1, trimmed.indexOf(')', parenIndex)) // On ne garde que la valeur entre parenthèses
                : trimmed; // Pas de parenthèses : la chaîne est déjà la valeur en mètres

        String withoutUnit = valeurMetrique.replace(" m", "");
        String normalized = withoutUnit.replace(',', '.');

        return new BigDecimal(normalized);
    }

    /**
     * Intervalle d'années issu du parsing d'anneeSortie.
     * fin vaut null quand le JSON ne donne qu'une seule année (pas d'intervalle).
     *
     * @param debut année de début
     * @param fin   année de fin, ou null si inconnue/non applicable
     */
    private record AnneeRange(Integer debut, Integer fin) {}
}
