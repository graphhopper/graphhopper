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

import org.codehaus.janino.Scanner;
import org.codehaus.janino.*;

import java.io.StringReader;
import java.util.*;

import static com.graphhopper.routing.weighting.custom.ExpressionBuilder.toEncodedValueClassName;

class ExpressionVisitor implements Visitor.AtomVisitor<Boolean, Exception> {

    private final ParseResult result;
    private final TreeMap<Integer, String> injects = new TreeMap<>();
    private final NameValidator nameValidator;
    private final Set<String> allowedMethods = new HashSet<>(Arrays.asList("ordinal", "getDistance", "getName",
            "contains", "sqrt", "abs"));

    public ExpressionVisitor(ParseResult result, NameValidator nameValidator) {
        this.result = result;
        this.nameValidator = nameValidator;
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
            if (n.identifiers.length == 1)
                // e.g. like road_class
                return isValidIdentifier(n.identifiers[0]);
            return false;
        }
        if (rv instanceof Java.Literal)
            return true;
        if (rv instanceof Java.MethodInvocation) {
            Java.MethodInvocation mi = (Java.MethodInvocation) rv;
            if (allowedMethods.contains(mi.methodName)) {
                // skip methods like this.in() for now
                if (mi.target == null)
                    return false;
                // edge.getDistance, Math.sqrt => check target name (edge or Math)
                Java.AmbiguousName n = (Java.AmbiguousName) mi.target.toRvalue();
                return n.identifiers.length == 2 && isValidIdentifier(n.identifiers[0]);
            }
            return false;
        }
        if (rv instanceof Java.BinaryOperation) {
            Java.BinaryOperation binOp = (Java.BinaryOperation) rv;
            if (!binOp.lhs.accept(this) || !binOp.rhs.accept(this))
                return false;
            if (binOp.lhs instanceof Java.AmbiguousName && binOp.rhs instanceof Java.AmbiguousName) {
                if (nameValidator.isValid(binOp.lhs.toString()) &&
                        binOp.rhs.toString().toUpperCase(Locale.ROOT).equals(binOp.rhs.toString())) {
                    injects.put(binOp.rhs.getLocation().getColumnNumber() - 1, binOp.lhs.toString());
                }
            }
            return true;
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
    static ParseResult parseExpression(String expression, NameValidator validator) {
        ParseResult result = new ParseResult();
        if (expression.length() > 100)
            return result;
        try {
            Parser parser = new Parser(new Scanner("ignore", new StringReader(expression)));
            Java.Atom atom = parser.parseConditionalExpression();
            // after parsing the expression the input should end (otherwise it is not "simple")
            if (parser.peek().type == TokenType.END_OF_INPUT) {
                result.guessedVariables = new LinkedHashSet<>();
                ExpressionVisitor visitor = new ExpressionVisitor(result, validator);
                result.ok = atom.accept(visitor);
                if (result.ok) {
                    result.converted = new StringBuilder(expression.length());
                    int start = 0;
                    // Insert class name to avoid conflict if e.g. enum values are identically named (e.g. bike_network != OTHER)
                    for (Map.Entry<Integer, String> inject : visitor.injects.entrySet()) {
                        String value = toEncodedValueClassName(inject.getValue());
                        result.converted.append(expression, start, inject.getKey()).append(value).append('.');
                        start = inject.getKey();
                    }
                    result.converted.append(expression.substring(start));
                }
            }

            return result;
        } catch (Exception ex) {
            return result;
        }
    }

    static class ParseResult {
        StringBuilder converted;
        boolean ok;
        Set<String> guessedVariables;
    }

    interface NameValidator {
        boolean isValid(String name);
    }
}
