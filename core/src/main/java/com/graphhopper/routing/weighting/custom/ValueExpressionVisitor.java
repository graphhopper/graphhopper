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
 * Expression visitor for right-hand side value of limit_to or multiply_by.
 */
public class ValueExpressionVisitor implements Visitor.AtomVisitor<Boolean, Exception> {

    private static final String INFINITY = Double.toString(Double.POSITIVE_INFINITY);
    private static final Set<String> allowedMethodParents = Set.of("Math");
    // functions must be monotone in every argument (see findMinMax)
    private static final Set<String> allowedMethods = Set.of("sqrt", "min", "max");
    // the built-in function, also a static method in CustomWeightingHelper for the ExpressionEvaluator
    static final String BIKE_CLIMB_FACTOR = "bike_climb_factor";
    // example arguments of bike_climb_factor (only used for error messages): the slope encoded value and the parameters
    static final String[] BIKE_CLIMB_ARGS = {"average_slope", "p_power", "p_mass", "p_cda", "p_crr"};
    private final ParseResult result;
    private final NameValidator variableValidator;
    private String invalidMessage;

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

    @Override
    public Boolean visitRvalue(Java.Rvalue rv) throws Exception {
        if (rv instanceof Java.AmbiguousName n) {
            if (n.identifiers.length == 1) {
                String arg = n.identifiers[0];
                // e.g. like road_class
                if (isValidIdentifier(arg)) return true;
                invalidMessage = "'" + arg + "' not available";
                return false;
            }
            invalidMessage = "identifier " + n + " invalid";
            return false;
        }
        if (rv instanceof Java.Literal) {
            return true;
        } else if (rv instanceof Java.UnaryOperation uop) {
            result.operators.add(uop.operator);
            if (uop.operator.equals("-"))
                return uop.operand.accept(this);
            return false;
        } else if (rv instanceof Java.MethodInvocation mi) {
            if (mi.target == null && mi.methodName.equals(BIKE_CLIMB_FACTOR)) {
                if (mi.arguments.length != BIKE_CLIMB_ARGS.length) {
                    invalidMessage = BIKE_CLIMB_FACTOR + " expects " + BIKE_CLIMB_ARGS.length + " arguments like " + String.join(", ", BIKE_CLIMB_ARGS) + ", but got: " + mi.arguments.length;
                    return false;
                }
                // the arguments must be the slope encoded value and numbers or parameters (verified in
                // checkBikeClimbArgs) as the BikeClimbSpeedTable is created once per instance from their values
                String[] args = new String[mi.arguments.length];
                for (int i = 0; i < args.length; i++) {
                    String expects = BIKE_CLIMB_FACTOR + " expects an argument like " + BIKE_CLIMB_ARGS[i] + " as argument " + (i + 1) + ", but ";
                    if (i > 0 && (mi.arguments[i] instanceof Java.IntegerLiteral || mi.arguments[i] instanceof Java.FloatingPointLiteral)) {
                        args[i] = ((Java.Literal) mi.arguments[i]).value;
                        continue;
                    }
                    if (!(mi.arguments[i] instanceof Java.AmbiguousName n) || n.identifiers.length != 1) {
                        invalidMessage = expects + "got: " + mi.arguments[i];
                        return false;
                    }
                    if (!isValidIdentifier(n.identifiers[0])) {
                        invalidMessage = expects + "'" + n.identifiers[0] + "' is not available";
                        return false;
                    }
                    args[i] = n.identifiers[0];
                }
                result.bikeClimbArgs = args;
                return true;
            }
            if (allowedMethods.contains(mi.methodName)) {
                // skip methods like this.in()
                if (mi.target != null) {
                    // edge.getDistance(), Math.sqrt(2) => check target name (edge or Math)
                    Java.AmbiguousName n = (Java.AmbiguousName) mi.target.toRvalue();
                    if (n.identifiers.length == 2) {
                        if (allowedMethodParents.contains(n.identifiers[0])) {
                            // edge.getDistance(), Math.sqrt(x) => check target name i.e. edge or Math
                            if (mi.arguments.length == 0) {
                                result.guessedVariables.add(n.identifiers[0]); // return "edge"
                                return true;
                            } else if (mi.arguments.length == 1) {
                                // return "x" but verify before
                                return mi.arguments[0].accept(this);
                            } else if (mi.arguments.length == 2) {
                                // Math.min(x, 10) or Math.max(0.5, x)
                                return mi.arguments[0].accept(this) && mi.arguments[1].accept(this);
                            }
                        }
                        // TODO unlike in ConditionalExpressionVisitor we don't support a call like road_class.ordinal()
                        //  as this is currently unsupported in FindMinMax
                    }
                }
            }
            invalidMessage = mi.methodName + " is an illegal method in a value expression";
            return false;
        } else if (rv instanceof Java.ParenthesizedExpression) {
            return ((Java.ParenthesizedExpression) rv).value.accept(this);
        } else if (rv instanceof Java.BinaryOperation binOp) {
            String op = binOp.operator;
            result.operators.add(op);
            if (op.equals("*") || op.equals("+") || binOp.operator.equals("-")) {
                return binOp.lhs.accept(this) && binOp.rhs.accept(this);
            }
            invalidMessage = "invalid operation '" + op + "'";
            return false;
        }
        return false;
    }

