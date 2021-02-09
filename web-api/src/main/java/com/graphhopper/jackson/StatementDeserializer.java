package com.graphhopper.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.json.Statement;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

class StatementDeserializer extends JsonDeserializer<Statement> {
    @Override
    public Statement deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode treeNode = p.readValueAsTree();
        Statement.Op jsonOp = null;
        double value = Double.NaN;
        if (treeNode.size() != 2)
            throw new IllegalArgumentException("Statement expects two entries but was " + treeNode.size() + " for " + treeNode);

        for (Statement.Op op : Statement.Op.values()) {
            if (treeNode.has(op.getName())) {
                if (!treeNode.get(op.getName()).isNumber())
                    throw new IllegalArgumentException("Operations " + op.getName() + " expects a number but was " + treeNode.get(op.getName()));
                if (jsonOp != null)
                    throw new IllegalArgumentException("Multiple operations are not allowed. Statement: " + treeNode);
                jsonOp = op;
                value = treeNode.get(op.getName()).asDouble();
            }
        }
        if (jsonOp == null)
            throw new IllegalArgumentException("Cannot find an operation in " + treeNode + ". Must be one of: " + Arrays.stream(Statement.Op.values()).map(Statement.Op::getName).collect(Collectors.joining(",")));
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
