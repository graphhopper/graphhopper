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

public class Clause {
    private final Cond condition;
    private final String expression;
    private final Op operation;
    private final double value;

    public Clause(Cond condition, String expression, Op operation, double value) {
        this.condition = condition;
        this.expression = expression;
        this.value = value;
        this.operation = operation;
    }

    public Cond getCondition() {
        return condition;
    }

    public String getExpression() {
        return expression;
    }

    public Op getOperation() {
        return operation;
    }

    public double getValue() {
        return value;
    }

    public enum Cond {
        IF, ELSEIF, ELSE
    }

    public enum Op {
        MULT("multiply with"), LIMIT("limit to");

        String name;

        Op(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static Clause If(String expression, Op op, double thenValue) {
        return new Clause(Cond.IF, expression, op, thenValue);
    }

    public static Clause ElseIf(String expression, Op op, double thenValue) {
        return new Clause(Cond.ELSEIF, expression, op, thenValue);
    }

    public static Clause Else(Op op, double elseValue) {
        return new Clause(Cond.ELSE, null, op, elseValue);
    }
}
