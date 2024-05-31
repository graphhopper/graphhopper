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
import java.util.Objects;
import java.util.stream.Collectors;

public record BlockStatement(Keyword keyword, String condition,
                             List<Statement> doBlock) implements Statement {

    @Override
    public boolean isBlock() {
        return true;
    }

    @Override
    public Op operation() {
        return Op.DO;
    }

    @Override
    public String value() {
        throw new UnsupportedOperationException("Not supported for block statement.");
    }

    public String toPrettyString() {
        return "{\"" + keyword.getName() + "\": \"" + condition + "\",\n"
                + "  \"do\": [\n"
                + doBlock.stream().map(Objects::toString).collect(Collectors.joining(",\n  "))
                + "  ]\n" +
                "}";
    }

    @Override
    public String toString() {
        return "{\"" + keyword.getName() + "\": \"" + condition + "\", \"do\": " + doBlock + " }";
    }

    public static BlockStatement If(String expression, List<Statement> doBlock) {
        return new BlockStatement(Keyword.IF, expression, doBlock);
    }

    public static BlockStatement ElseIf(String expression, List<Statement> doBlock) {
        return new BlockStatement(Keyword.ELSEIF, expression, doBlock);
    }

    public static BlockStatement Else(List<Statement> doBlock) {
        return new BlockStatement(Keyword.ELSE, "", doBlock);
    }
}
