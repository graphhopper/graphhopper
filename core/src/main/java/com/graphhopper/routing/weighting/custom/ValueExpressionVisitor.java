/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.MinMax;
import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.util.CustomModel;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.*;

import java.io.StringReader;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the right-hand side value of limit_to, multiply_by or add and builds the "converted" Java from
 * the parsed value, not from the original text. So comments and unicode escapes never reach the
 * compiler and the checks below see the same text as the compiler.
 */
public class ValueExpressionVisitor {

    private static final String INFINITY = Double.toString(Double.POSITIVE_INFINITY);
    private static final Set<String> allowedMethodParents = Set.of("Math");
    // functions must be monotone in every argument (see findMinMax)
    private static final Set<String> allowedMethods = Set.of("sqrt", "min", "max");
    // built-in function of CustomWeightingHelper, see BikeSpeed. It gets the running speed 'value' of the
    // generated getSpeed as first argument, which the evaluator (findMinMax) replaces with NaN.
    static final String BIKE_SPEED_FACTOR = "bike_speed_factor";
    private static final String BIKE_SPEED_FACTOR_ARGS = "slope, power, mass, cda, crr, base_speed";
    private final ParseResult result;
    private final NameValidator variableValidator;

    public ValueExpressionVisitor(ParseResult result, NameValidator variableValidator) {
        this.result = result;
        this.variableValidator = variableValidator;
    }

    // allow only methods and other identifiers (constants and encoded values)
    boolean isValidIdentifier(String identifier) {
        if (variableValidator.isValid(identifier)) {
            if (!Character.isUpperCase(identifier.charAt(0)))
                result.guessedVariables.add(identifier);
            return true;
        }
        return false;
    }

    /**
     * Checks rv against the allowed list and returns the Java text for it. Throws for anything not allowed.
     */
    String emit(Java.Rvalue rv) throws Exception {
        if (rv instanceof Java.AmbiguousName n) {
            if (n.identifiers.length != 1)
                throw new IllegalArgumentException("identifier " + n + " invalid");
            String arg = n.identifiers[0];
            // e.g. like max_speed
            if (isValidIdentifier(arg)) return arg;
            throw new IllegalArgumentException("'" + arg + "' not available");
        } else if (rv instanceof Java.IntegerLiteral || rv instanceof Java.FloatingPointLiteral) {
            // a value is a number, so text, char, boolean and null literals are not allowed
            return ((Java.Literal) rv).value;
        } else if (rv instanceof Java.UnaryOperation uop) {
            result.operators.add(uop.operator);
            if (!uop.operator.equals("-"))
                throw new IllegalArgumentException("invalid operation '" + uop.operator + "'");
            String operand = emit(uop.operand);
            // "- -x" must not become the decrement "--x"
            return operand.startsWith("-") ? "- " + operand : "-" + operand;
        } else if (rv instanceof Java.MethodInvocation mi && mi.target == null && mi.methodName.equals(BIKE_SPEED_FACTOR)) {
            if (mi.arguments.length != BIKE_SPEED_FACTOR_ARGS.split(",").length)
                throw new IllegalArgumentException(BIKE_SPEED_FACTOR + " requires the arguments: " + BIKE_SPEED_FACTOR_ARGS);
            if (!(mi.arguments[0] instanceof Java.AmbiguousName))
                throw new IllegalArgumentException("the slope of " + BIKE_SPEED_FACTOR + " must be an encoded value like average_slope");
            // the table is built from the values, so they must be constant per custom model
            for (int i = 1; i < mi.arguments.length; i++)
                if (!(mi.arguments[i] instanceof Java.AmbiguousName n && n.identifiers.length == 1 && n.identifiers[0].startsWith(CustomModelParser.PARAM_PREFIX))
                        && !(mi.arguments[i] instanceof Java.IntegerLiteral || mi.arguments[i] instanceof Java.FloatingPointLiteral))
                    throw new IllegalArgumentException("argument " + (i + 1) + " of " + BIKE_SPEED_FACTOR + " must be a parameter or a positive number");
            result.bikeSpeedFactor = true;
            StringBuilder args = new StringBuilder("value");
            for (Java.Rvalue arg : mi.arguments) args.append(", ").append(emit(arg));
            return BIKE_SPEED_FACTOR + "(" + args + ")";
        } else if (rv instanceof Java.MethodInvocation mi) {
            // Math.sqrt(x), Math.min(x, 10) => the target is [Math, sqrt]. Skips methods like this.in() or chained calls.
            // TODO unlike in ConditionalExpressionVisitor we don't support a call like road_class.ordinal()
            //  as this is currently unsupported in FindMinMax
            if (!allowedMethods.contains(mi.methodName) || mi.target == null
                    || !(mi.target.toRvalue() instanceof Java.AmbiguousName n) || n.identifiers.length != 2
                    || !allowedMethodParents.contains(n.identifiers[0])
                    || mi.arguments.length < 1 || mi.arguments.length > 2)
                throw new IllegalArgumentException(mi.methodName + " is an illegal method in a value expression");
            String args = emit(mi.arguments[0]);
            if (mi.arguments.length == 2) args += ", " + emit(mi.arguments[1]);
            return n.identifiers[0] + "." + mi.methodName + "(" + args + ")";
        } else if (rv instanceof Java.ParenthesizedExpression pe) {
            return "(" + emit(pe.value) + ")";
        } else if (rv instanceof Java.BinaryOperation binOp) {
            String op = binOp.operator;
            result.operators.add(op);
            if (!op.equals("*") && !op.equals("+") && !op.equals("-"))
                throw new IllegalArgumentException("invalid operation '" + op + "'");
            return emit(binOp.lhs) + " " + op + " " + emit(binOp.rhs);
        }
        throw new IllegalArgumentException("invalid expression '" + rv + "'");
    }

