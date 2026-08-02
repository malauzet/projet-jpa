package fr.diginamic.cinema.mapper;

import fr.diginamic.cinema.mapper.FilmMapper.AnneeRange;
import fr.diginamic.cinema.entity.*;
import fr.diginamic.cinema.json.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires des méthodes de parsing/normalisation pures de FilmMapper
 * (parseDate, parseRating, parseTaille, parseAnneeRange, cleDedoublonnage).
 * Plusieurs cas correspondent directement à des bugs réels trouvés en testant
 * contre les vraies données : ce sont des tests de non-régression sur ces incidents précis.
 */
public class FilmMapperTest {

    // --- parseDate ---

    @Test
    @DisplayName("parseDate : format anglais complet")
    void parseDate_formatAnglaisComplet() {
        assertEquals(LocalDate.of(1940, 5, 7), FilmMapper.parseDate("May 7 1940 "));
    }

    @Test
    @DisplayName("parseDate : format français complet")
    void parseDate_formatFrancaisComplet() {
        assertEquals(LocalDate.of(1943, 8, 17), FilmMapper.parseDate("17 août 1943 "));
    }

    @Test
    @DisplayName("parseDate : mois + jour sans année -> null (pas de date partielle)")
    void parseDate_sansAnnee_renvoieNull() {
        assertNull(FilmMapper.parseDate("May 7 "));
    }

    @Test
    @DisplayName("parseDate : année seule -> null")
    void parseDate_anneeSeule_renvoieNull() {
        assertNull(FilmMapper.parseDate("1940 "));
    }

    @Test
    @DisplayName("parseDate : chaîne vide -> null")
    void parseDate_vide_renvoieNull() {
        assertNull(FilmMapper.parseDate(""));
    }

    @Test
    @DisplayName("parseDate : null -> null")
    void parseDate_null_renvoieNull() {
        assertNull(FilmMapper.parseDate(null));
    }


    // --- parseRating ---

    @Test
    @DisplayName("parseRating : format point")
    void parseRating_formatPoint() {
        assertEquals(new BigDecimal("6.3"), FilmMapper.parseRating("6.3"));
    }

    @Test
    @DisplayName("parseRating : format virgule")
    void parseRating_formatVirgule() {
        assertEquals(new BigDecimal("6.3"), FilmMapper.parseRating("6,3"));
    }

    @Test
    @DisplayName("parseRating : chaîne vide -> null")
    void parseRating_vide_renvoieNull() {
        assertNull(FilmMapper.parseRating(""));
    }

    @Test
    @DisplayName("parseRating : null -> null")
    void parseRating_null_renvoieNull() {
        assertNull(FilmMapper.parseRating(null));
    }


    // --- parseTaille ---

    @Test
    @DisplayName("parseTaille : format point")
    void parseTaille_formatPoint() {
        assertEquals(new BigDecimal("1.70"), FilmMapper.parseTaille("1.70 m"));
    }

    @Test
    @DisplayName("parseTaille : format virgule")
    void parseTaille_formatVirgule() {
        assertEquals(new BigDecimal("1.70"), FilmMapper.parseTaille("1,70 m"));
    }

    @Test
    @DisplayName("parseTaille : espace fine insécable")
    void parseTaille_espaceFineInsecable() {
        assertEquals(new BigDecimal("1.70"), FilmMapper.parseTaille("1,70\u202Fm"));
    }

    @Test
    @DisplayName("parseTaille : mesure impériale entre parenthèses -> ne garde que la valeur métrique")
    void parseTaille_mesureImperiale() {
        assertEquals(new BigDecimal("1.89"), FilmMapper.parseTaille("6′ 2½″ (1.89 m)"));
    }

    @Test
    @DisplayName("parseTaille : chaîne vide -> null")
    void parseTaille_vide_renvoieNull() {
        assertNull(FilmMapper.parseTaille(""));
    }

    @Test
    @DisplayName("parseTaille : null -> null")
    void parseTaille_null_renvoieNull() {
        assertNull(FilmMapper.parseTaille(null));
    }


