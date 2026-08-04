package org.phylospec.components;

import java.util.Objects;

/**
 * Represents an assignment to a property of a parsed type.
 */
public abstract class ParsedTypeProperty {
    private final String propertyName;

    private ParsedTypeProperty(String propertyName) {
        this.propertyName = propertyName;
    }

    /**
     * Represents a type property assigned a constant value.
     */
    public static final class Constant extends ParsedTypeProperty {
        private final String value;

        /**
         * Creates a constant type property assignment.
         *
         * @param propertyName the assigned type property
         * @param value the constant value
         */
        public Constant(String propertyName, String value) {
            super(propertyName);
            this.value = value;
        }

        /**
         * Returns the constant value.
         *
         * @return the constant value
         */
        public String getValue() {
            return value;
        }

        /**
         * Compares this constant assignment with another constant assignment.
         *
         * @param object the object to compare with
         * @return whether both assignments contain the same property name and value
         */
        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Constant constant)) {
                return false;
            }
            return getPropertyName().equals(constant.getPropertyName()) && this.value.equals(constant.value);
        }

        /**
         * Returns a hash code derived from the property name and value.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(getPropertyName(), this.value);
        }

        @Override
        public String toString() {
            return getPropertyName() + "=" + value;
        }
    }

    /**
     * Represents a type property assigned from a property of an input.
     */
    public static final class Assignment extends ParsedTypeProperty {
        private final String inputName;
        private final String inputPropertyName;

        /**
         * Creates an input property assignment.
         *
         * @param propertyName the assigned type property
         * @param inputName the input supplying the value
         * @param inputPropertyName the property read from the input
         */
        public Assignment(String propertyName, String inputName, String inputPropertyName) {
            super(propertyName);
            this.inputName = inputName;
            this.inputPropertyName = inputPropertyName;
        }

        /**
         * Returns the name of the input supplying the value.
         *
         * @return the input name
         */
        public String getInputName() {
            return inputName;
        }

        /**
         * Returns the name of the property read from the input.
         *
         * @return the input property name
         */
        public String getInputPropertyName() {
            return inputPropertyName;
        }

        /**
         * Compares this input assignment with another input assignment.
         *
         * @param object the object to compare with
         * @return whether both assignments refer to the same properties and input
         */
        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Assignment assignment)) {
                return false;
            }
            return getPropertyName().equals(assignment.getPropertyName())
                    && this.inputName.equals(assignment.inputName)
                    && this.inputPropertyName.equals(assignment.inputPropertyName);
        }

        /**
         * Returns a hash code derived from the assigned and referenced properties.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(getPropertyName(), this.inputName, this.inputPropertyName);
        }

        @Override
        public String toString() {
            return getPropertyName() + "=" + inputName + "." + inputPropertyName;
        }
    }

    /**
     * Returns the name of the assigned type property.
     *
     * @return the property name
     */
    public String getPropertyName() {
        return propertyName;
    }
}
