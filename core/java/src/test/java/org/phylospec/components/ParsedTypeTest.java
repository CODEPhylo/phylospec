package org.phylospec.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

public class ParsedTypeTest {
    @Test
    public void testGetUnqualifiedNameWithQualifiedNestedGenerics() {
        ParsedType parsedType = new ParsedType("Real");
        assertEquals(parsedType.getNamespace(), "");
        assertEquals(parsedType.getAtomicTypeName(), "Real");
        assertEquals(parsedType.stripGenerics(), "Real");
        assertEquals(parsedType.getTypeParameters(), List.of());
        assertEquals(parsedType.getTypeProperties(), List.of());

        parsedType = new ParsedType("phylospec.types.Real");
        assertEquals(parsedType.getNamespace(), "phylospec.types");
        assertEquals(parsedType.getAtomicTypeName(), "Real");
        assertEquals(parsedType.stripGenerics(), "phylospec.types.Real");
        assertEquals(parsedType.getTypeParameters(), List.of());
        assertEquals(parsedType.getTypeProperties(), List.of());

        parsedType = new ParsedType("Vector<Real>");
        assertEquals(parsedType.getNamespace(), "");
        assertEquals(parsedType.getAtomicTypeName(), "Vector");
        assertEquals(parsedType.stripGenerics(), "Vector");
        assertEquals(parsedType.getTypeParameters(), List.of(new ParsedType("Real")));
        assertEquals(parsedType.getTypeProperties(), List.of());

        parsedType = new ParsedType("Vector<Vector<Real>>");
        assertEquals(parsedType.getNamespace(), "");
        assertEquals(parsedType.getAtomicTypeName(), "Vector");
        assertEquals(parsedType.stripGenerics(), "Vector");
        assertEquals(parsedType.getTypeParameters(), List.of(new ParsedType("Vector<Real>")));
        assertEquals(parsedType.getTypeProperties(), List.of());
        assertEquals(parsedType.getTypeParameters().getFirst().getNamespace(), "");
        assertEquals(parsedType.getTypeParameters().getFirst().getAtomicTypeName(), "Vector");
        assertEquals(parsedType.getTypeParameters().getFirst().stripGenerics(), "Vector");
        assertEquals(
                parsedType.getTypeParameters().getFirst().getTypeParameters(),
                List.of(new ParsedType("Real")));
        assertEquals(parsedType.getTypeParameters().getFirst().getTypeProperties(), List.of());

        parsedType = new ParsedType("phylospec.types.Vector<phylospec.types.Real>");
        assertEquals(parsedType.getNamespace(), "phylospec.types");
        assertEquals(parsedType.getAtomicTypeName(), "Vector");
        assertEquals(parsedType.stripGenerics(), "phylospec.types.Vector");
        assertEquals(
                parsedType.getTypeParameters(), List.of(new ParsedType("phylospec.types.Real")));
        assertEquals(parsedType.getTypeProperties(), List.of());
        assertEquals(parsedType.getTypeParameters().getFirst().getNamespace(), "phylospec.types");
        assertEquals(parsedType.getTypeParameters().getFirst().getAtomicTypeName(), "Real");
        assertEquals(
                parsedType.getTypeParameters().getFirst().stripGenerics(), "phylospec.types.Real");
        assertEquals(parsedType.getTypeParameters().getFirst().getTypeParameters(), List.of());
        assertEquals(parsedType.getTypeParameters().getFirst().getTypeProperties(), List.of());

        parsedType = new ParsedType("Vector<Real; num=10>");
        assertEquals(parsedType.getNamespace(), "");
        assertEquals(parsedType.getAtomicTypeName(), "Vector");
        assertEquals(parsedType.stripGenerics(), "Vector");
        assertEquals(parsedType.getTypeParameters(), List.of(new ParsedType("Real")));
        assertEquals(
                parsedType.getTypeProperties(),
                List.of(new ParsedTypeProperty.Constant("num", "10")));

        parsedType = new ParsedType("Vector<;num=tree.numBranches, otherNum=2>");
        assertEquals(parsedType.getNamespace(), "");
        assertEquals(parsedType.getAtomicTypeName(), "Vector");
        assertEquals(parsedType.stripGenerics(), "Vector");
        assertEquals(parsedType.getTypeParameters(), List.of());
        assertEquals(
                parsedType.getTypeProperties(),
                List.of(
                        new ParsedTypeProperty.Assignment("num", "tree", "numBranches"),
                        new ParsedTypeProperty.Constant("otherNum", "2")));

        parsedType = new ParsedType("phylospec.types.Vector<;num=10, num=20>");
        assertEquals(parsedType.getNamespace(), "phylospec.types");
        assertEquals(parsedType.getAtomicTypeName(), "Vector");
        assertEquals(parsedType.stripGenerics(), "phylospec.types.Vector");
        assertEquals(parsedType.getTypeParameters(), List.of());
        assertEquals(
                parsedType.getTypeProperties(),
                List.of(
                        new ParsedTypeProperty.Constant("num", "10"),
                        new ParsedTypeProperty.Constant("num", "20")));

        parsedType =
                new ParsedType("phylospec.types.Vector<Vector<Real; num=tree.numBranches>;num=10>");
        assertEquals(parsedType.getNamespace(), "phylospec.types");
        assertEquals(parsedType.getAtomicTypeName(), "Vector");
        assertEquals(parsedType.stripGenerics(), "phylospec.types.Vector");
        assertEquals(
                parsedType.getTypeParameters(),
                List.of(new ParsedType("Vector<Real; num=tree.numBranches>")));
        assertEquals(
                parsedType.getTypeProperties(),
                List.of(new ParsedTypeProperty.Constant("num", "10")));
        assertEquals(parsedType.getTypeParameters().getFirst().getNamespace(), "");
        assertEquals(parsedType.getTypeParameters().getFirst().getAtomicTypeName(), "Vector");
        assertEquals(parsedType.getTypeParameters().getFirst().stripGenerics(), "Vector");
        assertEquals(
                parsedType.getTypeParameters().getFirst().getTypeParameters(),
                List.of(new ParsedType("Real")));
        assertEquals(
                parsedType.getTypeParameters().getFirst().getTypeProperties(),
                List.of(new ParsedTypeProperty.Assignment("num", "tree", "numBranches")));
    }
}
