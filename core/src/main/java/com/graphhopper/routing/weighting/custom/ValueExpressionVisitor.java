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
import com.graphhopper.routing.ev.AverageSlope;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.IntEncodedValue;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.*;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.graphhopper.json.Statement.Keyword.IF;

/**
 * Expression visitor for right-hand side value of limit_to or multiply_by.
 */
public class ValueExpressionVisitor implements Visitor.AtomVisitor<Boolean, Exception> {

    private static final String INFINITY = Double.toString(Double.POSITIVE_INFINITY);
    private static final Set<String> allowedMethodParents = Set.of("Math");
    private static final Set<String> allowedMethods = Set.of("sqrt");
    // built-in functions (static methods in CustomWeightingHelper) mapped to their expected number
    // of arguments. They must be monotone in the encoded value argument, see findMinMax.
    static final String BIKE_CLIMB_FACTOR = "bike_climb_factor";
    private static final Map<String, Integer> allowedFunctions = Map.of(BIKE_CLIMB_FACTOR, 2);
    // the pseudo variable and field prefix for the BikeClimbSpeedTable of a bike_climb_factor call
    static final String BIKE_CLIMB_TABLE = "bike_climb_table";
    private final ParseResult result;
    private final NameValidator variableValidator;
    private String invalidMessage;
    // the offset of the single allowed built-in function call, see parse
    private int functionCallStart = -1;
    // a built-in function combined with other terms can be non-monotone in the encoded value which
    // would break the endpoint-based interval calculation in findMinMax. So allow it only as the
    // entire expression, optionally scaled by a literal like "0.9 * bike_climb_speed(...)"
    private boolean functionCallAllowed = true;

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
            if (uop.operator.equals("-")) {
                functionCallAllowed = false;
                return uop.operand.accept(this);
            }
            return false;
        } else if (rv instanceof Java.MethodInvocation mi) {
            if (mi.target == null && allowedFunctions.containsKey(mi.methodName)) {
                if (!functionCallAllowed) {
                    invalidMessage = mi.methodName + " must be the entire expression, optionally scaled like \"0.9 * " + mi.methodName + "(...)\"";
                    return false;
                }
                int arity = allowedFunctions.get(mi.methodName);
                if (mi.arguments.length != arity) {
                    invalidMessage = mi.methodName + " expects " + arity + " arguments, but got: " + mi.arguments.length;
                    return false;
                }
                functionCallStart = mi.getLocation().getColumnNumber() - 1;
                // the slope is not an argument but implicitly the average_slope encoded value
                if (!isValidIdentifier(AverageSlope.KEY)) {
                    invalidMessage = mi.methodName + " requires '" + AverageSlope.KEY + "' which is not available";
                    return false;
                }
                // the arguments (power, mass) must be numbers as CustomModelParser creates a
                // BikeClimbSpeedTable field from them in the generated class. The field is named after
                // the power, which therefore must be an integer (one table per power).
                String[] params = new String[arity];
                for (int i = 0; i < arity; i++) {
                    boolean valid = i == 0 ? mi.arguments[i] instanceof Java.IntegerLiteral
                            : mi.arguments[i] instanceof Java.IntegerLiteral || mi.arguments[i] instanceof Java.FloatingPointLiteral;
                    if (!valid) {
                        invalidMessage = mi.methodName + " expects " + (i == 0 ? "an integer" : "a number") + " as argument " + (i + 1) + ", but got: " + mi.arguments[i];
                        return false;
                    }
                    params[i] = ((Java.Literal) mi.arguments[i]).value;
                }
                result.methods.put(mi.methodName, params);
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
                                functionCallAllowed = false;
                                return mi.arguments[0].accept(this);
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
                boolean allowed = functionCallAllowed;
                functionCallAllowed = allowed && op.equals("*") && binOp.rhs instanceof Java.Literal;
                if (!binOp.lhs.accept(this)) return false;
                functionCallAllowed = allowed && op.equals("*") && binOp.lhs instanceof Java.Literal;
                return binOp.rhs.accept(this);
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
                result.methods = new LinkedHashMap<>();
                ValueExpressionVisitor visitor = new ValueExpressionVisitor(result, variableValidator);
                result.ok = atom.accept(visitor);
                result.invalidMessage = visitor.invalidMessage;
                if (result.ok) {
                    // The converted expression contains the built-in function call in its canonical form
                    // (see toCall), e.g. "bike_climb_factor( 120,95 )" -> "bike_climb_factor(120, 95)", so that
                    // the generated getSpeed code (CustomModelParser.convertValue) and the expression for the
                    // ExpressionEvaluator (toEvaluable) can be derived via a simple String.replace
                    result.converted = new StringBuilder(expression);
                    for (Map.Entry<String, String[]> entry : result.methods.entrySet())
                        result.converted.replace(visitor.functionCallStart, findClosingParen(expression, visitor.functionCallStart) + 1,
                                toCall(entry.getKey(), entry.getValue()));
                }
            }
        } catch (Exception ex) {
        }
        return result;
    }

    /**
     * @return the canonical call of the specified method, e.g. bike_climb_factor(120, 95)
     */
    static String toCall(String method, String[] params) {
        return method + "(" + String.join(", ", params) + ")";
    }

    /**
     * @return the field name in the generated class, e.g. bike_climb_table_120 for bike_climb_table(120, 95)
     */
    static String toFieldName(String bikeClimbTable) {
        return BIKE_CLIMB_TABLE + "_" + bikeClimbTable.substring(BIKE_CLIMB_TABLE.length() + 1, bikeClimbTable.indexOf(','));
    }

    /**
     * @return the expression for the ExpressionEvaluator used in findMinMax and findVariables. Currently
     * only bike_climb_factor is a built-in function, which is replaced by the static function with the slope
     * as explicit first argument, e.g. bike_climb_factor(average_slope, 120, 95)
     */
    private static String toEvaluable(ParseResult result) {
        String expression = result.converted.toString();
        for (Map.Entry<String, String[]> entry : result.methods.entrySet())
            if (entry.getKey().equals(BIKE_CLIMB_FACTOR))
                expression = expression.replace(toCall(entry.getKey(), entry.getValue()),
                        BIKE_CLIMB_FACTOR + "(" + AverageSlope.KEY + ", " + String.join(", ", entry.getValue()) + ")");
        return expression;
    }

    /**
     * @return the offset of the closing parenthesis that ends the function call starting at the
     * given offset. Parentheses are purely structural in a value expression (no strings or
     * comments), so counting the depth is safe.
     */
    private static int findClosingParen(String expression, int callStart) {
        int depth = 0;
        for (int i = callStart; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i;
        }
        throw new IllegalArgumentException("no closing parenthesis found in: " + expression);
    }

    static Set<String> findVariables(List<Statement> statements, EncodedValueLookup lookup) {
        List<List<Statement>> groups = CustomModelParser.splitIntoGroup(statements);
        Set<String> variables = new LinkedHashSet<>();
        for (List<Statement> group : groups) findVariablesForGroup(variables, group, lookup);
        return variables;
    }

    private static void findVariablesForGroup(Set<String> createdObjects, List<Statement> group, EncodedValueLookup lookup) {
        if (group.isEmpty() || !IF.equals(group.get(0).keyword()))
            throw new IllegalArgumentException("Every group of statements must start with an if-statement");

        Statement first = group.get(0);
        if (first.condition().trim().equals("true")) {
            if (first.isBlock()) {
                List<List<Statement>> groups = CustomModelParser.splitIntoGroup(first.doBlock());
                for (List<Statement> subGroup : groups)
                    findVariablesForGroup(createdObjects, subGroup, lookup);
            } else {
                createdObjects.addAll(ValueExpressionVisitor.findVariables(first.value(), lookup));
            }

            if (group.size() > 1)
                throw new IllegalArgumentException("Only one statement allowed for an unconditional statement");
        } else {
            for (Statement st : group) {
                if (st.isBlock()) {
                    List<List<Statement>> groups = CustomModelParser.splitIntoGroup(st.doBlock());
                    for (List<Statement> subGroup : groups)
                        findVariablesForGroup(createdObjects, subGroup, lookup);
                } else {
                    createdObjects.addAll(ValueExpressionVisitor.findVariables(st.value(), lookup));
                }
            }
        }
    }

    static Set<String> findVariables(String valueExpression, EncodedValueLookup lookup) {
        ParseResult result = parse(valueExpression, key -> lookup.hasEncodedValue(key) || key.contains(INFINITY));
        if (!result.ok)
            throw new IllegalArgumentException(result.invalidMessage);
        if (result.guessedVariables.size() > 1)
            throw new IllegalArgumentException("Currently only a single EncodedValue is allowed on the right-hand side, but was " + result.guessedVariables.size() + ". Value expression: " + valueExpression);

        // TODO Nearly duplicate code as in findMinMax
        double value;
        try {
            // Speed optimization for numbers only as its over 200x faster than ExpressionEvaluator+cook+evaluate!
            // We still call the parse() method before as it is only ~3x slower and might increase security slightly. Because certain
            // expressions are accepted from Double.parseDouble but parse() rejects them. With this call order we avoid unexpected security problems.
            value = Double.parseDouble(valueExpression);
        } catch (NumberFormatException ex) {
            try {
                if (result.guessedVariables.isEmpty()) { // without encoded values
                    NoArgEvaluator ee = createExpressionEvaluator().createFastEvaluator(valueExpression, NoArgEvaluator.class);
                    value = ee.evaluate();
                } else if (lookup.hasEncodedValue(valueExpression)) { // speed up for common case that complete right-hand side is the encoded value
                    EncodedValue enc = lookup.getEncodedValue(valueExpression, EncodedValue.class);
                    value = Math.min(getMin(enc), getMax(enc));
                } else {
                    // single encoded value
                    String var = result.guessedVariables.iterator().next();
                    SingleArgEvaluator ee = createExpressionEvaluator().createFastEvaluator(toEvaluable(result), SingleArgEvaluator.class, var);
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

        if (result.methods.isEmpty()) return result.guessedVariables;
        // the generated class needs a field for the table, see CustomModelParser.createClassTemplate
        Set<String> variables = new LinkedHashSet<>(result.guessedVariables);
        for (String[] params : result.methods.values()) variables.add(toCall(BIKE_CLIMB_TABLE, params));
        return variables;
    }

    static MinMax findMinMax(String valueExpression, EncodedValueLookup lookup) {
        ParseResult result = parse(valueExpression, lookup::hasEncodedValue);
        if (!result.ok)
            throw new IllegalArgumentException(result.invalidMessage);
        if (result.guessedVariables.size() > 1)
            throw new IllegalArgumentException("Currently only a single EncodedValue is allowed on the right-hand side, but was " + result.guessedVariables.size() + ". Value expression: " + valueExpression);

        // TODO Nearly duplicate as in findVariables
        try {
            // Speed optimization for numbers only as its over 200x faster than ExpressionEvaluator+cook+evaluate!
            // We still call the parse() method before as it is only ~3x slower and might increase security slightly. Because certain
            // expressions are accepted from Double.parseDouble but parse() rejects them. With this call order we avoid unexpected security problems.
            double val = Double.parseDouble(valueExpression);
            return new MinMax(val, val);
        } catch (NumberFormatException ex) {
        }

        try {
            if (result.guessedVariables.isEmpty()) { // without encoded values
                NoArgEvaluator ee = createExpressionEvaluator().createFastEvaluator(valueExpression, NoArgEvaluator.class);
                double val = ee.evaluate();
                return new MinMax(val, val);
            }

            if (lookup.hasEncodedValue(valueExpression)) { // speed up for common case that complete right-hand side is the encoded value
                EncodedValue enc = lookup.getEncodedValue(valueExpression, EncodedValue.class);
                double min = getMin(enc), max = getMax(enc);
                return new MinMax(min, max);
            }

            String var = result.guessedVariables.iterator().next();
            SingleArgEvaluator ee = createExpressionEvaluator().createFastEvaluator(toEvaluable(result), SingleArgEvaluator.class, var);
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
        // make the built-in functions from allowedFunctions resolvable
        ee.setDefaultImports("static " + CustomWeightingHelper.class.getName() + ".*");
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
