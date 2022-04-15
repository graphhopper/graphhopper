package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.util.CustomModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        checkMultiplyValue(queryModel.getSpeed(), lookup);
    }

    private static void checkMultiplyValue(List<Statement> list, EncodedValueLookup lookup) {
        Set<String> createdObjects = new HashSet<>();
        for (Statement statement : list) {
            if (statement.getOperation() == Statement.Op.MULTIPLY) {
                double[] minMax = ValueExpressionVisitor.findMinMax(createdObjects, statement.getValue(), lookup);
                if (minMax[1] > 1)
                    throw new IllegalArgumentException("maximum of value '" + statement.getValue() + "'cannot be larger than 1 but was " + minMax[1]);
                else if (minMax[0] < 0)
                    throw new IllegalArgumentException("minimum of value '" + statement.getValue() + "' cannot be smaller than 0 but was " + minMax[0]);
            }
        }
    }

    static double findMax(Set<String> createdObjects, List<Statement> statements, EncodedValueLookup lookup, double max, String type) {
        // we want to find the smallest value that cannot be exceeded by any edge. the 'blocks' of speed statements
        // are applied one after the other.
        List<List<Statement>> blocks = splitIntoBlocks(statements);
        for (List<Statement> block : blocks) max = findMaxForBlock(createdObjects, block, lookup, max);
        if (max <= 0) throw new IllegalArgumentException(type + " cannot be negative or 0 (was " + max + ")");
        return max;
    }

    static double findMaxForBlock(Set<String> createdObjects, List<Statement> block, EncodedValueLookup lookup, final double max) {
        if (block.isEmpty() || !IF.equals(block.get(0).getKeyword()))
            throw new IllegalArgumentException("Every block must start with an if-statement");
        if (block.get(0).getCondition().trim().equals("true"))
            return block.get(0).getOperation().apply(max, ValueExpressionVisitor.findMinMax(createdObjects, block.get(0).getValue(), lookup)[1]);

        double blockMax = block.stream()
                .mapToDouble(statement -> statement.getOperation().apply(max, ValueExpressionVisitor.findMinMax(createdObjects,
                        statement.getValue(), lookup)[1]))
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
