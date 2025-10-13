package org.phylospec.ast;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public abstract class Type {

    public static class Atomic extends Type {
		public Atomic(String name) {
			this.name = name;
		}

		final String name;

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
    }

    public static class Generic extends Type {
        public Generic(String name, Type... typeParameters) {
            this.name = name;
            this.typeParameters = typeParameters;
        }

        final String name;
        final Type[] typeParameters;

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
    }


}