    @Override
    public Boolean visitPackage(Java.Package p) {
        return false;
    }

    @Override
    public Boolean visitType(Java.Type t) {
        return false;
    }

    @Override
    public Boolean visitConstructorInvocation(Java.ConstructorInvocation ci) {
        return false;
    }

    static ParseResult parse(String expression, NameValidator variableValidator) {
        ParseResult result = new ParseResult();
        try {
            Parser parser = new Parser(new Scanner("ignore", new StringReader(expression)));
            Java.Atom atom = parser.parseConditionalExpression();
            if (parser.peek().type == TokenType.END_OF_INPUT) {
                result.guessedVariables = new LinkedHashSet<>();
                result.operators = new LinkedHashSet<>();
                ValueExpressionVisitor visitor = new ValueExpressionVisitor(result, variableValidator);
                result.ok = atom.accept(visitor);
                result.invalidMessage = visitor.invalidMessage;
                // combined with other terms the expression could be non-monotone in average_slope, see findMinMax
                if (result.ok && result.bikeClimbArgs != null && !isBikeClimbCall(atom)) {
                    result.ok = false;
                    result.invalidMessage = BIKE_CLIMB_FACTOR + " must be the entire expression";
                }
            }
        } catch (Exception ex) {
        }
        return result;
    }

    private static boolean isBikeClimbCall(Java.Atom atom) {
        return atom instanceof Java.MethodInvocation mi && mi.methodName.equals(BIKE_CLIMB_FACTOR);
    }

    private static void checkBikeClimbArgs(ParseResult result, Map<String, CustomModel.Parameter> parameters, EncodedValueLookup lookup) {
        if (result.bikeClimbArgs == null) return;
        String[] args = result.bikeClimbArgs;
        if (!lookup.hasEncodedValue(args[0]))
            throw new IllegalArgumentException(BIKE_CLIMB_FACTOR + " expects the slope encoded value as first argument but '" + args[0] + "' is not an encoded value");
        for (int i = 1; i < args.length; i++)
            if (!CustomModelParser.isParameter(args[i], parameters) && !isNumber(args[i]))
                throw new IllegalArgumentException(BIKE_CLIMB_FACTOR + " expects numbers or parameters as arguments but '" + args[i] + "' is not defined in 'parameters'");
    }

