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
                    ", but was: " + queryModel.getDistanceInfluence());

        checkMultiplyValue(queryModel.getPriority(), lookup);
        checkMultiplyValue(queryModel.getSpeed(), lookup);
    }

    private static void checkMultiplyValue(List<Statement> list, EncodedValueLookup lookup) {
        Set<String> createdObjects = new HashSet<>();
        for (Statement statement : list) {
            if (statement.getOperation() == Statement.Op.MULTIPLY) {
                double[] minMax = ValueExpressionVisitor.findMinMax(createdObjects, statement.getValue(), lookup);
                if (minMax[1] > 1)
                    throw new IllegalArgumentException("maximum of value '" + statement.getValue() + "'cannot be larger than 1, but was: " + minMax[1]);
                else if (minMax[0] < 0)
                    throw new IllegalArgumentException("minimum of value '" + statement.getValue() + "' cannot be smaller than 0, but was: " + minMax[0]);
            }
        }
    }

    /**
     * This method returns the smallest value possible in minMax[0] ("minimum") and the smallest value that cannot be
     * exceeded by any edge in minMax[1] ("maximum").
     */
    static double[] findMinMax(Set<String> createdObjects, double[] minMax, List<Statement> statements, EncodedValueLookup lookup) {
        // 'blocks' of the statements are applied one after the other. A block consists of one (if) or more statements (elseif+else)
        List<List<Statement>> blocks = splitIntoBlocks(statements);
        for (List<Statement> block : blocks) findMinMaxForBlock(createdObjects, minMax, block, lookup);
        return minMax;
    }

    private static void findMinMaxForBlock(Set<String> createdObjects, final double[] minMax, List<Statement> block, EncodedValueLookup lookup) {
        if (block.isEmpty() || !IF.equals(block.get(0).getKeyword()))
            throw new IllegalArgumentException("Every block must start with an if-statement");

        double[] minMaxBlock;
        if (block.get(0).getCondition().trim().equals("true")) {
            minMaxBlock = block.get(0).getOperation().apply(minMax, ValueExpressionVisitor.findMinMax(createdObjects, block.get(0).getValue(), lookup));
        } else {
            minMaxBlock = new double[]{Double.MAX_VALUE, 0};
            boolean foundElse = false;
            for (Statement s : block) {
                if (s.getKeyword() == ELSE) foundElse = true;
                double[] tmp = s.getOperation().apply(minMax, ValueExpressionVisitor.findMinMax(createdObjects, s.getValue(), lookup));
                minMaxBlock[0] = Math.min(minMaxBlock[0], tmp[0]);
                minMaxBlock[1] = Math.max(minMaxBlock[1], tmp[1]);
            }

            // if there is no 'else' statement it's like there is a 'neutral' branch that leaves the initial value as is
            if (!foundElse) {
                minMaxBlock[0] = Math.min(minMaxBlock[0], minMax[0]);
                minMaxBlock[1] = Math.max(minMaxBlock[1], minMax[1]);
            }
        }

        minMax[0] = minMaxBlock[0];
        minMax[1] = minMaxBlock[1];
    }

    /**
     * Splits the specified list into several list of statements starting with if
     */
    private static List<List<Statement>> splitIntoBlocks(List<Statement> statements) {
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
