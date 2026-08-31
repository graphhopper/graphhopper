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
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.util.CustomModel;
import org.codehaus.janino.Java;
import org.codehaus.janino.Parser;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.TokenType;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.graphhopper.json.Statement.Keyword.ELSE;
import static com.graphhopper.json.Statement.Keyword.IF;

public class FindMinMax {

    /**
     * This method throws an exception when this CustomModel would decrease the edge weight compared to the specified
     * baseModel as in such a case the optimality of A* with landmarks can no longer be guaranteed (as the preparation
     * is based on baseModel).
     */
    public static void checkLMConstraints(CustomModel baseModel, CustomModel queryModel, EncodedValueLookup lookup) {
        if (queryModel.getDistanceInfluence() != null) {
            double bmDI = baseModel.getDistanceInfluence() == null ? 0 : baseModel.getDistanceInfluence();
            if (queryModel.getDistanceInfluence() < bmDI)
                throw new IllegalArgumentException("CustomModel in query can only use distance_influence bigger or equal to "
                        + bmDI + ", but was: " + queryModel.getDistanceInfluence());
        }

        Map<String, Object> parameters = new LinkedHashMap<>(baseModel.getParameters());
        parameters.putAll(queryModel.getParameters());

        // changing a parameter of the (prepared) base model is only accepted when it provably cannot
        // decrease any edge weight, e.g. a decreased p_max_speed
        for (Map.Entry<String, Object> entry : queryModel.getParameters().entrySet()) {
            String name = entry.getKey();
            Object baseValue = baseModel.getParameters().get(name);
            if (baseValue == null)
                throw new IllegalArgumentException("parameter '" + name + "' is not defined in the server-side custom model");
            if (equalValues(baseValue, entry.getValue())) continue;
            if (!(baseValue instanceof Number oldValue) || !(entry.getValue() instanceof Number))
                throw cannotChange(name, baseValue, entry.getValue(), "");
            boolean increased = ((Number) entry.getValue()).doubleValue() > oldValue.doubleValue();
            checkWeightOnlyIncreases(baseModel, name, increased, baseModel.getParameters(), parameters, lookup);
        }

        checkMultiplyValue(queryModel.getPriority(), lookup, parameters);
        checkMultiplyValue(queryModel.getSpeed(), lookup, parameters);
    }

    private static boolean equalValues(Object a, Object b) {
        if (a instanceof Number na && b instanceof Number nb) return na.doubleValue() == nb.doubleValue();
        return a.equals(b);
    }

    private static IllegalArgumentException cannotChange(String name, Object oldValue, Object newValue, String reason) {
        return new IllegalArgumentException("CustomModel in query cannot change the parameter '" + name + "' from "
                + oldValue + " to " + newValue + (reason.isEmpty() ? "" : ": " + reason) + ". Use lm.disable=true");
    }

    /**
     * Throws an exception unless changing the specified parameter of the base model can only increase
     * (or keep) the weight of every edge, i.e. reduce access, lower the speed or the priority.
     */
    private static void checkWeightOnlyIncreases(CustomModel baseModel, String name, boolean increased,
                                                 Map<String, Object> oldParams, Map<String, Object> newParams,
                                                 EncodedValueLookup lookup) {
        // the name is already validated and contains no regex special characters
        Pattern pattern = Pattern.compile("\\b" + CustomModelParser.PARAM_PREFIX + name + "\\b");
        if (pattern.matcher(baseModel.getTurnPenalty().toString()).find())
            throw cannotChange(name, oldParams.get(name), newParams.get(name), "it is used in turn_penalty");
        checkStatements(baseModel.getSpeed(), pattern, name, increased, oldParams, newParams, lookup);
        checkStatements(baseModel.getPriority(), pattern, name, increased, oldParams, newParams, lookup);
    }

