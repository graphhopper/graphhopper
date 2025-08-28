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
package com.graphhopper.json;

import com.graphhopper.util.Helper;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record Statement(Keyword keyword, String condition, Op operation, String value,
                        List<Statement> doBlock) {

    public Statement {
        if (condition == null)
            throw new IllegalArgumentException("'condition' cannot be null");
        if (doBlock != null && operation != Op.DO)
            throw new IllegalArgumentException("For 'doBlock' you have to use Op.DO");
        if (doBlock != null && value != null)
            throw new IllegalArgumentException("'doBlock' or 'value' cannot be both non-null");
        if (doBlock == null && Helper.isEmpty(value))
            throw new IllegalArgumentException("a leaf statement must have a non-empty 'value'");
        if (condition.isEmpty() && keyword != Keyword.ELSE)
            throw new IllegalArgumentException("All statements (except 'else') have to use a non-empty 'condition'");
        if (!condition.isEmpty() && keyword == Keyword.ELSE)
            throw new IllegalArgumentException("For the 'else' statement you have to use an empty 'condition'");
    }

    public boolean isBlock() {
        return doBlock != null;
    }

    @Override
    public String value() {
        if (isBlock())
            throw new UnsupportedOperationException("'value' is not supported for block statement.");
        return value;
    }

    @Override
    public List<Statement> doBlock() {
        if (!isBlock())
            throw new UnsupportedOperationException("'doBlock' is not supported for leaf statement.");
        return doBlock;
    }

    public String toPrettyString() {
        if (isBlock())
            return "{\"" + keyword.getName() + "\": \"" + condition + "\",\n"
                    + "  \"do\": [\n"
                    + doBlock.stream().map(Objects::toString).collect(Collectors.joining(",\n  "))
                    + "  ]\n" +
                    "}";
        else return toString();
    }

    @Override
    public String toString() {
        if (isBlock())
            return "{\"" + keyword.getName() + "\": \"" + condition + "\", \"do\": " + doBlock + " }";
        else
            return "{\"" + keyword.getName() + "\": \"" + condition + "\", \"" + operation.getName() + "\": \"" + value + "\"}";
    }

    public enum Keyword {
        IF("if"), ELSEIF("else_if"), ELSE("else");

        private final String name;

        Keyword(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public enum Op {
        MULTIPLY("multiply_by"), LIMIT("limit_to"), DO("do"), ADD("add");

        private final String name;

        Op(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String build(String value) {
            switch (this) {
                case MULTIPLY:
                    return "value *= " + value;
                case LIMIT:
                    return "value = Math.min(value," + value + ")";
                case ADD:
                    return "value += " + (value.equals("Infinity") ? "Double.POSITIVE_INFINITY" : value);
                default:
                    throw new IllegalArgumentException();
            }
        }

        public MinMax apply(MinMax minMax1, MinMax minMax2) {
            switch (this) {
                case MULTIPLY:
                    return new MinMax(minMax1.min * minMax2.min, minMax1.max * minMax2.max);
                case LIMIT:
                    return new MinMax(Math.min(minMax1.min, minMax2.min), Math.min(minMax1.max, minMax2.max));
                case ADD:
                    return new MinMax(minMax1.min + minMax2.min, minMax1.max + minMax2.max);
                default:
                    throw new IllegalArgumentException();
            }
        }
    }

    public static Statement If(String expression, List<Statement> doBlock) {
        return new Statement(Keyword.IF, expression, Op.DO, null, doBlock);
    }

    public static Statement If(String expression, Op op, String value) {
        return new Statement(Keyword.IF, expression, op, value, null);
    }

    public static Statement ElseIf(String expression, List<Statement> doBlock) {
        return new Statement(Keyword.ELSEIF, expression, Op.DO, null, doBlock);
    }

    public static Statement ElseIf(String expression, Op op, String value) {
        return new Statement(Keyword.ELSEIF, expression, op, value, null);
    }

    public static Statement Else(List<Statement> doBlock) {
        return new Statement(Keyword.ELSE, "", Op.DO, null, doBlock);
    }

    public static Statement Else(Op op, String value) {
        return new Statement(Keyword.ELSE, "", op, value, null);
    }
}
