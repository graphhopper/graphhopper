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

import com.graphhopper.util.Helper;
import org.codehaus.janino.*;
import org.codehaus.janino.Scanner;

import java.io.StringReader;
import java.util.*;

import static com.graphhopper.routing.weighting.custom.CustomModelParser.IN_AREA_PREFIX;

/**
 * Expression visitor for the if or else_if condition.
 */
class ConditionalExpressionVisitor implements Visitor.AtomVisitor<Boolean, Exception> {

    private static final Set<String> allowedMethodParents = new HashSet<>(Arrays.asList("edge", "Math", "country"));
    private static final Set<String> allowedMethods = new HashSet<>(Arrays.asList("ordinal", "getDistance", "getName",
            "contains", "sqrt", "abs", "isRightHandTraffic"));
    private final ParseResult result;
    private final List<Replacement> replacements = new ArrayList<>();
    private final NameValidator variableValidator;
    private final ClassHelper classHelper;
    private String invalidMessage;
    private Java.BinaryOperation lhsOpForVarInclude;
    private Java.BinaryOperation opForTypeInclude;

    public ConditionalExpressionVisitor(ParseResult result, NameValidator variableValidator, ClassHelper classHelper) {
        this.result = result;
        this.variableValidator = variableValidator;
        this.classHelper = classHelper;
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
        if (rv instanceof Java.AmbiguousName) {
            Java.AmbiguousName n = (Java.AmbiguousName) rv;
            if (n.identifiers.length == 1) {
                String arg = n.identifiers[0];
                if (arg.startsWith(IN_AREA_PREFIX)) {
                    int start = rv.getLocation().getColumnNumber() - 1;
                    replacements.add(new Replacement(start, arg.length(),
                            CustomWeightingHelper.class.getSimpleName() + ".in(this." + arg + ", edge)"));
                    result.guessedVariables.add(arg);
                    return true;
                } else {
                    // arg is e.g. "road_class" or "MOTORWAY"
                    if (isValidIdentifier(arg)) {
                        int start = rv.getLocation().getColumnNumber() - 1;
                        if (lhsOpForVarInclude != null)
                            replacements.add(new Replacement(start, 0, lhsOpForVarInclude.lhs + " " + lhsOpForVarInclude.operator + " "));

                        if (opForTypeInclude != null && Helper.toUpperCase(arg).equals(arg))
                            // lhs must be a variable and the arg is the rhs and must be an enum
                            replacements.add(new Replacement(start, 0, classHelper.getClassName(opForTypeInclude.lhs.toString()) + "."));

                        return true;
                    }
                    invalidMessage = "'" + arg + "' not available";
                    return false;
                }
            }
            invalidMessage = "identifier " + n + " invalid";
            return false;
        }
        if (rv instanceof Java.Literal) {
            return true;
        } else if (rv instanceof Java.UnaryOperation uo) {
            if (uo.operator.equals("!")) return uo.operand.accept(this);
            if (uo.operator.equals("-")) return uo.operand.accept(this);
            return false;
        } else if (rv instanceof Java.MethodInvocation mi) {
            if (allowedMethods.contains(mi.methodName) && mi.target != null) {
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
                        }
                    } else if (variableValidator.isValid(n.identifiers[0])) {
                        // road_class.ordinal()
                        if (mi.arguments.length == 0) {
                            result.guessedVariables.add(n.identifiers[0]); // return road_class
                            return true;
                        }
                    }
                }
            }
            invalidMessage = mi.methodName + " is an illegal method in a conditional expression";
            return false;
        } else if (rv instanceof Java.ParenthesizedExpression) {
            lhsOpForVarInclude = null;
            opForTypeInclude = null;
            return ((Java.ParenthesizedExpression) rv).value.accept(this);
        } else if (rv instanceof Java.BinaryOperation binOp) {
            // Handle "type includes": for expressions like "road_class == MOTORWAY", store the operation
            // in opForTypeInclude so that the rhs value is prefixed with its enum type. Results in: "road_class == RoadClass.MOTORWAY".
            // To avoid confusion and simplify reasoning the variable opForTypeInclude cannot be overwritten and will be the same (even recursively downstream) until set to null at the end.
            boolean doTypeInclude = opForTypeInclude == null && (opForTypeInclude = getEnumOp(binOp)) != null;

            boolean lhsRet = binOp.lhs.accept(this);

            // Handle "variable includes": for expressions like "road_class == MOTORWAY || SECONDARY || TERTIARY",
            // store the left-most operation in lhsOpForVarInclude so that all subsequent rhs values (SECONDARY and TERTIARY) will get the "road_class ==" prefix too.
            // Results in: "road_class == MOTORWAY || road_class == SECONDARY || road_class == TERTIARY".
            boolean doVarInclude = lhsOpForVarInclude == null && (lhsOpForVarInclude = getLHSOp(binOp)) != null;

            // Special case for "variable includes": we need to enable "type includes" for all subsequent rhs values too.
            // Results in: "road_class == RoadClass.MOTORWAY || road_class == RoadClass.SECONDARY || road_class == RoadClass.TERTIARY".
            if (lhsOpForVarInclude != null)
                doTypeInclude = (opForTypeInclude = getEnumOp(lhsOpForVarInclude)) != null;

            boolean ret = lhsRet && binOp.rhs.accept(this);
            if (doVarInclude) lhsOpForVarInclude = null;
            if (doTypeInclude) opForTypeInclude = null;
            return ret;
        }
        return false;
    }

    /**
     * This recursive method extracts the first lhs operation 'var == A' of a binary operation 'tree'
     * like: 'var == A || B || ...' and it ensures that the rhs expressions (like A) are no
     * BinaryOperations and e.g. just enums.
     */
    private Java.BinaryOperation getLHSOp(Java.Rvalue v) {
        if (v instanceof Java.BinaryOperation binOp) {
            if (!(binOp.rhs instanceof Java.BinaryOperation)
                    && ("&&".equals(binOp.operator) || "||".equals(binOp.operator))) {
                Java.Rvalue lhs = binOp.lhs;
                if (lhs instanceof Java.BinaryOperation binOp2) {
                    if ("==".equals(binOp2.operator) || "!=".equals(binOp2.operator)) {
                        if (binOp2.lhs instanceof Java.AmbiguousName) return binOp2;
                        return null;
                    } else {
                        return getLHSOp(binOp2);
                    }
                }
            }
        }
        return null;
    }

    private Java.BinaryOperation getEnumOp(Java.Rvalue v) {
        if (v instanceof Java.BinaryOperation binOp) {
            if ("==".equals(binOp.operator) || "!=".equals(binOp.operator)) {
                String rhValueAsString = binOp.rhs.toString();
                if (variableValidator.isValid(binOp.lhs.toString()) && Helper.toUpperCase(rhValueAsString).equals(rhValueAsString))
                    return binOp;
                return null;
            }
        }
        return null;
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
    static ParseResult parse(String expression, NameValidator validator, ClassHelper helper) {
        ParseResult result = new ParseResult();
        try {
            Parser parser = new Parser(new Scanner("ignore", new StringReader(expression)));
            Java.Atom atom = parser.parseConditionalExpression();
            // after parsing the expression the input should end (otherwise it is not "simple")
            if (parser.peek().type == TokenType.END_OF_INPUT) {
                result.guessedVariables = new LinkedHashSet<>();
                ConditionalExpressionVisitor visitor = new ConditionalExpressionVisitor(result, validator, helper);
                result.ok = atom.accept(visitor);
                result.invalidMessage = visitor.invalidMessage;
                if (result.ok) {
                    result.converted = new StringBuilder(expression.length());
                    int start = 0;
                    visitor.replacements.sort(Comparator.comparingInt(r -> r.start));
                    for (Replacement replace : visitor.replacements) {
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

    static class Replacement {
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
