import dr.evolution.alignment.SimpleAlignment;
import dr.evolution.datatype.AminoAcids;
import dr.evolution.datatype.Codons;
import dr.evolution.datatype.Nucleotides;
import dr.evolution.sequence.Sequence;
import dr.evolution.util.Taxon;
import dr.evomodel.substmodel.FrequencyModel;
import dr.evomodel.substmodel.aminoacid.AminoAcidModelType;
import dr.evomodel.substmodel.aminoacid.EmpiricalAminoAcidModel;
import dr.evomodel.substmodel.codon.GY94CodonModel;
import dr.evomodel.substmodel.nucleotide.GTR;
import dr.inference.model.Parameter;
import dr.inference.model.VectorSliceParameter;
import org.junit.jupiter.api.Test;
import tiling.xml.XmlElement;
import tiling.xml.builders.AlignmentXmlBuilder;
import tiling.xml.builders.SubstitutionModelXmlBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXXmlComponentBuilderTest {

    @Test
    public void buildsJointGTRRelativeRatesXmlComponentLayer() {
        Parameter relativeRates =
                new Parameter.Default(new double[] {0.1, 0.2, 0.1, 0.2, 0.1, 0.3});
        relativeRates.setId("relativeRates");

        GTR model =
                new GTR(
                        slice(relativeRates, 0),
                        slice(relativeRates, 1),
                        slice(relativeRates, 2),
                        slice(relativeRates, 3),
                        slice(relativeRates, 4),
                        slice(relativeRates, 5),
                        new FrequencyModel(
                                Nucleotides.INSTANCE,
                                new double[] {0.25, 0.25, 0.25, 0.25}));

        String xml =
                toXml(new SubstitutionModelXmlBuilder()
                        .buildSubstitutionModel(model, "gtr"));

        assertTrue(xml.contains("<rates>"), xml);
        assertTrue(xml.contains("<parameter idref=\"relativeRates\""), xml);
        assertTrue(!xml.contains("<rateAC>"), xml);
    }

    @Test
    public void buildsWAGEmpiricalAminoAcidSubstitutionModelXmlComponentLayer() {
        assertEmpiricalAminoAcidSubstitutionModelXml(
                AminoAcidModelType.WAG,
                "WAG"
        );
    }

    @Test
    public void buildsLGEmpiricalAminoAcidSubstitutionModelXmlComponentLayer() {
        assertEmpiricalAminoAcidSubstitutionModelXml(
                AminoAcidModelType.LG,
                "LG"
        );
    }

    @Test
    public void buildsEmpiricalAminoAcidSubstitutionModelXmlComponentLayer() {
        assertEmpiricalAminoAcidSubstitutionModelXml(
                AminoAcidModelType.JTT,
                "JTT"
        );
    }

    @Test
    public void buildsGY94CodonSubstitutionModelXmlComponentLayer() {
        Parameter kappa =
                new Parameter.Default(2.0);

        kappa.setId("kappa");

        Parameter omega =
                new Parameter.Default(0.5);

        omega.setId("omega");

        double[] frequencies =
                new double[Codons.UNIVERSAL.getStateCount()];

        Arrays.fill(
                frequencies,
                1.0 / frequencies.length
        );

        FrequencyModel frequencyModel =
                new FrequencyModel(
                        Codons.UNIVERSAL,
                        frequencies
                );

        GY94CodonModel model =
                new GY94CodonModel(
                        Codons.UNIVERSAL,
                        kappa,
                        omega,
                        frequencyModel
                );

        List<XmlElement> elements =
                new SubstitutionModelXmlBuilder()
                        .buildSubstitutionModel(
                                model,
                                "codon_likelihood_substitutionModel"
                        );

        String xml =
                toXml(elements);

        assertTrue(xml.contains("<frequencyModel"), xml);
        assertTrue(xml.contains("dataType=\"codon-universal\""), xml);
        assertTrue(xml.contains("<yangCodonModel"), xml);
        assertTrue(xml.contains("<omega>"), xml);
        assertTrue(xml.contains("<parameter idref=\"omega\""), xml);
        assertTrue(xml.contains("<kappa>"), xml);
        assertTrue(xml.contains("<parameter idref=\"kappa\""), xml);
        assertTrue(xml.contains("<frequencyModel idref=\"codon_likelihood_substitutionModel_frequencies\""), xml);
    }

    @Test
    public void buildsCodonAlignmentAndPatternsXmlComponentLayer() {
        SimpleAlignment alignment =
                new SimpleAlignment();

        alignment.setDataType(Codons.UNIVERSAL);

        Sequence firstSequence =
                new Sequence(
                        new Taxon("taxon1"),
                        "ATGAAACCCGGG"
                );

        firstSequence.setDataType(Codons.UNIVERSAL);

        Sequence secondSequence =
                new Sequence(
                        new Taxon("taxon2"),
                        "ATGAAACCCGGA"
                );

        secondSequence.setDataType(Codons.UNIVERSAL);

        alignment.addSequence(firstSequence);
        alignment.addSequence(secondSequence);
        alignment.updateSiteCount();

        List<XmlElement> elements =
                new AlignmentXmlBuilder()
                        .buildAlignmentAndPatterns(
                                alignment,
                                "codon_likelihood_alignment",
                                "codon_likelihood_patterns"
                        );

        String xml =
                toXml(elements);

        assertTrue(xml.contains("<alignment"), xml);
        assertTrue(xml.contains("id=\"codon_likelihood_alignment\""), xml);
        assertTrue(xml.contains("dataType=\"codon-universal\""), xml);
        assertTrue(xml.contains("<sequence>"), xml);
        assertTrue(xml.contains("<taxon idref=\"taxon1\""), xml);
        assertTrue(xml.contains("ATGAAACCCGGG"), xml);
        assertTrue(xml.contains("<patterns"), xml);
        assertTrue(xml.contains("id=\"codon_likelihood_patterns\""), xml);
        assertTrue(xml.contains("<alignment idref=\"codon_likelihood_alignment\""), xml);
    }

    private void assertEmpiricalAminoAcidSubstitutionModelXml(
            AminoAcidModelType aminoAcidModelType,
            String expectedXmlType
    ) {
        EmpiricalAminoAcidModel model =
                new EmpiricalAminoAcidModel(
                        aminoAcidModelType.getRateMatrixInstance(),
                        new FrequencyModel(
                                AminoAcids.INSTANCE,
                                aminoAcidModelType.getRateMatrixInstance().getEmpiricalFrequencies()
                        )
                );

        List<XmlElement> elements =
                new SubstitutionModelXmlBuilder()
                        .buildSubstitutionModel(
                                model,
                                "protein_likelihood_substitutionModel"
                        );

        String xml =
                toXml(elements);

        assertTrue(xml.contains("<frequencyModel"), xml);
        assertTrue(xml.contains("dataType=\"amino acid\""), xml);
        assertTrue(xml.contains("<aminoAcidModel"), xml);
        assertTrue(xml.contains("type=\"" + expectedXmlType + "\""), xml);
        assertTrue(xml.contains("<frequencyModel idref=\"protein_likelihood_substitutionModel_frequencies\"/>"), xml);
    }

    private VectorSliceParameter slice(Parameter parameter, int index) {
        VectorSliceParameter slice =
                new VectorSliceParameter(null, index);
        slice.addParameter(parameter);
        return slice;
    }

    private static String toXml(List<XmlElement> elements) {
        return elements.stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n"));
    }
}
