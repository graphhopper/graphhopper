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
import org.codehaus.janino.Scanner;
import org.codehaus.janino.*;

import java.io.StringReader;
import java.util.*;

import static com.graphhopper.routing.weighting.custom.CustomModelParser.IN_AREA_PREFIX;

/**
 * Checks the if/else_if condition and builds the "converted" Java from the parsed condition, not by
 * copying parts of the original text.
 */
class ConditionalExpressionVisitor {

    private static final Set<String> allowedMethodParents = new HashSet<>(Arrays.asList("edge", "Math", "country"));
    private static final Set<String> allowedMethods = new HashSet<>(Arrays.asList("ordinal", "getDistance",
            "contains", "sqrt", "abs", "isRightHandTraffic", "equals"));
    // operators are written as fixed strings, never copied from the user text; the list keeps conditions simple
    private static final Set<String> allowedBinaryOps = new HashSet<>(Arrays.asList(
            "==", "!=", "<", ">", "<=", ">=", "&&", "||", "*", "+", "-", "/", "%"));
    private static final String HELPER = CustomWeightingHelper.class.getSimpleName();

    private final ParseResult result;
    private final NameValidator variableValidator;
    private final ClassHelper classHelper;

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

    /**
     * Checks rv against the allowed list and returns the Java text for it. Throws for anything not
     * allowed, so nothing unexpected can reach the output.
     */
    String emit(Java.Rvalue rv) throws Exception {
        if (rv instanceof Java.AmbiguousName) {
            Java.AmbiguousName n = (Java.AmbiguousName) rv;
            if (n.identifiers.length != 1)
                throw new IllegalArgumentException("identifier " + n + " invalid");
            String arg = n.identifiers[0];
            if (arg.startsWith(IN_AREA_PREFIX)) {
                result.guessedVariables.add(arg);
                // area name: write the helper call directly (not copied from the user text)
                return HELPER + ".in(this." + arg + ", edge)";
            }
            if (isValidIdentifier(arg)) return arg;
            throw new IllegalArgumentException("'" + arg + "' not available");
        } else if (rv instanceof Java.Literal) {
            // Emit the token as scanned. Safe for text too: the scanner only yields
            // a closed string with inner quotes escaped. A quote written as \\u0022 ends the string like a
            // normal quote and the rest is parsed and validated as code, i.e. same as typing the quote.
            return ((Java.Literal) rv).value;
        } else if (rv instanceof Java.UnaryOperation) {
            Java.UnaryOperation uo = (Java.UnaryOperation) rv;
            if (!uo.operator.equals("!") && !uo.operator.equals("-"))
                throw new IllegalArgumentException("unary operator " + uo.operator + " not allowed");
            String operand = emit(uo.operand);
            // "- -x" must not become the decrement "--x"
            return uo.operator.equals("-") && operand.startsWith("-") ? "- " + operand : uo.operator + operand;
        } else if (rv instanceof Java.MethodInvocation) {
            Java.MethodInvocation mi = (Java.MethodInvocation) rv;
            String illegal = mi.methodName + " is an illegal method in a conditional expression";
            // a chained call like edge.getName().contains("A 4") has no 2-identifier AmbiguousName target -> rejected
            if (!allowedMethods.contains(mi.methodName) || mi.target == null
                    || !(mi.target.toRvalue() instanceof Java.AmbiguousName))
                throw new IllegalArgumentException(illegal);
            Java.AmbiguousName n = (Java.AmbiguousName) mi.target.toRvalue();
            // Janino models "a.b()" with target identifiers [a, b] -> [0] is the parent (edge, Math, road_class, ...)
            if (n.identifiers.length != 2 || mi.arguments.length > 1)
                throw new IllegalArgumentException(illegal);
            String parent = n.identifiers[0];
            boolean parentAllowed = allowedMethodParents.contains(parent);
            if (!parentAllowed && !variableValidator.isValid(parent))
                throw new IllegalArgumentException(illegal);
            // edge.getDistance() or road_class.ordinal() => remember edge or road_class, but not Math of Math.sqrt(x)
            if (!parentAllowed || mi.arguments.length == 0)
                result.guessedVariables.add(parent);
            String arg = mi.arguments.length == 0 ? "" : emit(mi.arguments[0]);
            return parent + "." + mi.methodName + "(" + arg + ")";
        } else if (rv instanceof Java.ParenthesizedExpression) {
            return "(" + emit(((Java.ParenthesizedExpression) rv).value) + ")";
        } else if (rv instanceof Java.BinaryOperation) {
            Java.BinaryOperation binOp = (Java.BinaryOperation) rv;
            if (!allowedBinaryOps.contains(binOp.operator))
                throw new IllegalArgumentException("operator " + binOp.operator + " not allowed");
            String lh = emit(binOp.lhs);
            // validates the enum value too
            String rh = emit(binOp.rhs);
            if (binOp.lhs instanceof Java.AmbiguousName && ((Java.AmbiguousName) binOp.lhs).identifiers.length == 1
                    && binOp.rhs instanceof Java.AmbiguousName && ((Java.AmbiguousName) binOp.rhs).identifiers.length == 1) {
                String lhVar = ((Java.AmbiguousName) binOp.lhs).identifiers[0];
                String rhValue = ((Java.AmbiguousName) binOp.rhs).identifiers[0];
                // make enum explicit as NO/OTHER can occur in other enums: "toll == NO" -> "toll == Toll.NO"
                if (variableValidator.isValid(lhVar) && Helper.toUpperCase(rhValue).equals(rhValue)) {
                    if (!binOp.operator.equals("==") && !binOp.operator.equals("!="))
                        throw new IllegalArgumentException("Operator " + binOp.operator + " not allowed for enum");
                    String enumClass = classHelper.getClassName(lhVar);
                    if (!Helper.isEmpty(enumClass))
                        rh = enumClass + "." + rhValue;
                }
            }
            return lh + " " + binOp.operator + " " + rh;
        }
        throw new IllegalArgumentException("invalid expression " + rv);
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
        result.guessedVariables = new LinkedHashSet<>();
        try {
            // no file name => message starts with "Line 1, Column 7: ..."
            Parser parser = new Parser(new Scanner(null, new StringReader(expression)));
            Java.Atom atom = parser.parseConditionalExpression();
            // after parsing the expression the input should end (otherwise it is not "simple")
            if (parser.peek().type != TokenType.END_OF_INPUT)
                throw new IllegalArgumentException("expression is not simple");
            ConditionalExpressionVisitor visitor = new ConditionalExpressionVisitor(result, validator, helper);
            result.converted = visitor.emit(atom.toRvalue());
            result.ok = true;
        } catch (Exception ex) {
            // fail closed: never leave a partially built "converted" that could reach the compiler
            result.ok = false;
            result.converted = null;
            if (result.invalidMessage == null)
                result.invalidMessage = ex.getMessage();
        }
        return result;
    }
}
