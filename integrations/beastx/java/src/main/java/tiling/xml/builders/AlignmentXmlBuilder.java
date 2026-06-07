package tiling.xml.builders;

import dr.evolution.alignment.Alignment;
import dr.evolution.datatype.Codons;
import dr.evolution.datatype.DataType;
import dr.evolution.sequence.Sequence;
import dr.evolution.util.Taxon;
import tiling.xml.XmlElement;

import java.util.ArrayList;
import java.util.List;

public class AlignmentXmlBuilder {

    public List<XmlElement> buildAlignmentAndPatterns(
            Alignment alignment,
            String alignmentId,
            String patternsId
    ) {
        List<XmlElement> elements =
                new ArrayList<>();

        elements.add(alignmentDefinition(alignment, alignmentId));
        elements.add(patternsDefinition(alignmentId, patternsId));

        return elements;
    }

    public XmlElement alignmentDefinition(
            Alignment alignment,
            String alignmentId
    ) {
        XmlElement element =
                XmlElement.element("alignment")
                        .withId(alignmentId)
                        .withAttribute("dataType", dataTypeName(alignment.getDataType()));

        for (int i = 0; i < alignment.getSequenceCount(); i++) {
            Sequence sequence =
                    alignment.getSequence(i);

            element =
                    element.withChild(sequenceDefinition(sequence));
        }

        return element;
    }

    public XmlElement patternsDefinition(
            String alignmentId,
            String patternsId
    ) {
        return XmlElement.element("patterns")
                .withId(patternsId)
                .withAttribute("from", 1)
                .withAttribute("strip", "false")
                .withChild(XmlElement.ref("alignment", alignmentId));
    }

    private XmlElement sequenceDefinition(Sequence sequence) {
        Taxon taxon =
                sequence.getTaxon();

        if (taxon == null || taxon.getId() == null || taxon.getId().isBlank()) {
            throw new IllegalArgumentException(
                    "Cannot serialize BEAST X alignment sequence without a taxon id."
            );
        }

        String sequenceString =
                sequence.getSequenceString();

        if (sequenceString == null || sequenceString.isBlank()) {
            throw new IllegalArgumentException(
                    "Cannot serialize empty BEAST X alignment sequence for taxon '" + taxon.getId() + "'."
            );
        }

        return XmlElement.element("sequence")
                .withChild(XmlElement.ref("taxon", taxon.getId()))
                .withText(sequenceString);
    }

    private String dataTypeName(DataType dataType) {
        if (dataType == null) {
            throw new IllegalArgumentException(
                    "Cannot serialize BEAST X alignment without a data type."
            );
        }

        String description =
                dataType.getDescription();

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "Cannot serialize BEAST X alignment with unnamed data type."
            );
        }

        return switch (description.toLowerCase()) {
            case "nucleotide", "nucleotides", "dna" -> "nucleotide";
            case "amino acid", "amino acids", "aminoacid", "aminoacids", "protein" -> "amino acid";
            case "codon", "codons", "universal codons", "codon-universal" -> "codon-universal";
            case "two states", "binary", "boolean" -> "binary";
            default -> {
                if (dataType instanceof Codons) {
                    yield "codon-universal";
                }

                yield description;
            }
        };
    }
}