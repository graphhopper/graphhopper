package com.graphhopper.expression;

import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EncodedValueFactory;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.EnumEncodedValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GSExpression {
    private final String name;
    private final List<Object> parameters;
    private final Object value;
    private final int lineNumber;

    public GSExpression(String name, List<Object> parameters, Object value, int lineNumber) {
        this.name = name;
        this.parameters = parameters;
        this.value = value;
        this.lineNumber = lineNumber;
    }

    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public List<Object> getParameters() {
        return parameters;
    }

    /**
     * This method makes running this expression possible and faster:
     * 1. it resolves the specified EncodedValues and 2. it converts string expressions into a simple object structure (TODO NOW AST)
     */
    public GSCompiledExpression compile(EncodedValueLookup lookup, EncodedValueFactory factory) {
        if (getParameters().isEmpty()) {
            return new GSCompiledUnconditionalExpression(name, value);
        }

        String valueAsStr = (String) getParameters().get(0);
        if (!lookup.hasEncodedValue(valueAsStr))
            throw new IllegalArgumentException("Cannot find parameter " + valueAsStr + " in storage, line " + getLineNumber());

        // TODO NOW currently it is required that the first parameter is an EncodedValue -> avoid this via detecting the type (string, number, EncodedValue)
        if (getName().equals("==")) {
            String parameterStr = (String) getParameters().get(1);
            Enum param = factory.find(valueAsStr, parameterStr);
            return new GSCompiledEQExpression(name, lookup.getEncodedValue(valueAsStr, EnumEncodedValue.class),
                    param, ((Number) value).doubleValue());
        } else if (getName().equals(">"))
            return new GSCompiledGTExpression(name, lookup.getEncodedValue(valueAsStr, DecimalEncodedValue.class),
                    ((Number) getParameters().get(1)).doubleValue(), ((Number) value).doubleValue());
        else if (getName().equals("<"))
            return new GSCompiledLTExpression(name, lookup.getEncodedValue(valueAsStr, DecimalEncodedValue.class),
                    ((Number) getParameters().get(1)).doubleValue(), ((Number) value).doubleValue());

        throw new GSParseException("Cannot compile script. Name not found " + getName(), "", lineNumber);
    }

    @Override
    public String toString() {
        StringBuilder paramList = new StringBuilder();
        for (Object param : parameters) {
            if (paramList.length() != 0)
                paramList.append(" ");
            paramList.append(param);
        }
        return name + " " + parameters + " ? " + value;
    }

    public static GSExpression parse(String fullLine, String expressionLine, int lineNumber) {
        String[] tokens = expressionLine.split(" ");
        if (tokens.length == 1) {
            String valueStr = tokens[0].trim();
            Object value;
            try {
                value = parseNumber(valueStr, "unused", "unused", -1);
            } catch (GSParseException ex) {
                value = parseString(valueStr, "unconditional value expected to be a number or a string but was " + valueStr,
                        fullLine, lineNumber);
            }
            return new GSExpression("unconditional", Collections.emptyList(), value, lineNumber);
        }

        if (tokens.length < 3)
            throw new GSParseException("expression with illegal number of tokens: " + tokens.length,
                    fullLine, lineNumber);

        for (int tokenIndex = 0; tokenIndex < tokens.length; tokenIndex++) {
            String str = tokens[tokenIndex];
            if (str.equals("?")) {
                if (tokenIndex + 1 >= tokens.length)
                    throw new GSParseException("expression found with no value after '?'", expressionLine, lineNumber);
                if (tokenIndex == 3) {
                    String name = tokens[1];
                    List<Object> parameters;
                    if (name.equals("==")) {
                        parameters = new ArrayList<>();
                        parameters.add(tokens[0]);
                        parameters.add(parseString(tokens[2], "Second parameter of condition " + name
                                + " expected to be a string but was " + tokens[2], expressionLine, lineNumber));
                    } else if (name.equals("<") || name.equals(">")) {
                        parameters = new ArrayList<>();
                        parameters.add(tokens[0]);
                        parameters.add(parseNumber(tokens[2], "Second parameter of condition " + name
                                + " expected to be a number but was " + tokens[2], expressionLine, lineNumber));
                    } else {
                        throw new GSParseException("no expression found with name " + name, expressionLine, lineNumber);
                    }
                    Number value = parseNumber(tokens[tokenIndex + 1], "Value was expected to be a number but was "
                            + tokens[tokenIndex + 1], expressionLine, lineNumber);
                    return new GSExpression(name, parameters, value, lineNumber);
                }

                throw new GSParseException("expression with illegal number of tokens: " + tokens.length,
                        expressionLine, lineNumber);
            }
        }
        throw new GSParseException("expression without '?' found", expressionLine, lineNumber);
    }

    static Number parseNumber(String token, String message, String expressionLine, int lineNumber) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            try {
                return Double.parseDouble(token);
            } catch (NumberFormatException ex2) {
                throw new GSParseException(message, expressionLine, lineNumber);
            }
        }
    }

    static String parseString(String token, String message, String expressionLine, int lineNumber) {
        if (token.startsWith("'") && token.endsWith("'"))
            return token.substring(1, token.length() - 1);

        throw new GSParseException(message, expressionLine, lineNumber);
    }
}
