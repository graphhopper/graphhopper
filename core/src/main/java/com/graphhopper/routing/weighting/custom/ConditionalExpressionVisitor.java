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

import com.graphhopper.routing.ev.KVStorageEncodedValue;
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
    private final TreeMap<Integer, Replacement> replacements = new TreeMap<>();
    private final NameValidator variableValidator;
    private final ClassHelper classHelper;
    private String invalidMessage;

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
            return ((Java.ParenthesizedExpression) rv).value.accept(this);
        } else if (rv instanceof Java.BinaryOperation) {
            Java.BinaryOperation binOp = (Java.BinaryOperation) rv;
            int startRH = binOp.rhs.getLocation().getColumnNumber() - 1;

            // Handle tag("key") or tag.get("key") compared to a string or null, in either order
            Java.MethodInvocation tagCall = isTagCall(binOp.lhs) ? (Java.MethodInvocation) binOp.lhs
                    : isTagCall(binOp.rhs) ? (Java.MethodInvocation) binOp.rhs : null;
            Java.Rvalue other = tagCall == binOp.lhs ? binOp.rhs : binOp.lhs;
            if (tagCall != null && other instanceof Java.Literal) {
                boolean isNull = isNullLiteral(other);
                boolean isString = isStringLiteral(other);
                if (isNull || isString) {
                    if (!binOp.operator.equals("==") && !binOp.operator.equals("!="))
                        throw new IllegalArgumentException("Only == and != allowed for tag() comparison");

                    String key = extractStringLiteralValue(((Java.Literal) tagCall.arguments[0]).value);
                    String fieldName = KVStorageEncodedValue.toFieldName(key);
                    result.guessedVariables.add(fieldName);

                    int tagCallStart = tagCall.getLocation().getColumnNumber() - 1;
                    Java.Literal argLiteral = (Java.Literal) tagCall.arguments[0];
                    int tagCallEnd = argLiteral.getLocation().getColumnNumber() - 1 + argLiteral.value.length() + 1;
                    int otherStart = other.getLocation().getColumnNumber() - 1;
                    int otherEnd = otherStart + ((Java.Literal) other).value.length();

                    int exprStart = Math.min(tagCallStart, otherStart);
                    int exprEnd = Math.max(tagCallEnd, otherEnd);

                    String getCall = "edge.get(this." + fieldName + "_enc)";
                    String newExpr;
                    if (isNull) {
                        newExpr = getCall + " " + binOp.operator + " null";
                    } else {
                        String value = extractStringLiteralValue(((Java.Literal) other).value);
                        String prefix = binOp.operator.equals("!=") ? "!" : "";
                        newExpr = prefix + "\"" + value + "\".equals(" + getCall + ")";
                    }

                    replacements.put(exprStart, new Replacement(exprStart, exprEnd - exprStart, newExpr));
                    return true;
                }
            }

            if (binOp.lhs instanceof Java.AmbiguousName && ((Java.AmbiguousName) binOp.lhs).identifiers.length == 1) {
                String lhVarAsString = ((Java.AmbiguousName) binOp.lhs).identifiers[0];
                boolean eqOps = binOp.operator.equals("==") || binOp.operator.equals("!=");
                if (binOp.rhs instanceof Java.AmbiguousName && ((Java.AmbiguousName) binOp.rhs).identifiers.length == 1) {
                    // Make enum explicit as NO or OTHER can occur in other enums so convert "toll == NO" to "toll == Toll.NO"
                    String rhValueAsString = ((Java.AmbiguousName) binOp.rhs).identifiers[0];
                    if (variableValidator.isValid(lhVarAsString) && Helper.toUpperCase(rhValueAsString).equals(rhValueAsString)) {
                        if (!eqOps)
                            throw new IllegalArgumentException("Operator " + binOp.operator + " not allowed for enum");
                        String value = classHelper.getClassName(binOp.lhs.toString());
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

    private static boolean isTagCall(Java.Rvalue rvalue) {
        if (!(rvalue instanceof Java.MethodInvocation)) return false;
        Java.MethodInvocation mi = (Java.MethodInvocation) rvalue;
        if (mi.arguments.length != 1 || !(mi.arguments[0] instanceof Java.Literal)) return false;
        return "tag".equals(mi.methodName) && mi.target == null;
    }

    private static boolean isStringLiteral(Java.Rvalue rvalue) {
        if (!(rvalue instanceof Java.Literal)) return false;
        String value = ((Java.Literal) rvalue).value;
        return value.startsWith("\"") && value.endsWith("\"");
    }

    private static boolean isNullLiteral(Java.Rvalue rvalue) {
        return rvalue instanceof Java.Literal && "null".equals(((Java.Literal) rvalue).value);
    }

    private static String extractStringLiteralValue(String literalValue) {
        return literalValue.substring(1, literalValue.length() - 1);
    }

    /**
     * Convert single-quoted strings to double-quoted strings for Janino parsing.
     * Escaped single quotes (\') inside strings are converted to literal single quotes.
     */
    static String convertSingleToDoubleQuotes(String expression) {
        boolean hasSingleQuotes = false;
        for (int i = 0; i < expression.length(); i++) {
            if (expression.charAt(i) == '"')
                throw new IllegalArgumentException("Double quotes are not allowed in expression: " + expression);
            if (expression.charAt(i) == '\'' && (i == 0 || expression.charAt(i - 1) != '\\')) {
                hasSingleQuotes = true;
                break;
            }
        }
        if (!hasSingleQuotes) return expression;

        StringBuilder sb = new StringBuilder(expression.length());
        int unescapedCount = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '\\' && i + 1 < expression.length() && expression.charAt(i + 1) == '\'') {
                // escaped single quote \' -> literal single quote (valid inside double-quoted strings)
                sb.append('\'');
                i++; // skip the quote
            } else if (c == '\'') {
                sb.append('"');
                unescapedCount++;
            } else {
                sb.append(c);
            }
        }
        if (unescapedCount % 2 != 0)
            throw new IllegalArgumentException("Unmatched single quotes in expression: " + expression);
        return sb.toString();
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
            String convertedExpression = convertSingleToDoubleQuotes(expression);
            Parser parser = new Parser(new Scanner("ignore", new StringReader(convertedExpression)));
            Java.Atom atom = parser.parseConditionalExpression();
            // after parsing the expression the input should end (otherwise it is not "simple")
            if (parser.peek().type == TokenType.END_OF_INPUT) {
                result.guessedVariables = new LinkedHashSet<>();
                ConditionalExpressionVisitor visitor = new ConditionalExpressionVisitor(result, validator, helper);
                result.ok = atom.accept(visitor);
                result.invalidMessage = visitor.invalidMessage;
                if (result.ok) {
                    result.converted = new StringBuilder(convertedExpression.length());
                    int start = 0;
                    for (Replacement replace : visitor.replacements.values()) {
                        result.converted.append(convertedExpression, start, replace.start).append(replace.newString);
                        start = replace.start + replace.oldLength;
                    }
                    result.converted.append(convertedExpression.substring(start));
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
