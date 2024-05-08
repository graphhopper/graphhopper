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

import java.util.List;

public record SingleStatement(Keyword keyword, String condition, Op operation,
                              String value) implements Statement {

    @Override
    public List<Statement> then() {
        throw new UnsupportedOperationException("Not supported for single statement.");
    }

    @Override
    public boolean isBlock() {
        return false;
    }

    @Override
    public String toString() {
        return "{" + str(keyword.getName()) + ": " + str(condition) + ", " + str(operation.getName()) + ": " + value + "}";
    }

    private String str(String str) {
        return "\"" + str + "\"";
    }

    public static SingleStatement If(String expression, Op op, String value) {
        return new SingleStatement(Keyword.IF, expression, op, value);
    }

    public static SingleStatement ElseIf(String expression, Op op, String value) {
        return new SingleStatement(Keyword.ELSEIF, expression, op, value);
    }

    public static SingleStatement Else(Op op, String value) {
        return new SingleStatement(Keyword.ELSE, null, op, value);
    }
}
