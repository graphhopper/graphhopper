package com.graphhopper.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.graphhopper.json.Clause;

import java.io.IOException;
import java.util.Arrays;

public class ClauseDeserializer extends JsonDeserializer<Clause> {
    @Override
    public Clause deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode treeNode = p.readValueAsTree();
        Clause.Op jsonOp = null;
        double value = Double.NaN;
        for (Clause.Op op : Clause.Op.values()) {
            if (treeNode.has(op.getName()) && treeNode.get(op.getName()).isNumber()) {
                jsonOp = op;
                value = treeNode.get(op.getName()).asDouble();
                break;
            }
        }
        if (jsonOp == null)
            throw new IllegalArgumentException("Cannot find operation. Must be one of " + Arrays.toString(Clause.Op.values()));
        if (Double.isNaN(value))
            throw new IllegalArgumentException("Value of operation " + jsonOp.getName() + " is not a number");

        if (treeNode.has("if"))
            return Clause.If(treeNode.get("if").asText(), jsonOp, value);
        else if (treeNode.has("else if"))
            return Clause.ElseIf(treeNode.get("else if").asText(), jsonOp, value);
        else if (treeNode.has("else"))
            return Clause.Else(jsonOp, value);

        throw new IllegalArgumentException("Cannot find if, else if or else");
    }
}