    private static void checkStatements(List<Statement> statements, Pattern pattern, String name, boolean increased,
                                        Map<String, Object> oldParams, Map<String, Object> newParams,
                                        EncodedValueLookup lookup) {
        Object oldValue = oldParams.get(name), newValue = newParams.get(name);
        for (Statement statement : statements) {
            if (statement.isBlock()) {
                if (pattern.matcher(statement.condition()).find())
                    throw cannotChange(name, oldValue, newValue, "it is used in the condition of a block statement");
                checkStatements(statement.doBlock(), pattern, name, increased, oldParams, newParams, lookup);
                continue;
            }
            // for speed and priority a smaller value means a larger weight, so the value must not increase
            if (pattern.matcher(statement.value()).find()) {
                // findMinMax evaluates an encoded value only at its endpoints and misses interior extremes of a non-monotone expression
                if (ValueExpressionVisitor.containsEncodedValue(statement.value(), lookup, newParams))
                    throw cannotChange(name, oldValue, newValue, "the value '" + statement.value() + "' uses an encoded value");
                if (ValueExpressionVisitor.findMinMax(statement.value(), lookup, newParams).max
                        > ValueExpressionVisitor.findMinMax(statement.value(), lookup, oldParams).min)
                    throw cannotChange(name, oldValue, newValue, "the value '" + statement.value() + "' could increase");
            }
            if (pattern.matcher(statement.condition()).find()) {
                Integer direction = conditionDirection(statement.condition(), CustomModelParser.PARAM_PREFIX + name);
                if (direction == null)
                    throw cannotChange(name, oldValue, newValue, "cannot analyze the condition '" + statement.condition() + "'");
                if (direction != 0) {
                    if (increased != (direction > 0))
                        throw cannotChange(name, oldValue, newValue, "the condition '" + statement.condition() + "' would apply to fewer edges");
                    // applying to more edges increases the weight no matter which branch they were in
                    // before, but only when the statement blocks them, i.e. sets speed or priority to 0
                    if (ValueExpressionVisitor.containsEncodedValue(statement.value(), lookup, newParams)
                            || ValueExpressionVisitor.findMinMax(statement.value(), lookup, newParams).max > 0
                            || ValueExpressionVisitor.findMinMax(statement.value(), lookup, oldParams).max > 0)
                        throw cannotChange(name, oldValue, newValue, "a parameter in a condition is only supported for blocking statements (value 0)");
                }
            }
        }
    }

