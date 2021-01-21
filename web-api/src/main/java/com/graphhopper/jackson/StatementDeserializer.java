package com.graphhopper.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.json.Statement;

import java.io.IOException;
import java.util.Arrays;

public class StatementDeserializer extends JsonDeserializer<Statement> {
    @Override
    public Statement deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode treeNode = p.readValueAsTree();
        Statement.Op jsonOp = null;
        double value = Double.NaN;
        for (Statement.Op op : Statement.Op.values()) {
            if (treeNode.has(op.getName()) && treeNode.get(op.getName()).isNumber()) {
                jsonOp = op;
                value = treeNode.get(op.getName()).asDouble();
                break;
            }
        }
        if (jsonOp == null)
            throw new IllegalArgumentException("Cannot find operation. Must be one of " + Arrays.toString(Statement.Op.values()));
        if (Double.isNaN(value))
            throw new IllegalArgumentException("Value of operation " + jsonOp.getName() + " is not a number");

        if (treeNode.has("if"))
            return Statement.If(treeNode.get("if").asText(), jsonOp, value);
        else if (treeNode.has("else if"))
            return Statement.ElseIf(treeNode.get("else if").asText(), jsonOp, value);
        else if (treeNode.has("else")) {
            if (!treeNode.get("else").isNull())
                throw new IllegalArgumentException("else cannot have expression but was " + treeNode.get("else"));
            return Statement.Else(jsonOp, value);
        }

        throw new IllegalArgumentException("Cannot find if, else if or else for " + treeNode.toString());
    }
}
