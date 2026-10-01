package com.bayzyl.generation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExpressionEngine {
    private final List<Token> tokens;
    private int index;

    private ExpressionEngine(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static Program compile(String expression) {
        List<Token> tokens = tokenize(expression);
        ExpressionEngine parser = new ExpressionEngine(tokens);
        List<Node> statements = new ArrayList<>();
        while (!parser.peek(TokenType.EOF)) {
            statements.add(parser.parseStatement());
            if (parser.peek(TokenType.SEMICOLON)) {
                parser.consume(TokenType.SEMICOLON);
            } else if (!parser.peek(TokenType.EOF)) {
                throw parser.error("Expected ';' or end of expression.");
            }
        }
        return new Program(statements);
    }

    private Node parseStatement() {
        if (peek(TokenType.IDENTIFIER) && peekNext(TokenType.ASSIGN)) {
            String name = consume(TokenType.IDENTIFIER).text;
            consume(TokenType.ASSIGN);
            return new AssignmentNode(name, parseExpression());
        }
        return parseExpression();
    }

    private Node parseExpression() {
        return parseComparison();
    }

    private Node parseComparison() {
        Node left = parseAdditive();
        while (peek(TokenType.LESS, TokenType.LESS_EQUAL, TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.EQUAL_EQUAL, TokenType.NOT_EQUAL)) {
            Token operator = advance();
            Node right = parseAdditive();
            left = new BinaryNode(left, operator.type, right);
        }
        return left;
    }

    private Node parseAdditive() {
        Node left = parseMultiplicative();
        while (peek(TokenType.PLUS, TokenType.MINUS)) {
            Token operator = advance();
            Node right = parseMultiplicative();
            left = new BinaryNode(left, operator.type, right);
        }
        return left;
    }

    private Node parseMultiplicative() {
        Node left = parsePower();
        while (peek(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            Token operator = advance();
            Node right = parsePower();
            left = new BinaryNode(left, operator.type, right);
        }
        return left;
    }

    private Node parsePower() {
        Node left = parseUnary();
        if (peek(TokenType.CARET)) {
            Token operator = advance();
            Node right = parsePower();
            return new BinaryNode(left, operator.type, right);
        }
        return left;
    }

    private Node parseUnary() {
        if (peek(TokenType.PLUS, TokenType.MINUS)) {
            Token operator = advance();
            return new UnaryNode(operator.type, parseUnary());
        }
        return parsePrimary();
    }

    private Node parsePrimary() {
        if (peek(TokenType.NUMBER)) {
            return new NumberNode(Double.parseDouble(advance().text));
        }
        if (peek(TokenType.IDENTIFIER)) {
            Token identifier = advance();
            if (peek(TokenType.LEFT_PAREN)) {
                consume(TokenType.LEFT_PAREN);
                List<Node> args = new ArrayList<>();
                if (!peek(TokenType.RIGHT_PAREN)) {
                    do {
                        args.add(parseExpression());
                    } while (match(TokenType.COMMA));
                }
                consume(TokenType.RIGHT_PAREN);
                return new FunctionNode(identifier.text, args);
            }
            return new VariableNode(identifier.text);
        }
        if (match(TokenType.LEFT_PAREN)) {
            Node node = parseExpression();
            consume(TokenType.RIGHT_PAREN);
            return node;
        }
        throw error("Unexpected token: " + current().text);
    }

    private boolean match(TokenType type) {
        if (!peek(type)) {
            return false;
        }
        index++;
        return true;
    }

    private boolean peek(TokenType... types) {
        for (TokenType type : types) {
            if (current().type == type) {
                return true;
            }
        }
        return false;
    }

    private boolean peekNext(TokenType type) {
        int next = index + 1;
        return next < tokens.size() && tokens.get(next).type == type;
    }

    private Token consume(TokenType type) {
        if (!peek(type)) {
            throw error("Expected " + type + " but found " + current().text);
        }
        return advance();
    }

    private Token advance() {
        return tokens.get(index++);
    }

    private Token current() {
        return tokens.get(index);
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message);
    }

    private static List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (Character.isDigit(c) || c == '.') {
                int start = i++;
                while (i < input.length() && (Character.isDigit(input.charAt(i)) || input.charAt(i) == '.')) {
                    i++;
                }
                tokens.add(new Token(TokenType.NUMBER, input.substring(start, i)));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i++;
                while (i < input.length() && (Character.isLetterOrDigit(input.charAt(i)) || input.charAt(i) == '_')) {
                    i++;
                }
                tokens.add(new Token(TokenType.IDENTIFIER, input.substring(start, i).toLowerCase(Locale.ROOT)));
                continue;
            }
            switch (c) {
                case '+' -> tokens.add(new Token(TokenType.PLUS, "+"));
                case '-' -> tokens.add(new Token(TokenType.MINUS, "-"));
                case '*' -> tokens.add(new Token(TokenType.STAR, "*"));
                case '/' -> tokens.add(new Token(TokenType.SLASH, "/"));
                case '%' -> tokens.add(new Token(TokenType.PERCENT, "%"));
                case '^' -> tokens.add(new Token(TokenType.CARET, "^"));
                case '(' -> tokens.add(new Token(TokenType.LEFT_PAREN, "("));
                case ')' -> tokens.add(new Token(TokenType.RIGHT_PAREN, ")"));
                case ',' -> tokens.add(new Token(TokenType.COMMA, ","));
                case ';' -> tokens.add(new Token(TokenType.SEMICOLON, ";"));
                case '=' -> {
                    if (i + 1 < input.length() && input.charAt(i + 1) == '=') {
                        tokens.add(new Token(TokenType.EQUAL_EQUAL, "=="));
                        i++;
                    } else {
                        tokens.add(new Token(TokenType.ASSIGN, "="));
                    }
                }
                case '!' -> {
                    if (i + 1 < input.length() && input.charAt(i + 1) == '=') {
                        tokens.add(new Token(TokenType.NOT_EQUAL, "!="));
                        i++;
                    } else {
                        throw new IllegalArgumentException("Unexpected '!'");
                    }
                }
                case '<' -> {
                    if (i + 1 < input.length() && input.charAt(i + 1) == '=') {
                        tokens.add(new Token(TokenType.LESS_EQUAL, "<="));
                        i++;
                    } else {
                        tokens.add(new Token(TokenType.LESS, "<"));
                    }
                }
                case '>' -> {
                    if (i + 1 < input.length() && input.charAt(i + 1) == '=') {
                        tokens.add(new Token(TokenType.GREATER_EQUAL, ">="));
                        i++;
                    } else {
                        tokens.add(new Token(TokenType.GREATER, ">"));
                    }
                }
                default -> throw new IllegalArgumentException("Unexpected character: " + c);
            }
            i++;
        }
        tokens.add(new Token(TokenType.EOF, ""));
        return tokens;
    }

    public record Program(List<Node> statements) {
        public double evaluate(Map<String, Double> variables) {
            Map<String, Double> env = new HashMap<>();
            env.put("pi", Math.PI);
            env.put("e", Math.E);
            env.putAll(variables);
            double result = 0.0;
            for (Node statement : statements) {
                result = statement.evaluate(env);
            }
            return result;
        }
    }

    private interface Node {
        double evaluate(Map<String, Double> env);
    }

    private record NumberNode(double value) implements Node {
        @Override
        public double evaluate(Map<String, Double> env) {
            return value;
        }
    }

    private record VariableNode(String name) implements Node {
        @Override
        public double evaluate(Map<String, Double> env) {
            Double value = env.get(name);
            if (value == null) {
                throw new IllegalArgumentException("Unknown variable: " + name);
            }
            return value;
        }
    }

    private record AssignmentNode(String name, Node expression) implements Node {
        @Override
        public double evaluate(Map<String, Double> env) {
            double value = expression.evaluate(env);
            env.put(name, value);
            return value;
        }
    }

    private record UnaryNode(TokenType operator, Node node) implements Node {
        @Override
        public double evaluate(Map<String, Double> env) {
            double value = node.evaluate(env);
            return operator == TokenType.MINUS ? -value : value;
        }
    }

    private record BinaryNode(Node left, TokenType operator, Node right) implements Node {
        @Override
        public double evaluate(Map<String, Double> env) {
            double a = left.evaluate(env);
            double b = right.evaluate(env);
            return switch (operator) {
                case PLUS -> a + b;
                case MINUS -> a - b;
                case STAR -> a * b;
                case SLASH -> a / b;
                case PERCENT -> a % b;
                case CARET -> Math.pow(a, b);
                case LESS -> a < b ? 1.0 : 0.0;
                case LESS_EQUAL -> a <= b ? 1.0 : 0.0;
                case GREATER -> a > b ? 1.0 : 0.0;
                case GREATER_EQUAL -> a >= b ? 1.0 : 0.0;
                case EQUAL_EQUAL -> a == b ? 1.0 : 0.0;
                case NOT_EQUAL -> a != b ? 1.0 : 0.0;
                default -> throw new IllegalStateException("Unexpected operator: " + operator);
            };
        }
    }

    private record FunctionNode(String name, List<Node> args) implements Node {
        @Override
        public double evaluate(Map<String, Double> env) {
            List<Double> values = new ArrayList<>(args.size());
            for (Node arg : args) {
                values.add(arg.evaluate(env));
            }
            return switch (name) {
                case "sqrt" -> unary(values, Math::sqrt);
                case "sin" -> unary(values, Math::sin);
                case "cos" -> unary(values, Math::cos);
                case "tan" -> unary(values, Math::tan);
                case "asin" -> unary(values, Math::asin);
                case "acos" -> unary(values, Math::acos);
                case "atan" -> unary(values, Math::atan);
                case "abs" -> unary(values, Math::abs);
                case "floor" -> unary(values, Math::floor);
                case "ceil" -> unary(values, Math::ceil);
                case "round" -> unary(values, v -> (double) Math.round(v));
                case "exp" -> unary(values, Math::exp);
                case "log" -> unary(values, Math::log);
                case "min" -> binary(values, Math::min);
                case "max" -> binary(values, Math::max);
                case "pow" -> binary(values, Math::pow);
                case "atan2" -> binary(values, Math::atan2);
                default -> throw new IllegalArgumentException("Unknown function: " + name);
            };
        }

        private double unary(List<Double> values, java.util.function.DoubleUnaryOperator operator) {
            if (values.size() != 1) {
                throw new IllegalArgumentException(name + "() expects 1 argument.");
            }
            return operator.applyAsDouble(values.get(0));
        }

        private double binary(List<Double> values, java.util.function.DoubleBinaryOperator operator) {
            if (values.size() != 2) {
                throw new IllegalArgumentException(name + "() expects 2 arguments.");
            }
            return operator.applyAsDouble(values.get(0), values.get(1));
        }
    }

    private record Token(TokenType type, String text) {
    }

    private enum TokenType {
        NUMBER,
        IDENTIFIER,
        PLUS,
        MINUS,
        STAR,
        SLASH,
        PERCENT,
        CARET,
        LEFT_PAREN,
        RIGHT_PAREN,
        COMMA,
        SEMICOLON,
        ASSIGN,
        LESS,
        LESS_EQUAL,
        GREATER,
        GREATER_EQUAL,
        EQUAL_EQUAL,
        NOT_EQUAL,
        EOF
    }
}
