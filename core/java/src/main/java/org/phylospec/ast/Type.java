package org.phylospec.ast;

import java.util.Arrays;
import java.util.Objects;

/**
 * Types are a type of nodes in the AST tree and correspond to types for variables.
 * This class has a number of subclasses for different types of types.
 * (Yes, I used the word "type" five times in two lines.)
 */
public abstract class Type {

    abstract public <S, E, T> T accept(AstVisitor<S, E, T> visitor);

    /** Represents a non-generic type like `Real` */
    public static class Atomic extends Type {
		public Atomic(String name) {
			this.name = name;
		}

		public final String name;

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
    public static class Generic extends Type {
        public Generic(String name, Type... typeParameters) {
            this.name = name;
            this.typeParameters = typeParameters;
        }

        public final String name;
        public final Type[] typeParameters;

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