    static ParseResult parse(String expression, NameValidator variableValidator) {
        ParseResult result = new ParseResult();
        result.guessedVariables = new LinkedHashSet<>();
        result.operators = new LinkedHashSet<>();
        try {
            // no file name => message starts with "Line 1, Column 7: ..."
            Parser parser = new Parser(new Scanner(null, new StringReader(expression)));
            Java.Atom atom = parser.parseConditionalExpression();
            if (parser.peek().type != TokenType.END_OF_INPUT)
                throw new IllegalArgumentException("invalid expression '" + expression + "'");
            result.converted = new ValueExpressionVisitor(result, variableValidator).emit(atom.toRvalue());
            result.ok = true;
        } catch (Exception ex) {
            // fail closed: no "converted" that could reach the compiler
            result.ok = false;
            result.converted = null;
            result.invalidMessage = ex.getMessage();
        }
        return result;
    }

    /**
     * @return the checked value with its variables (see findVariables) and the "converted" Java to compile.
     */
    static ParseResult parseValue(String valueExpression, Map<String, CustomModel.Parameter> parameters, EncodedValueLookup lookup) {
        ParseResult result = parseOrThrow(valueExpression, parameters, lookup);
        // from here on use only the text built from the parsed value
        valueExpression = result.converted;
        Set<String> encodedValues = new LinkedHashSet<>(result.guessedVariables);
        encodedValues.removeIf(v -> CustomModelParser.isParameter(v, parameters));
        if (encodedValues.size() > 1)
            throw new IllegalArgumentException("Currently only a single EncodedValue is allowed on the right-hand side, but was " + encodedValues.size() + ". Value expression: " + valueExpression);

        Set<String> usedParameters = new LinkedHashSet<>(result.guessedVariables);
        usedParameters.removeAll(encodedValues);
        // the built-in function is finite and positive for every valid combination, so checking the range endpoints is still sufficient
        if (usedParameters.size() > 1 && !result.bikeSpeedFactor)
            throw new IllegalArgumentException("Currently only a single parameter is allowed on the right-hand side, but was " + usedParameters.size() + ". Value expression: " + valueExpression);
        if (usedParameters.size() == 1 && !result.bikeSpeedFactor) {
            Matcher matcher = Pattern.compile("\\b" + usedParameters.iterator().next() + "\\b").matcher(valueExpression);
            matcher.find();
            if (matcher.find())
                throw new IllegalArgumentException("Parameter '" + usedParameters.iterator().next() + "' must not be used more than once. Value expression: " + valueExpression);
        }

        // TODO Nearly duplicate code as in findMinMax
        // the evaluator does not know the parameters, so replace them with their values
        String evalExpression = replaceParameters(valueExpression, parameters);
        if (result.bikeSpeedFactor) evalExpression = evalExpression.replace("(value, ", "(Double.NaN, ");
        double value;
        try {
            // Speed optimization for numbers only as its over 200x faster than ExpressionEvaluator+cook+evaluate!
            // We still call the parse() method before as it is only ~3x slower and might increase security slightly. Because certain
            // expressions are accepted from Double.parseDouble but parse() rejects them. With this call order we avoid unexpected security problems.
            value = Double.parseDouble(evalExpression);
        } catch (NumberFormatException ex) {
            evalExpression = Statement.toJavaExpression(evalExpression);
            try {
                if (encodedValues.isEmpty()) { // without encoded values
                    NoArgEvaluator ee = createEvaluator().createFastEvaluator(evalExpression, NoArgEvaluator.class);
                    value = ee.evaluate();
                } else if (lookup.hasEncodedValue(valueExpression)) { // speed up for common case that complete right-hand side is the encoded value
                    EncodedValue enc = lookup.getEncodedValue(valueExpression, EncodedValue.class);
                    value = Math.min(getMin(enc), getMax(enc));
                } else {
                    // single encoded value
                    String var = encodedValues.iterator().next();
                    SingleArgEvaluator ee = createEvaluator().createFastEvaluator(evalExpression, SingleArgEvaluator.class, var);
                    EncodedValue enc = lookup.getEncodedValue(var, EncodedValue.class);
                    double max = getMax(enc);
                    double val1 = ee.evaluate(max);
                    double min = getMin(enc);
                    double val2 = ee.evaluate(min);
                    value = Math.min(val1, val2);
                }
            } catch (CompileException ex2) {
                throw new IllegalArgumentException(ex2);
            }
        }
        if (value < 0)
            throw new IllegalArgumentException("illegal expression as it can result in a negative weight: " + valueExpression);

        return result;
    }

