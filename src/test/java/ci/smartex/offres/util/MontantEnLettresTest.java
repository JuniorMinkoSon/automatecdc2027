package ci.smartex.offres.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Montants en toutes lettres")
class MontantEnLettresTest {

    @Test
    @DisplayName("le total du lot 1 de NCHTP, qui est la reference du jalon")
    void totalLotUn() {
        assertThat(MontantEnLettres.montant(18_639_665L))
                .isEqualTo("Dix-huit millions six cent trente-neuf mille "
                        + "six cent soixante-cinq francs CFA");
    }

    @Test
    @DisplayName("un total TTC porte sa mention de regime fiscal")
    void totalAvecMention() {
        assertThat(MontantEnLettres.montant(724_520L, "toutes taxes comprises"))
                .isEqualTo("Sept cent vingt-quatre mille cinq cent vingt francs CFA "
                        + "toutes taxes comprises");
    }

    @Test
    @DisplayName("la garantie exigee par le DAO de la Bagoue")
    void garantieOffre() {
        assertThat(MontantEnLettres.montant(850_000L))
                .isEqualTo("Huit cent cinquante mille francs CFA");
    }

    @ParameterizedTest(name = "{0} donne {1}")
    @DisplayName("les pieges classiques de l'orthographe des nombres")
    @CsvSource(delimiter = '|', value = {
            // « et » sans trait d'union, et seulement jusqu'a soixante et onze.
            "21        | vingt et un",
            "31        | trente et un",
            "71        | soixante et onze",
            "81        | quatre-vingt-un",
            "91        | quatre-vingt-onze",
            // Les septante et nonante se comptent sur soixante et quatre-vingt.
            "70        | soixante-dix",
            "72        | soixante-douze",
            "79        | soixante-dix-neuf",
            "90        | quatre-vingt-dix",
            "99        | quatre-vingt-dix-neuf",
            // « quatre-vingts » perd son s devant mille, le garde a la fin.
            "80        | quatre-vingts",
            "80000     | quatre-vingt mille",
            "80000000  | quatre-vingts millions",
            // Idem pour « cents ».
            "100       | cent",
            "200       | deux cents",
            "201       | deux cent un",
            "200000    | deux cent mille",
            "200000000 | deux cents millions",
            // « mille » est invariable et ne prend pas d'article.
            "1000      | mille",
            "2000      | deux mille",
            "1000000   | un million",
            "2000000   | deux millions",
            // Les deux sous-totaux du DQE du lot 1.
            "16214800  | seize millions deux cent quatorze mille huit cents",
            "1096664   | un million quatre-vingt-seize mille six cent soixante-quatre",
            "0         | zéro",
    })
    void orthographeDesNombres(long valeur, String attendu) {
        assertThat(MontantEnLettres.nombre(valeur)).isEqualTo(attendu);
    }

    @Test
    @DisplayName("zero et un restent au singulier")
    void singulierDeLaDevise() {
        assertThat(MontantEnLettres.montant(0L)).isEqualTo("Zéro franc CFA");
        assertThat(MontantEnLettres.montant(1L)).isEqualTo("Un franc CFA");
        assertThat(MontantEnLettres.montant(2L)).isEqualTo("Deux francs CFA");
    }

    @Test
    @DisplayName("la date des lettres types, avec premier et non un")
    void dateEnLettres() {
        assertThat(MontantEnLettres.date(LocalDate.of(2026, 8, 14)))
                .isEqualTo("quatorze août deux mille vingt-six");
        assertThat(MontantEnLettres.date(LocalDate.of(2026, 1, 1)))
                .isEqualTo("premier janvier deux mille vingt-six");
        assertThat(MontantEnLettres.date(LocalDate.of(2026, 12, 31)))
                .isEqualTo("trente et un décembre deux mille vingt-six");
    }

    @Test
    @DisplayName("un montant et ses lettres ne peuvent pas diverger")
    void memeSourcePourLesDeuxEcritures() {
        // Le controle bloquant « montant en lettres » compare ces deux chaines ;
        // il ne peut echouer que si quelqu'un a ressaisi l'une des deux ailleurs.
        long total = 16_214_800L + 1_096_664L + 1_328_201L;
        assertThat(MontantEnLettres.montant(total))
                .isEqualTo(MontantEnLettres.montant(18_639_665L));
    }
}
