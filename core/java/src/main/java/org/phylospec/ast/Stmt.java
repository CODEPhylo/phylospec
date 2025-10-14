package org.phylospec.ast;

import org.phylospec.lexer.Token;

import java.util.Objects;

public abstract class Stmt {

    public static class Assignment extends Stmt {
        public Assignment(Type type, String name, Expr expression) {
            this.type = type;
            this.name = name;
            this.expression = expression;
        }

        final Type type;
        final String name;
        final Expr expression;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Assignment that = (Assignment) o;
            return Objects.equals(type, that.type) && Objects.equals(name, that.name) && Objects.equals(expression, that.expression);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, name, expression);
        }
    }

    public static class Draw extends Stmt {
        public Draw(Type type, String name, Expr expression) {
            this.type = type;
            this.name = name;
            this.expression = expression;
        }

        final Type type;
        final String name;
        final Expr expression;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Draw that = (Draw) o;
            return Objects.equals(type, that.type) && Objects.equals(name, that.name) && Objects.equals(expression, that.expression);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, name, expression);
        }
    }

    public static class Decorated extends Stmt {
        public Decorated(Expr.Call decorator, Stmt statememt) {
            this.decorator = decorator;
            this.statememt = statememt;
        }

        final Expr.Call decorator;
        final Stmt statememt;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Decorated decorated = (Decorated) o;
            return Objects.equals(decorator, decorated.decorator) && Objects.equals(statememt, decorated.statememt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(decorator, statememt);
        }
    }

}
