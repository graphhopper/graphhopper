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
package com.graphhopper.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.json.Statement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.graphhopper.json.Statement.*;
import static com.graphhopper.json.Statement.Keyword.*;
import static com.graphhopper.json.Statement.Op.DO;

class StatementDeserializer extends JsonDeserializer<Statement> {

    @Override
    public Statement deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return deserializeStatement(p.readValueAsTree());
    }

    static Statement deserializeStatement(JsonNode treeNode) {
        if (treeNode.has(DO.getName())) {
            if (treeNode.size() != 2)
                throw new IllegalArgumentException("This block statement expects two entries but was " + treeNode.size() + " for " + treeNode);

            JsonNode doNode = treeNode.get(DO.getName());
            if (!doNode.isArray())
                throw new IllegalArgumentException("'do' block must be an array");
            List<Statement> list = new ArrayList<>();
            for (JsonNode thenSt : doNode) {
                list.add(deserializeStatement(thenSt));
            }

            if (treeNode.has(IF.getName()))
                return If(treeNode.get(IF.getName()).asText(), list);
            else if (treeNode.has(ELSEIF.getName()))
                return ElseIf(treeNode.get(ELSEIF.getName()).asText(), list);
            else if (treeNode.has(ELSE.getName())) {
                JsonNode elseNode = treeNode.get(ELSE.getName());
                if (elseNode.isNull() || elseNode.isValueNode() && elseNode.asText().isEmpty())
                    return Else(list);
                throw new IllegalArgumentException("else cannot have expression but was " + treeNode.get(ELSE.getName()));
            } else
                throw new IllegalArgumentException("invalid then block: " + treeNode.toPrettyString());

        } else {
            if (treeNode.size() != 2)
                throw new IllegalArgumentException("This statement expects two entries but was " + treeNode.size() + " for " + treeNode);
            Statement.Op jsonOp = null;
            String value = null;
            for (Statement.Op op : Statement.Op.values()) {
                if (treeNode.has(op.getName())) {
                    if (jsonOp != null)
                        throw new IllegalArgumentException("Multiple operations are not allowed. Statement: " + treeNode);
                    jsonOp = op;
                    value = treeNode.get(op.getName()).asText();
                }
            }

            if (jsonOp == null)
                throw new IllegalArgumentException("Cannot find an operation in " + treeNode + ". Must be one of: " + Arrays.stream(Statement.Op.values()).map(Statement.Op::getName).collect(Collectors.joining(",")));
            if (value == null)
                throw new IllegalArgumentException("Cannot find a value in " + treeNode);

            if (treeNode.has(IF.getName()))
                return If(treeNode.get(IF.getName()).asText(), jsonOp, value);
            else if (treeNode.has(ELSEIF.getName()))
                return ElseIf(treeNode.get(ELSEIF.getName()).asText(), jsonOp, value);
            else if (treeNode.has(ELSE.getName())) {
                JsonNode elseNode = treeNode.get(ELSE.getName());
                if (elseNode.isNull() || elseNode.isValueNode() && elseNode.asText().isEmpty())
                    return Else(jsonOp, value);
                throw new IllegalArgumentException("else cannot have expression but was " + treeNode.get(ELSE.getName()));
            }
        }
        throw new IllegalArgumentException("Cannot find if, else_if or else for " + treeNode);
    }
}
