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
    // either if or else-if with thenValue
    private String ifClause;
    private String elseIfClause;
    private Double thenValue;
    // or just elseValue
    private Double elseValue;

    // for JSON
    public Clause() {
    }

    private Clause(String ifClause, String elseIfClause, Double elseValue, Double thenValue) {
        this.ifClause = ifClause;
        this.elseIfClause = elseIfClause;
        this.elseValue = elseValue;
        this.thenValue = thenValue;
    }

    public void setIf(String ifClause) {
        this.ifClause = ifClause;
    }

    public String getIf() {
        return ifClause;
    }

    public void setElseIf(String elseIfClause) {
        this.elseIfClause = elseIfClause;
    }

    public String getElseIf() {
        return elseIfClause;
    }

    public void setElse(Double elseValue) {
        this.elseValue = elseValue;
    }

    public Double getElse() {
        return elseValue;
    }

    public void setThen(Double thenValue) {
        this.thenValue = thenValue;
    }

    public Double getThen() {
        return thenValue;
    }

    public static Clause If(String clause, double thenValue) {
        return new Clause(clause, null, null, thenValue);
    }

    public static Clause ElseIf(String clause, double thenValue) {
        return new Clause(null, clause, null, thenValue);
    }

    public static Clause Else(double elseValue) {
        return new Clause(null, null, elseValue, null);
    }
}