    /**
     * @return the encoded values and parameters of the value expression. Throws an exception if the
     * expression is invalid, contains more than one encoded value or can result in a negative value.
     */
    static Set<String> findVariables(String valueExpression, Map<String, CustomModel.Parameter> parameters, EncodedValueLookup lookup) {
        return parseValue(valueExpression, parameters, lookup).guessedVariables;
    }

    private static ParseResult parseOrThrow(String valueExpression, Map<String, CustomModel.Parameter> parameters, EncodedValueLookup lookup) {
        ParseResult result = parse(valueExpression, key -> lookup.hasEncodedValue(key) || key.contains(INFINITY) || CustomModelParser.isParameter(key, parameters));
        if (!result.ok)
            throw new IllegalArgumentException(result.invalidMessage);
        return result;
    }

    static MinMax findMinMax(String valueExpression, Map<String, CustomModel.Parameter> parameters, EncodedValueLookup lookup) {
        ParseResult result = parseOrThrow(valueExpression, parameters, lookup);
        // from here on use only the text built from the parsed value
        valueExpression = result.converted;
        Set<String> encodedValues = new LinkedHashSet<>(result.guessedVariables);
        encodedValues.removeIf(v -> CustomModelParser.isParameter(v, parameters));
        if (encodedValues.size() > 1)
            throw new IllegalArgumentException("Currently only a single EncodedValue is allowed on the right-hand side, but was " + encodedValues.size() + ". Value expression: " + valueExpression);

        // TODO Nearly duplicate as in findVariables
        // the evaluator does not know the parameters, so replace them with their values
        String evalExpression = replaceParameters(valueExpression, parameters);
        if (result.bikeSpeedFactor) evalExpression = evalExpression.replace("(value, ", "(Double.NaN, ");
        try {
            // Speed optimization for numbers only as its over 200x faster than ExpressionEvaluator+cook+evaluate!
            // We still call the parse() method before as it is only ~3x slower and might increase security slightly. Because certain
            // expressions are accepted from Double.parseDouble but parse() rejects them. With this call order we avoid unexpected security problems.
            double val = Double.parseDouble(evalExpression);
            return new MinMax(val, val);
        } catch (NumberFormatException ex) {
        }

        evalExpression = Statement.toJavaExpression(evalExpression);
        try {
            if (encodedValues.isEmpty()) { // without encoded values
                NoArgEvaluator ee = createEvaluator().createFastEvaluator(evalExpression, NoArgEvaluator.class);
                double val = ee.evaluate();
                return new MinMax(val, val);
            }

            if (lookup.hasEncodedValue(valueExpression)) { // speed up for common case that complete right-hand side is the encoded value
                EncodedValue enc = lookup.getEncodedValue(valueExpression, EncodedValue.class);
                double min = getMin(enc), max = getMax(enc);
                return new MinMax(min, max);
            }

            String var = encodedValues.iterator().next();
            SingleArgEvaluator ee = createEvaluator().createFastEvaluator(evalExpression, SingleArgEvaluator.class, var);
            EncodedValue enc = lookup.getEncodedValue(var, EncodedValue.class);
            double max = getMax(enc);
            double val1 = ee.evaluate(max);
            double min = getMin(enc);
            double val2 = ee.evaluate(min);
            return new MinMax(Math.min(val1, val2), Math.max(val1, val2));
        } catch (CompileException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    static boolean containsEncodedValue(String valueExpression, Map<String, CustomModel.Parameter> parameters, EncodedValueLookup lookup) {
        ParseResult result = parse(valueExpression, key -> lookup.hasEncodedValue(key) || key.contains(INFINITY) || CustomModelParser.isParameter(key, parameters));
        return !result.ok || result.guessedVariables.stream().anyMatch(lookup::hasEncodedValue);
    }

    /**
     * @return the expression with the parameters replaced by their values for the ExpressionEvaluator,
     * e.g. "0.9 * p_hill_factor" -> "0.9 * (0.5)". A lone parameter becomes the bare literal for the
     * Double.parseDouble fast path.
     */
    private static String replaceParameters(String expression, Map<String, CustomModel.Parameter> parameters) {
        for (Map.Entry<String, CustomModel.Parameter> entry : parameters.entrySet()) {
            String name = CustomModelParser.PARAM_PREFIX + entry.getKey();
            if (expression.trim().equals(name)) return entry.getValue().toString();
            // parenthesized canonical literal, as e.g. "speed--2" for a negative value would not compile
            String literal = entry.getValue().value() instanceof Number number ? "(" + number.doubleValue() + ")" : entry.getValue().value().toString();
            expression = expression.replaceAll("\\b" + name + "\\b", literal);
        }
        return expression;
    }

    private static ExpressionEvaluator createEvaluator() {
        ExpressionEvaluator ee = new ExpressionEvaluator();
        // makes the built-in function bike_speed_factor available
        ee.setExtendedClass(CustomWeightingHelper.class);
        return ee;
    }

    static double getMin(EncodedValue enc) {
        if (enc instanceof DecimalEncodedValue)
            return ((DecimalEncodedValue) enc).getMinStorableDecimal();
        else if (enc instanceof IntEncodedValue) return ((IntEncodedValue) enc).getMinStorableInt();
        throw new IllegalArgumentException("Cannot use non-number data '" + enc.getName() + "' in value expression");
    }

    static double getMax(EncodedValue enc) {
        if (enc instanceof DecimalEncodedValue)
            return ((DecimalEncodedValue) enc).getMaxOrMaxStorableDecimal();
        else if (enc instanceof IntEncodedValue)
            return ((IntEncodedValue) enc).getMaxOrMaxStorableInt();
        throw new IllegalArgumentException("Cannot use non-number data '" + enc.getName() + "' in value expression");
    }

    protected interface NoArgEvaluator {
        double evaluate();
    }

    protected interface SingleArgEvaluator {
        double evaluate(double arg);
    }
}
