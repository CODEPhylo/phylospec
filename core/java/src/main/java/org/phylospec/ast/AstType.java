package org.phylospec.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * Types are a type of nodes in the AST tree and correspond to types for variables.
 * This class has a number of subclasses for different types of types.
 * (Yes, I used the word "type" five times in two lines.)
 */
public abstract class AstType extends AstNode {

    @JsonPropertyDescription("The name of the type prefixed by its namespace.")
    public String name;

    private Set<String> resolvedTypeNames = Set.of();

    /**
     * Returns the fully qualified names of this resolved type and its aliases and ancestors.
     * This derived metadata is available to engine mappings and is not part of the serialized AST.
     */
    @JsonIgnore
    public Set<String> getResolvedTypeNames() {
        return resolvedTypeNames;
    }

    /** Records an immutable snapshot of the names found during type resolution. */
    public void setResolvedTypeNames(Set<String> resolvedTypeNames) {
        this.resolvedTypeNames = Set.copyOf(resolvedTypeNames);
    }

    public abstract <S, E, T> T accept(AstVisitor<S, E, T> visitor);

    /** Represents a non-generic type like `Real` */
    public static class Atomic extends AstType {
        public Atomic(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Atomic that = (Atomic) o;
            return Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }

        @Override
        public <S, E, T> T accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitAtomicType(this);
        }
    }

    /** Represents a generic type like `Real<T>`. */
    public static class Generic extends AstType {
        public Generic(String name, AstType... typeParameters) {
            this.name = name;
            this.typeParameters = typeParameters;
        }

        @JsonPropertyDescription("A list of the parameter types.")
        public final AstType[] typeParameters;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Generic generic = (Generic) o;
            return Objects.equals(name, generic.name) && Objects.deepEquals(typeParameters, generic.typeParameters);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, Arrays.hashCode(typeParameters));
        }

        @Override
        public <S, E, T> T accept(AstVisitor<S, E, T> visitor) {
            return visitor.visitGenericType(this);
        }
    }
}
