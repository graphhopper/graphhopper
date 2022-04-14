package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.util.CustomModel;

import java.util.ArrayList;
import java.util.List;

import static com.graphhopper.json.Statement.Keyword.ELSE;
import static com.graphhopper.json.Statement.Keyword.IF;

public class FindMinMax {

    /**
     * This method throws an exception when this CustomModel would decrease the edge weight compared to the specified
     * baseModel as in such a case the optimality of A* with landmarks can no longer be guaranteed (as the preparation
     * is based on baseModel).
     */
    public static void checkLMConstraints(CustomModel baseModel, CustomModel queryModel, EncodedValueLookup lookup) {
        if (queryModel.isInternal())
            throw new IllegalArgumentException("CustomModel of query cannot be internal");
        if (queryModel.hasDistanceInfluence() && queryModel.getDistanceInfluence() < baseModel.getDistanceInfluence())
            throw new IllegalArgumentException("CustomModel in query can only use " +
                    "distance_influence bigger or equal to " + baseModel.getDistanceInfluence() +
                    ", given: " + queryModel.getDistanceInfluence());

        checkMultiplyValue(queryModel.getPriority(), lookup);
        double maxPrio = FindMinMax.findMax(queryModel.getPriority(), lookup, 1, "priority");
        if (maxPrio > 1)
            throw new IllegalArgumentException("priority of CustomModel in query cannot be bigger than 1. Was: " + maxPrio);

        checkMultiplyValue(queryModel.getSpeed(), lookup);
    }

    private static void checkMultiplyValue(List<Statement> list, EncodedValueLookup lookup) {
        for (Statement statement : list) {
            // TODO NOW allow factor > 1 if last limits it to 1 again -> maybe just use findMax(queryModel.getSpeed(), 200) with a high speed?
            if (statement.getOperation() == Statement.Op.MULTIPLY && ValueExpressionVisitor.findMinMax(statement.getValue(), lookup)[1] > 1)
                throw new IllegalArgumentException("factor cannot be larger than 1 but was " + statement.getValue());
        }
    }

    static double findMax(List<Statement> statements, EncodedValueLookup lookup, double max, String type) {
        // we want to find the smallest value that cannot be exceeded by any edge. the 'blocks' of speed statements
        // are applied one after the other.
        List<List<Statement>> blocks = splitIntoBlocks(statements);
        for (List<Statement> block : blocks) max = findMaxForBlock(block, lookup, max);
        if (max <= 0) throw new IllegalArgumentException(type + " cannot be negative or 0 (was " + max + ")");
        return max;
    }

    static double findMaxForBlock(List<Statement> block, EncodedValueLookup lookup, final double max) {
        if (block.isEmpty() || !IF.equals(block.get(0).getKeyword()))
            throw new IllegalArgumentException("Every block must start with an if-statement");
        if (block.get(0).getCondition().trim().equals("true"))
            return block.get(0).getOperation().apply(max, ValueExpressionVisitor.findMinMax(block.get(0).getValue(), lookup)[1]);

        double blockMax = block.stream()
                .mapToDouble(statement -> statement.getOperation().apply(max, ValueExpressionVisitor.findMinMax(statement.getValue(), lookup)[1]))
                .max()
                .orElse(max);
        // if there is no 'else' statement it's like there is a 'neutral' branch that leaves the initial value as is
        if (block.stream().noneMatch(st -> ELSE.equals(st.getKeyword())))
            blockMax = Math.max(blockMax, max);
        return blockMax;
    }

    /**
     * Splits the specified list into several list of statements starting with if
     */
    static List<List<Statement>> splitIntoBlocks(List<Statement> statements) {
        List<List<Statement>> result = new ArrayList<>();
        List<Statement> block = null;
        for (Statement st : statements) {
            if (IF.equals(st.getKeyword())) result.add(block = new ArrayList<>());
            if (block == null) throw new IllegalArgumentException("Every block must start with an if-statement");
            block.add(st);
        }
        return result;
    }
}
