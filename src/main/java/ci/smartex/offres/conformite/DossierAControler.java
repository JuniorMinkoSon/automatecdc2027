package ci.smartex.offres.conformite;

import ci.smartex.offres.domain.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Tout ce qu'un controle a le droit de regarder, reuni une fois.
 *
 * <p>Les controles ne vont pas chercher eux-memes en base : ils recoivent cet
 * etat et rendent un verdict. Ils sont donc testables sans base de donnees,
 * ce qui est la condition pour que quelqu'un ecrive vraiment leurs tests.
 *
 * @param dateReference la date a laquelle la conformite est jugee, c'est-a-dire
 *                      la date limite de depot et non le jour du controle : une
 *                      attestation qui expire la veille du depot est deja
 *                      perimee, meme si elle est valable aujourd'hui.
 */
public record DossierAControler(
        Dossier dossier,
        AppelOffres appelOffres,
        Optional<Lot> lot,
        Entreprise entreprise,
        List<Exigence> exigences,
        List<LigneDqe> lignes,
        List<PieceAdmin> pieces,
        List<Personnel> personnel,
        List<Materiel> materiel,
        List<AffectationPersonnel> affectationsPersonnel,
        List<AffectationMateriel> affectationsMateriel,
        List<Dossier> autresLotsDuMemeAppel,
        /**
         * Les affectations de personnel des autres lots du meme appel. Sans
         * elles, la regle du DAO qui exige un personnel distinct entre lots ne
         * pourrait pas etre verifiee, puisqu'elle porte sur ce que les autres
         * dossiers engagent.
         */
        List<AffectationPersonnel> affectationsPersonnelAutresLots,
        List<Long> totauxDesAutresLots,
        LocalDate dateReference) {

    /** Le total du lot : la somme des lignes, jamais un chiffre saisi ailleurs. */
    public long totalDqe() {
        return lignes.stream()
                .filter(ligne -> !ligne.prixManquant())
                .mapToLong(LigneDqe::montantOuZero)
                .sum();
    }

    public boolean dqeComplet() {
        return !lignes.isEmpty() && lignes.stream().noneMatch(LigneDqe::prixManquant);
    }

    public List<Exigence> exigencesDeType(Exigence.Type type) {
        return exigences.stream().filter(e -> e.getType() == type).toList();
    }

    public Optional<PieceAdmin> pieceDeType(PieceAdmin.Type type) {
        return pieces.stream()
                .filter(piece -> piece.getType() == type)
                .filter(piece -> piece.valableLe(dateReference))
                .findFirst();
    }

    public Optional<Personnel> personnelParId(java.util.UUID id) {
        return personnel.stream().filter(p -> id.equals(p.getId())).findFirst();
    }

    public Optional<Materiel> materielParId(java.util.UUID id) {
        return materiel.stream().filter(m -> id.equals(m.getId())).findFirst();
    }
}
