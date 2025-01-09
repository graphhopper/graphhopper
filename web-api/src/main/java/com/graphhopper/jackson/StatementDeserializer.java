package com.graphhopper.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.json.Statement;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

import static com.graphhopper.json.Statement.Keyword.*;

public class StatementDeserializer extends JsonDeserializer<Statement> {
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

        if (treeNode.has(IF.getName()))
            return Statement.If(treeNode.get(IF.getName()).asText(), jsonOp, value);
        else if (treeNode.has(ELSEIF.getName()))
            return Statement.ElseIf(treeNode.get(ELSEIF.getName()).asText(), jsonOp, value);
        else if (treeNode.has(ELSE.getName())) {
            JsonNode elseNode = treeNode.get(ELSE.getName());
            if (elseNode.isNull() || elseNode.isValueNode() && elseNode.asText().isEmpty())
                return Statement.Else(jsonOp, value);
            throw new IllegalArgumentException("else cannot have expression but was " + treeNode.get(ELSE.getName()));
        }

        throw new IllegalArgumentException("Cannot find if, else_if or else for " + treeNode.toString());
    }
}