    // --- parseAnneeRange ---

    @Test
    @DisplayName("parseAnneeRange : année seule -> debut renseigné, fin null")
    void parseAnneeRange_anneeSeule() {
        assertEquals(new AnneeRange(1994, null), FilmMapper.parseAnneeRange("1994"));
    }

    @Test
    @DisplayName("parseAnneeRange : intervalle sur tiret demi-cadratin -> debut et fin renseignés")
    void parseAnneeRange_intervalle() {
        assertEquals(new AnneeRange(1969, 1970), FilmMapper.parseAnneeRange("1969\u20131970"));
    }

    @Test
    @DisplayName("parseAnneeRange : null -> AnneeRange(null, null), jamais null lui-même")
    void parseAnneeRange_null() {
        assertEquals(new AnneeRange(null, null), FilmMapper.parseAnneeRange(null));
    }


    // --- cleDedoublonnage ---

    @Test
    @DisplayName("cleDedoublonnage : trim + minuscules")
    void cleDedoublonnage_trimEtMinuscules() {
        assertEquals("montreal", FilmMapper.cleDedoublonnage("  Montreal  "));
    }

    @Test
    @DisplayName("cleDedoublonnage : insensible à la casse")
    void cleDedoublonnage_insensibleCasse() {
        assertEquals(
                FilmMapper.cleDedoublonnage("Newcastle upon Tyne, England, UK"),
                FilmMapper.cleDedoublonnage("Newcastle Upon Tyne, England, UK"));
    }

    @Test
    @DisplayName("cleDedoublonnage : insensible aux accents")
    void cleDedoublonnage_insensibleAccents() {
        assertEquals(
                FilmMapper.cleDedoublonnage("Montreal, Quebec, Canada"),
                FilmMapper.cleDedoublonnage("Montréal, Québec, Canada"));
    }

    @Test
    @DisplayName("cleDedoublonnage : insensible à une espace finale superflue")
    void cleDedoublonnage_insensibleEspaceFinale() {
        assertEquals(
                FilmMapper.cleDedoublonnage("Magog, Québec, Canada"),
                FilmMapper.cleDedoublonnage("Magog, Québec, Canada "));
    }


    // --- toFilm : fusion anneeDebut/anneeFin sur les doublons ---

    private static FilmJson creerFilmJson(String id, String nom, String anneeSortie) {
        FilmJson dto = new FilmJson();
        dto.setId(id);
        dto.setNom(nom);
        dto.setAnneeSortie(anneeSortie);
        dto.setGenres(List.of());
        dto.setRealisateurs(List.of());
        dto.setRoles(List.of());
        return dto;
    }

    @Test
    @DisplayName("toFilm : sur un doublon, anneeFin s'élargit si une occurrence plus tardive apparaît")
    void toFilm_doublon_elargitAnneeFin() {
        DedupCaches caches = new DedupCaches();

        FilmMapper.toFilm(creerFilmJson("tt0001", "Show", "2010"), caches);
        Film film = FilmMapper.toFilm(creerFilmJson("tt0001", "Show", "2015"), caches);

        assertEquals(2010, film.getAnneeDebut());
        assertEquals(2015, film.getAnneeFin());
    }

    @Test
    @DisplayName("toFilm : sur un doublon, anneeDebut se réduit si une occurrence plus ancienne apparaît")
    void toFilm_doublon_reduitAnneeDebut() {
        DedupCaches caches = new DedupCaches();

        FilmMapper.toFilm(creerFilmJson("tt0002", "Show", "2015"), caches);
        Film film = FilmMapper.toFilm(creerFilmJson("tt0002", "Show", "2010"), caches);

        assertEquals(2010, film.getAnneeDebut());
        assertEquals(2015, film.getAnneeFin());
    }

