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

public interface Statement {

    Keyword keyword();

    String condition();

    String value();

    List<Statement> doBlock();

    Op operation();

    boolean isBlock();

    enum Keyword {
        IF("if"), ELSEIF("else_if"), ELSE("else");

        private final String name;

        Keyword(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    enum Op {
        MULTIPLY("multiply_by"), LIMIT("limit_to"), DO("do");

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
                default:
                    throw new IllegalArgumentException();
            }
        }
    }

}
