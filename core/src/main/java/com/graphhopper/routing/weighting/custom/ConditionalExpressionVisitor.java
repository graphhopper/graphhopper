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

import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.RouteNetwork;
import com.graphhopper.routing.ev.StringEncodedValue;
import com.graphhopper.util.Helper;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.*;

import java.io.StringReader;
import java.util.*;

import static com.graphhopper.routing.weighting.custom.CustomModelParser.IN_AREA_PREFIX;

/**
 * Expression visitor for the if or else_if condition.
 */
class ConditionalExpressionVisitor implements Visitor.AtomVisitor<Boolean, Exception> {

    private final ParseResult result;
    private final EncodedValueLookup lookup;
    private final TreeMap<Integer, Replacement> replacements = new TreeMap<>();
    private final NameValidator nameValidator;
    private final Set<String> allowedMethods = new HashSet<>(Arrays.asList("ordinal", "getDistance", "getName",
            "contains", "sqrt", "abs"));
    private String invalidMessage;

    public ConditionalExpressionVisitor(ParseResult result, NameValidator nameValidator, EncodedValueLookup lookup) {
        this.result = result;
        this.nameValidator = nameValidator;
        this.lookup = lookup;
    }

    // allow only methods and other identifiers (constants and encoded values)
    boolean isValidIdentifier(String identifier) {
        if (nameValidator.isValid(identifier)) {
            if (!Character.isUpperCase(identifier.charAt(0)))
                result.guessedVariables.add(identifier);
            return true;
        }
        return false;
    }