    @Test
    @DisplayName("toFilm : intervalle + année seule combinés")
    void toFilm_doublon_intervalleEtAnneeSeuleCombines() {
        DedupCaches caches = new DedupCaches();

        FilmMapper.toFilm(creerFilmJson("tt0072562", "Saturday Night Live", "1975\u20131979"), caches);
        Film film = FilmMapper.toFilm(creerFilmJson("tt0072562", "Saturday Night Live", "2019"), caches);

        assertEquals(1975, film.getAnneeDebut());
        assertEquals(2019, film.getAnneeFin());
    }

    @Test
    @DisplayName("toFilm : sur un doublon, les autres champs restent ceux de la première occurrence")
    void toFilm_doublon_autresChampsIgnores() {
        DedupCaches caches = new DedupCaches();

        FilmMapper.toFilm(creerFilmJson("tt0003", "Premier nom", "2010"), caches);
        Film film = FilmMapper.toFilm(creerFilmJson("tt0003", "Nom different ignore", "2015"), caches);

        assertEquals("Premier nom", film.getNom());
    }

    @Test
    @DisplayName("toFilm : sur un doublon, renvoie la même instance (dédoublonnage par id)")
    void toFilm_doublon_renvoieMemeInstance() {
        DedupCaches caches = new DedupCaches();

        Film premier = FilmMapper.toFilm(creerFilmJson("tt0004", "Show", "2010"), caches);
        Film second = FilmMapper.toFilm(creerFilmJson("tt0004", "Show", "2015"), caches);

        assertSame(premier, second);
    }


    // --- toFilm : gardes sur les clés optionnelles absentes ---

    @Test
    @DisplayName("toFilm : pays/langue/lieuTournage absents ne lèvent pas de NullPointerException")
    void toFilm_champsOptionnelsAbsents_neLeventPasDException() {
        DedupCaches caches = new DedupCaches();

        Film film = FilmMapper.toFilm(creerFilmJson("tt0005", "Film sans champs optionnels", "2000"), caches);

        assertNull(film.getPays());
        assertNull(film.getLangue());
        assertNull(film.getVilleTournage());
    }

    @Test
    @DisplayName("toFilm : les champs de tournage sont trimés avant d'être assignés")
    void toFilm_lieuTournage_trimApplique() {
        DedupCaches caches = new DedupCaches();
        FilmJson dto = creerFilmJson("tt0006", "Film avec tournage", "2000");

        LieuTournageJson lieuTournage = new LieuTournageJson();
        lieuTournage.setVille(" Hollywood ");
        lieuTournage.setEtatDept(" California");
        lieuTournage.setPays(" USA");
        dto.setLieuTournage(lieuTournage);

        Film film = FilmMapper.toFilm(dto, caches);

        assertEquals("Hollywood", film.getVilleTournage());
        assertEquals("California", film.getEtatDeptTournage());
        assertEquals("USA", film.getPaysTournage());
    }

    @Test
    @DisplayName("toFilm : langue valant \"None\" est ignorée")
    void toFilm_langueNone_estIgnoree() {
        DedupCaches caches = new DedupCaches();
        FilmJson dto = creerFilmJson("tt0007", "Film sans langue connue", "2000");
        dto.setLangue("None");

        Film film = FilmMapper.toFilm(dto, caches);

        assertNull(film.getLangue());
    }


    // --- toPersonne : garde sur lieuNaissance vide ---

    private static PersonneJson creerPersonneJson(String id, String identite, String lieuNaissance) {
        NaissanceJson naissance = new NaissanceJson();
        naissance.setLieuNaissance(lieuNaissance);

        PersonneJson dto = new PersonneJson();
        dto.setId(id);
        dto.setIdentite(identite);
        dto.setNaissance(naissance);
        return dto;
    }

    @Test
    @DisplayName("toPersonne : lieuNaissance vide (\"\") ne crée pas de LieuNaissance vide")
    void toPersonne_lieuNaissanceVide_neCreePasDeLieuNaissance() {
        DedupCaches caches = new DedupCaches();

        Personne personne = FilmMapper.toPersonne(creerPersonneJson("nm0001", "Test Person", ""), caches);

        assertNull(personne.getLieuNaissance());
    }