    private static boolean isNumber(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /**
     * @return the encoded values of the variables of the parsed value expression
     */
    private static Set<String> encodedValuesOf(ParseResult result, Map<String, CustomModel.Parameter> parameters) {
        Set<String> encodedValues = new LinkedHashSet<>(result.guessedVariables);
        encodedValues.removeIf(v -> CustomModelParser.isParameter(v, parameters));
        return encodedValues;
    }

    /**
     * Parses the value expression and throws an exception if it is invalid, contains more than one
     * encoded value or parameter or can result in a negative value.
     */
    static ParseResult parseValue(String valueExpression, Map<String, CustomModel.Parameter> parameters, EncodedValueLookup lookup) {
        ParseResult result = parse(valueExpression, key -> lookup.hasEncodedValue(key) || key.contains(INFINITY) || CustomModelParser.isParameter(key, parameters));
        if (!result.ok)
            throw new IllegalArgumentException(result.invalidMessage);
        checkBikeClimbArgs(result, parameters, lookup);
        Set<String> encodedValues = encodedValuesOf(result, parameters);
        if (encodedValues.size() > 1)
            throw new IllegalArgumentException("Currently only a single EncodedValue is allowed on the right-hand side, but was " + encodedValues.size() + ". Value expression: " + valueExpression);

        // the limits of a single parameter are checked at its range endpoints, see CustomModelParser.checkParameterRanges.
        // bike_climb_factor is exempt as its result is non-negative and finite for all (valid) parameter values
        Set<String> usedParameters = new LinkedHashSet<>(result.guessedVariables);
        usedParameters.removeAll(encodedValues);
        if (result.bikeClimbArgs == null && usedParameters.size() > 1)
            throw new IllegalArgumentException("Currently only a single parameter is allowed on the right-hand side, but was " + usedParameters.size() + ". Value expression: " + valueExpression);
        if (result.bikeClimbArgs == null && usedParameters.size() == 1) {
            Matcher matcher = Pattern.compile("\\b" + usedParameters.iterator().next() + "\\b").matcher(valueExpression);
            matcher.find();
            if (matcher.find())
                throw new IllegalArgumentException("Parameter '" + usedParameters.iterator().next() + "' must not be used more than once. Value expression: " + valueExpression);
        }

        // TODO Nearly duplicate code as in findMinMax
        double value;
        try {
            // Speed optimization for numbers only as its over 200x faster than ExpressionEvaluator+cook+evaluate!
            // We still call the parse() method before as it is only ~3x slower and might increase security slightly. Because certain
            // expressions are accepted from Double.parseDouble but parse() rejects them. With this call order we avoid unexpected security problems.
            value = Double.parseDouble(valueExpression);
        } catch (NumberFormatException ex) {
            String evalExpression = replaceParameters(valueExpression, parameters);
            try {
                if (encodedValues.isEmpty()) { // without encoded values
                    NoArgEvaluator ee = createExpressionEvaluator().createFastEvaluator(evalExpression, NoArgEvaluator.class);
                    value = ee.evaluate();
                } else if (lookup.hasEncodedValue(valueExpression)) { // speed up for common case that complete right-hand side is the encoded value
                    EncodedValue enc = lookup.getEncodedValue(valueExpression, EncodedValue.class);
                    value = Math.min(getMin(enc), getMax(enc));
                } else {
                    // single encoded value
                    String var = encodedValues.iterator().next();
                    SingleArgEvaluator ee = createExpressionEvaluator().createFastEvaluator(evalExpression, SingleArgEvaluator.class, var);
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

    static MinMax findMinMax(String valueExpression, Map<String, CustomModel.Parameter> parameters, EncodedValueLookup lookup) {
        ParseResult result = parse(valueExpression, key -> lookup.hasEncodedValue(key) || key.contains(INFINITY) || CustomModelParser.isParameter(key, parameters));
        if (!result.ok)
            throw new IllegalArgumentException(result.invalidMessage);
        checkBikeClimbArgs(result, parameters, lookup);
        Set<String> encodedValues = encodedValuesOf(result, parameters);
        if (encodedValues.size() > 1)
            throw new IllegalArgumentException("Currently only a single EncodedValue is allowed on the right-hand side, but was " + encodedValues.size() + ". Value expression: " + valueExpression);

        // TODO Nearly duplicate as in parseValue
        try {
            // Speed optimization for numbers only as its over 200x faster than ExpressionEvaluator+cook+evaluate!
            // We still call the parse() method before as it is only ~3x slower and might increase security slightly. Because certain
            // expressions are accepted from Double.parseDouble but parse() rejects them. With this call order we avoid unexpected security problems.
            double val = Double.parseDouble(valueExpression);
            return new MinMax(val, val);
        } catch (NumberFormatException ex) {
        }

        String evalExpression = replaceParameters(valueExpression, parameters);
        try {
            if (encodedValues.isEmpty()) { // without encoded values
                NoArgEvaluator ee = createExpressionEvaluator().createFastEvaluator(evalExpression, NoArgEvaluator.class);
                double val = ee.evaluate();
                return new MinMax(val, val);
            }

            if (lookup.hasEncodedValue(valueExpression)) { // speed up for common case that complete right-hand side is the encoded value
                EncodedValue enc = lookup.getEncodedValue(valueExpression, EncodedValue.class);
                double min = getMin(enc), max = getMax(enc);
                return new MinMax(min, max);
            }

            String var = encodedValues.iterator().next();
            SingleArgEvaluator ee = createExpressionEvaluator().createFastEvaluator(evalExpression, SingleArgEvaluator.class, var);
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

    private static ExpressionEvaluator createExpressionEvaluator() {
        ExpressionEvaluator ee = new ExpressionEvaluator();
        // make the built-in function resolvable
        ee.setDefaultImports("static " + CustomWeightingHelper.class.getName() + ".*");
        return ee;
    }

    static boolean containsEncodedValue(String valueExpression, Map<String, CustomModel.Parameter> parameters, EncodedValueLookup lookup) {
        ParseResult result = parse(valueExpression, key -> lookup.hasEncodedValue(key) || key.contains(INFINITY) || CustomModelParser.isParameter(key, parameters));
        return !result.ok || result.guessedVariables.stream().anyMatch(lookup::hasEncodedValue);
    }

    private static String replaceParameters(String expression, Map<String, CustomModel.Parameter> parameters) {
        for (Map.Entry<String, CustomModel.Parameter> entry : parameters.entrySet()) {
            // parenthesized canonical literal, as e.g. "speed--2" for a negative value would not compile
            String literal = entry.getValue().value() instanceof Number number ? "(" + number.doubleValue() + ")" : entry.getValue().value().toString();
            expression = expression.replaceAll("\\b" + CustomModelParser.PARAM_PREFIX + entry.getKey() + "\\b", literal);
        }
        return Statement.toJavaExpression(expression);
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