    @Override
    public Boolean visitRvalue(Java.Rvalue rv) throws Exception {
        if (rv instanceof Java.AmbiguousName) {
            Java.AmbiguousName n = (Java.AmbiguousName) rv;
            if (n.identifiers.length == 1) {
                String arg = n.identifiers[0];
                if (arg.startsWith(IN_AREA_PREFIX)) {
                    int start = rv.getLocation().getColumnNumber() - 1;
                    replacements.put(start, new Replacement(start, arg.length(),
                            CustomWeightingHelper.class.getSimpleName() + ".in(this." + arg + ", edge)"));
                    result.guessedVariables.add(arg);
                    return true;
                } else {
                    // e.g. like road_class
                    if (isValidIdentifier(arg)) return true;
                    invalidMessage = "'" + arg + "' not available";
                    return false;
                }
            }
            invalidMessage = "identifier " + n + " invalid";
            return false;
        }
        if (rv instanceof Java.Literal) {
            return true;
        } else if (rv instanceof Java.UnaryOperation) {
            Java.UnaryOperation uo = (Java.UnaryOperation) rv;
            if (uo.operator.equals("!")) return uo.operand.accept(this);
            if (uo.operator.equals("-")) return uo.operand.accept(this);
            return false;
        } else if (rv instanceof Java.MethodInvocation) {
            Java.MethodInvocation mi = (Java.MethodInvocation) rv;
            if (allowedMethods.contains(mi.methodName)) {
                // skip methods like this.in() for now
                if (mi.target != null) {
                    // edge.getDistance, Math.sqrt => check target name (edge or Math)
                    Java.AmbiguousName n = (Java.AmbiguousName) mi.target.toRvalue();
                    if (n.identifiers.length == 2 && isValidIdentifier(n.identifiers[0])) return true;
                }
            }
            invalidMessage = mi.methodName + " is an illegal method";
            return false;
        } else if (rv instanceof Java.ParenthesizedExpression) {
            return ((Java.ParenthesizedExpression) rv).value.accept(this);
        } else if (rv instanceof Java.BinaryOperation) {
            Java.BinaryOperation binOp = (Java.BinaryOperation) rv;
            int startRH = binOp.rhs.getLocation().getColumnNumber() - 1;
            if (binOp.lhs instanceof Java.AmbiguousName && ((Java.AmbiguousName) binOp.lhs).identifiers.length == 1) {
                String lhVarAsString = ((Java.AmbiguousName) binOp.lhs).identifiers[0];
                boolean eqOps = binOp.operator.equals("==") || binOp.operator.equals("!=");
                if (binOp.rhs instanceof Java.StringLiteral) {
                    // replace String with its index for faster comparison (?) and skipping the Map<String, Integer> lookup at runtime
                    if (lookup.hasEncodedValue(lhVarAsString)) {
                        if (!eqOps)
                            throw new IllegalArgumentException("Operator " + binOp.operator + " not allowed for String");
                        StringEncodedValue ev = lookup.getStringEncodedValue(lhVarAsString);
                        String str = ((Java.StringLiteral) binOp.rhs).value;
                        int integ = ev.indexOf(str.substring(1, str.length() - 1));
                        if (integ == 0) integ = -1; // 0 means not found and this should always trigger inequality
                        replacements.put(startRH, new Replacement(startRH, str.length(), "" + integ));
                    }
                } else if (binOp.rhs instanceof Java.AmbiguousName && ((Java.AmbiguousName) binOp.rhs).identifiers.length == 1) {
                    // Make enum explicit as NO or OTHER can occur in other enums so convert "toll == NO" to "toll == Toll.NO"
                    String rhValueAsString = ((Java.AmbiguousName) binOp.rhs).identifiers[0];
                    if (nameValidator.isValid(lhVarAsString) && Helper.toUpperCase(rhValueAsString).equals(rhValueAsString)) {
                        if (!eqOps)
                            throw new IllegalArgumentException("Operator " + binOp.operator + " not allowed for enum");
                        String value = toEncodedValueClassName(binOp.lhs.toString());
                        replacements.put(startRH, new Replacement(startRH, rhValueAsString.length(), value + "." + rhValueAsString));
                    }
                }
            }
            return binOp.lhs.accept(this) && binOp.rhs.accept(this);
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

    /**
     * Enforce simple expressions of user input to increase security.
     *
     * @return ParseResult with ok if it is a valid and "simple" expression. It contains all guessed variables and a
     * converted expression that includes class names for constants to avoid conflicts e.g. when doing "toll == Toll.NO"
     * instead of "toll == NO".
     */
    static ParseResult parse(String expression, NameValidator validator, EncodedValueLookup lookup) {
        ParseResult result = new ParseResult();
        try {
            Parser parser = new Parser(new Scanner("ignore", new StringReader(expression)));
            Java.Atom atom = parser.parseConditionalExpression();
            // after parsing the expression the input should end (otherwise it is not "simple")
            if (parser.peek().type == TokenType.END_OF_INPUT) {
                result.guessedVariables = new LinkedHashSet<>();
                ConditionalExpressionVisitor visitor = new ConditionalExpressionVisitor(result, validator, lookup);
                result.ok = atom.accept(visitor);
                result.invalidMessage = visitor.invalidMessage;
                if (result.ok) {
                    result.converted = new StringBuilder(expression.length());
                    int start = 0;
                    for (Replacement replace : visitor.replacements.values()) {
                        result.converted.append(expression, start, replace.start).append(replace.newString);
                        start = replace.start + replace.oldLength;
                    }
                    result.converted.append(expression.substring(start));
                }
            }
        } catch (Exception ex) {
        }
        return result;
    }

    static String toEncodedValueClassName(String arg) {
        if (arg.isEmpty()) throw new IllegalArgumentException("Cannot be empty");
        if (arg.endsWith(RouteNetwork.key(""))) return RouteNetwork.class.getSimpleName();
        String clazz = Helper.underScoreToCamelCase(arg);
        return Character.toUpperCase(clazz.charAt(0)) + clazz.substring(1);
    }

    class Replacement {
        int start;
        int oldLength;
        String newString;

        public Replacement(int start, int oldLength, String newString) {
            this.start = start;
            this.oldLength = oldLength;
            this.newString = newString;
        }
    }
}