    // --- toPays / toLangue / toGenre / toLieuNaissance ---

    @Test
    @DisplayName("toPays : construit un Pays avec nom trim et url")
    void toPays_construitPaysAvecNomEtUrl() {
        DedupCaches caches = new DedupCaches();
        PaysJson dto = new PaysJson();
        dto.setNom(" France ");
        dto.setUrl("/search/title/?country_of_origin=FR");

        Pays pays = FilmMapper.toPays(dto, caches);

        assertEquals("France", pays.getNom());
        assertEquals("/search/title/?country_of_origin=FR", pays.getUrl());
    }

    @Test
    @DisplayName("toLangue : construit une Langue avec nom trim")
    void toLangue_construitLangueAvecNom() {
        DedupCaches caches = new DedupCaches();

        Langue langue = FilmMapper.toLangue(" English ", caches);

        assertEquals("English", langue.getNom());
    }

    @Test
    @DisplayName("toGenre : construit un Genre avec nom trim")
    void toGenre_construitGenreAvecNom() {
        DedupCaches caches = new DedupCaches();

        Genre genre = FilmMapper.toGenre(" Comedy ", caches);

        assertEquals("Comedy", genre.getNom());
    }

    @Test
    @DisplayName("toLieuNaissance : construit un LieuNaissance avec libelle trim")
    void toLieuNaissance_construitLieuNaissanceAvecLibelle() {
        DedupCaches caches = new DedupCaches();

        LieuNaissance lieu = FilmMapper.toLieuNaissance(" Montreal, Quebec, Canada ", caches);

        assertEquals("Montreal, Quebec, Canada", lieu.getLibelle());
    }


    // --- dedupe : le mécanisme de cache lui-même ---

    @Test
    @DisplayName("dedupe : deux clés équivalentes après normalisation renvoient la même instance sans reconstruire")
    void dedupe_clesEquivalentes_neReconstruitPas() {
        Map<String, Object> cache = new HashMap<>();
        int[] appels = {0};

        Function<String, Object> builder = trimmed -> {
            appels[0]++;
            return new Object();
        };

        Object premier = FilmMapper.dedupe(cache, "Montreal", builder);
        Object second = FilmMapper.dedupe(cache, "MONTREAL", builder);

        assertSame(premier, second);
        assertEquals(1, appels[0]);
    }


    // --- toRole / isPrincipal ---

    @Test
    @DisplayName("isPrincipal : acteur present dans castingPrincipal -> true")
    void isPrincipal_acteurPresent_renvoieTrue() {
        PersonneJson acteur = new PersonneJson();
        acteur.setId("nm0001");

        PersonneJson autre = new PersonneJson();
        autre.setId("nm0002");

        assertTrue(FilmMapper.isPrincipal(acteur, List.of(autre, acteur)));
    }

    @Test
    @DisplayName("isPrincipal : acteur absent de castingPrincipal -> false")
    void isPrincipal_acteurAbsent_renvoieFalse() {
        PersonneJson acteur = new PersonneJson();
        acteur.setId("nm0001");

        PersonneJson autre = new PersonneJson();
        autre.setId("nm0002");

        assertFalse(FilmMapper.isPrincipal(acteur, List.of(autre)));
    }

    @Test
    @DisplayName("toRole : construit un Role avec characterName, principal, film et personne dédoublonnée")
    void toRole_construitRoleComplet() {
        DedupCaches caches = new DedupCaches();
        Film film = new Film("tt0001");

        PersonneJson acteur = new PersonneJson();
        acteur.setId("nm0001");
        acteur.setIdentite("Craig Wasson");
        acteur.setNaissance(new NaissanceJson());

        RoleJson dto = new RoleJson();
        dto.setCharacterName("Don");
        dto.setActeur(acteur);

        Role role = FilmMapper.toRole(dto, film, true, caches);

        assertEquals("Don", role.getCharacterName());
        assertTrue(role.getPrincipal());
        assertSame(film, role.getFilm());
        assertEquals("nm0001", role.getPersonne().getId());
    }
}
