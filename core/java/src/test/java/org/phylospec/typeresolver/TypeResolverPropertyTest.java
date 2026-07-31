package org.phylospec.typeresolver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;

public class TypeResolverPropertyTest {

    @Test
    public void testAssignmentCopiesNestedTypeProperties() throws IOException {
        TypeResolver resolver =
                resolve(
                        """
                        Distribution<Real> base = Exponential(rate=10)
                        Distribution<Vector<Real>> iidDist = IID(base=base, num=2)
                        """);

        ResolvedType distribution = onlyType(resolver.resolveVariable("iidDist"));
        ResolvedType vector = distribution.getParameterTypes().get("T");

        assertEquals(2, vector.getProperty("num"));
    }

    @Test
    public void testDrawCopiesNestedTypeProperties() throws IOException {
        TypeResolver resolver =
                resolve(
                        """
                        Distribution<Real> base = Normal(mean=0.0, sd=1.0)
                        Distribution<Vector<Real>> inner = IID(base=base, num=2)
                        Vector<Vector<Real>> samples ~ IID(base=inner, num=3)
                        """);

        ResolvedType outerVector = onlyType(resolver.resolveVariable("samples"));
        ResolvedType innerVector = outerVector.getParameterTypes().get("T");

        assertEquals(3, outerVector.getProperty("num"));
        assertEquals(2, innerVector.getProperty("num"));
    }

    private static TypeResolver resolve(String source) throws IOException {
        List<Stmt> statements = new Parser(new Lexer(source).scanTokens()).parse();
        TypeResolver resolver =
                new TypeResolver(
                        new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()));

        for (Stmt statement : statements) {
            statement.accept(resolver);
        }
        return resolver;
    }

    private static ResolvedType onlyType(Set<ResolvedType> resolvedTypes) {
        assertEquals(1, resolvedTypes.size());
        return resolvedTypes.iterator().next();
    }
}