    /**
     * @return +1 if the condition applies to more edges when the variable increases, -1 if it applies
     * to more edges when it decreases, 0 if it does not depend on the variable and null if unknown
     */
    private static Integer conditionDirection(String condition, String variable) {
        try {
            Parser parser = new Parser(new Scanner("ignore", new StringReader(condition)));
            Java.Atom atom = parser.parseConditionalExpression();
            if (parser.peek().type != TokenType.END_OF_INPUT) return null;
            return direction(atom.toRvalueOrCompileException(), variable);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Integer direction(Java.Rvalue rv, String variable) {
        if (rv instanceof Java.AmbiguousName name)
            return isVariable(name, variable) ? null : 0;
        if (rv instanceof Java.Literal) return 0;
        if (rv instanceof Java.ParenthesizedExpression pe) return direction(pe.value, variable);
        if (rv instanceof Java.UnaryOperation uo) {
            Integer dir = direction(uo.operand, variable);
            if (uo.operator.equals("!")) return dir == null ? null : -dir;
            return isZero(dir) ? 0 : null;
        }
        if (rv instanceof Java.BinaryOperation binOp) {
            Integer lhs = direction(binOp.lhs, variable), rhs = direction(binOp.rhs, variable);
            switch (binOp.operator) {
                case "&&", "||" -> {
                    if (isZero(lhs)) return rhs;
                    if (isZero(rhs)) return lhs;
                    return lhs != null && lhs.equals(rhs) ? lhs : null;
                }
                case "<", "<=", ">", ">=" -> {
                    boolean less = binOp.operator.startsWith("<");
                    // e.g. "p_weight < max_weight" applies to more edges when p_weight decreases
                    if (isVariable(binOp.lhs, variable) && isZero(rhs)) return less ? -1 : +1;
                    // e.g. "max_weight < p_weight" applies to more edges when p_weight increases
                    if (isVariable(binOp.rhs, variable) && isZero(lhs)) return less ? +1 : -1;
                    return isZero(lhs) && isZero(rhs) ? 0 : null;
                }
                default -> {
                    return isZero(lhs) && isZero(rhs) ? 0 : null;
                }
            }
        }
        // be conservative for anything else, e.g. a method invocation
        return null;
    }

    private static boolean isZero(Integer direction) {
        return direction != null && direction == 0;
    }

    private static boolean isVariable(Java.Atom atom, String variable) {
        return atom instanceof Java.AmbiguousName name && name.identifiers.length == 1 && name.identifiers[0].equals(variable);
    }

    private static void checkMultiplyValue(List<Statement> list, EncodedValueLookup lookup, Map<String, Object> parameters) {
        for (Statement statement : list) {
            if (statement.isBlock()) {
                checkMultiplyValue(statement.doBlock(), lookup);
            } else if (statement.operation() == Statement.Op.ADD) {
                // a non-negative add increases the speed and so decreases the weight
                throw new IllegalArgumentException("CustomModel in query must not use 'add'");
            } else if (statement.operation() == Statement.Op.MULTIPLY) {
                MinMax minMax = ValueExpressionVisitor.findMinMax(statement.value(), lookup);
                if (minMax.max > 1)
                    throw new IllegalArgumentException("maximum of value '" + statement.value() + "' cannot be larger than 1, but was: " + minMax.max);
                else if (minMax.min < 0)
                    throw new IllegalArgumentException("minimum of value '" + statement.value() + "' cannot be smaller than 0, but was: " + minMax.min);
            }
        }
    }

    /**
     * This method returns the smallest value possible in "min" and the smallest value that cannot be
     * exceeded by any edge in max.
     */
    static MinMax findMinMax(MinMax minMax, List<Statement> statements, EncodedValueLookup lookup, Map<String, Object> parameters) {
        List<List<Statement>> groups = CustomModelParser.splitIntoGroup(statements);
        for (List<Statement> group : groups) findMinMaxForGroup(minMax, group, lookup, parameters);
        return minMax;
    }

    private static void findMinMaxForGroup(final MinMax minMax, List<Statement> group, EncodedValueLookup lookup, Map<String, Object> parameters) {
        if (group.isEmpty() || !IF.equals(group.get(0).keyword()))
            throw new IllegalArgumentException("Every group must start with an if-statement");

        MinMax minMaxGroup;
        Statement first = group.get(0);
        if (first.condition().trim().equals("true")) {
            if(first.isBlock()) {
                for (List<Statement> subGroup : CustomModelParser.splitIntoGroup(first.doBlock())) findMinMaxForGroup(minMax, subGroup, lookup, parameters);
                return;
            } else {
                minMaxGroup = first.operation().apply(minMax, ValueExpressionVisitor.findMinMax(first.value(), lookup, parameters));
                if (minMaxGroup.max < 0)
                    throw new IllegalArgumentException("statement resulted in negative value: " + first);
            }
        } else {
            minMaxGroup = new MinMax(Double.MAX_VALUE, 0);
            boolean foundElse = false;
            for (Statement s : group) {
                if (s.keyword() == ELSE) foundElse = true;
                MinMax tmp;
                if(s.isBlock()) {
                    tmp = new MinMax(minMax.min, minMax.max);
                    for (List<Statement> subGroup : CustomModelParser.splitIntoGroup(s.doBlock())) findMinMaxForGroup(tmp, subGroup, lookup, parameters);
                } else {
                    tmp = s.operation().apply(minMax, ValueExpressionVisitor.findMinMax(s.value(), lookup, parameters));
                    if (tmp.max < 0)
                        throw new IllegalArgumentException("statement resulted in negative value: " + s);
                }
                minMaxGroup.min = Math.min(minMaxGroup.min, tmp.min);
                minMaxGroup.max = Math.max(minMaxGroup.max, tmp.max);
            }

            // if there is no 'else' statement it's like there is a 'neutral' branch that leaves the initial value as is
            if (!foundElse) {
                minMaxGroup.min = Math.min(minMaxGroup.min, minMax.min);
                minMaxGroup.max = Math.max(minMaxGroup.max, minMax.max);
            }
        }

        minMax.min = minMaxGroup.min;
        minMax.max = minMaxGroup.max;
    }
}
