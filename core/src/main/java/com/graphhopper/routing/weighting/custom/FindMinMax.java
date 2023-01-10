package com.graphhopper.routing.weighting.custom;

import com.graphhopper.json.MinMax;
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
        double qmDI = queryModel.hasDistanceInfluence() ? queryModel.getDistanceInfluence() : 0;
        double bmDI = baseModel.hasDistanceInfluence() ? baseModel.getDistanceInfluence() : 0;
        if (qmDI < bmDI)
            throw new IllegalArgumentException("CustomModel in query can only use " +
                    "distance_influence bigger or equal to " + bmDI + ", but was: " + qmDI);

        checkMultiplyValue(queryModel.getPriority(), lookup);
        checkMultiplyValue(queryModel.getSpeed(), lookup);
    }

    private static void checkMultiplyValue(List<Statement> list, EncodedValueLookup lookup) {
        Set<String> createdObjects = new HashSet<>();
        for (Statement statement : list) {
            if (statement.getOperation() == Statement.Op.MULTIPLY) {
                MinMax minMax = ValueExpressionVisitor.findMinMax(createdObjects, statement.getValue(), lookup);
                if (minMax.max > 1)
                    throw new IllegalArgumentException("maximum of value '" + statement.getValue() + "' cannot be larger than 1, but was: " + minMax.max);
                else if (minMax.min < 0)
                    throw new IllegalArgumentException("minimum of value '" + statement.getValue() + "' cannot be smaller than 0, but was: " + minMax.min);
            }
        }
    }

    /**
     * This method returns the smallest value possible in "min" and the smallest value that cannot be
     * exceeded by any edge in max.
     */
    static MinMax findMinMax(Set<String> createdObjects, MinMax minMax, List<Statement> statements, EncodedValueLookup lookup) {
        // 'blocks' of the statements are applied one after the other. A block consists of one (if) or more statements (elseif+else)
        List<List<Statement>> blocks = splitIntoBlocks(statements);
        for (List<Statement> block : blocks) findMinMaxForBlock(createdObjects, minMax, block, lookup);
        return minMax;
    }

    private static void findMinMaxForBlock(Set<String> createdObjects, final MinMax minMax, List<Statement> block, EncodedValueLookup lookup) {
        if (block.isEmpty() || !IF.equals(block.get(0).getKeyword()))
            throw new IllegalArgumentException("Every block must start with an if-statement");

        MinMax minMaxBlock;
        if (block.get(0).getCondition().trim().equals("true")) {
            minMaxBlock = block.get(0).getOperation().apply(minMax, ValueExpressionVisitor.findMinMax(createdObjects, block.get(0).getValue(), lookup));
        } else {
            minMaxBlock = new MinMax(Double.MAX_VALUE, 0);
            boolean foundElse = false;
            for (Statement s : block) {
                if (s.getKeyword() == ELSE) foundElse = true;
                MinMax tmp = s.getOperation().apply(minMax, ValueExpressionVisitor.findMinMax(createdObjects, s.getValue(), lookup));
                minMaxBlock.min = Math.min(minMaxBlock.min, tmp.min);
                minMaxBlock.max = Math.max(minMaxBlock.max, tmp.max);
            }

            // if there is no 'else' statement it's like there is a 'neutral' branch that leaves the initial value as is
            if (!foundElse) {
                minMaxBlock.min = Math.min(minMaxBlock.min, minMax.min);
                minMaxBlock.max = Math.max(minMaxBlock.max, minMax.max);
            }
        }

        minMax.min = minMaxBlock.min;
        minMax.max = minMaxBlock.max;
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
