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

public class Statement {
    private final Keyword keyword;
    private final String condition;
    private final Op operation;
    private final double value;

    private Statement(Keyword keyword, String condition, Op operation, double value) {
        this.keyword = keyword;
        this.condition = condition;
        this.value = value;
        this.operation = operation;
    }

    public Keyword getKeyword() {
        return keyword;
    }

    public String getCondition() {
        return condition;
    }

    public Op getOperation() {
        return operation;
    }

    public double getValue() {
        return value;
    }

    public double apply(double externValue) {
        switch (operation) {
            case MULTIPLY:
                return value * externValue;
            case LIMIT:
                return Math.min(value, externValue);
            default:
                throw new IllegalArgumentException();
        }
    }

    public enum Keyword {
        IF("if"), ELSEIF("else_if"), ELSE("else");

        String name;

        Keyword(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public enum Op {
        MULTIPLY("multiply_by"), LIMIT("limit_to");

        String name;

        Op(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String build(double value) {
            switch (this) {
                case MULTIPLY:
                    return "value *= " + value;
                case LIMIT:
                    return "value = Math.min(value," + value + ")";
                default:
                    throw new IllegalArgumentException();
            }
        }
    }

    @Override
    public String toString() {
        return "{" + str(keyword.getName()) + ": " + str(condition) + ", " + str(operation.getName()) + ": " + value + "}";
    }

    private String str(String str) {
        return "\"" + str + "\"";
    }

    public static Statement If(String expression, Op op, double value) {
        return new Statement(Keyword.IF, expression, op, value);
    }

    public static Statement ElseIf(String expression, Op op, double value) {
        return new Statement(Keyword.ELSEIF, expression, op, value);
    }

    public static Statement Else(Op op, double value) {
        return new Statement(Keyword.ELSE, null, op, value);
    }
}
