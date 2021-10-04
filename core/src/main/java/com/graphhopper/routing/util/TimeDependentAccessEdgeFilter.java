package com.graphhopper.routing.util;

import ch.poole.conditionalrestrictionparser.Condition;
import ch.poole.conditionalrestrictionparser.ConditionalRestrictionParser;
import ch.poole.conditionalrestrictionparser.Restriction;
import com.graphhopper.routing.querygraph.EdgeKeys;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.storage.ConditionalEdges;
import com.graphhopper.util.DateTimeHelper;
import com.graphhopper.storage.ConditionalEdgesMap;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;

import java.io.ByteArrayInputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Filter out temporarily blocked edges
 *
 * @author Andrzej Oles
 */
public class TimeDependentAccessEdgeFilter implements TimeDependentEdgeFilter {
    private final BooleanEncodedValue conditionalEnc;
    private final ConditionalEdgesMap conditionalEdges;
    private final boolean fwd;
    private final boolean bwd;
    private final DateTimeHelper dateTimeHelper;

    public TimeDependentAccessEdgeFilter(GraphHopperStorage graph, FlagEncoder encoder) {
        this(graph, encoder.toString());
    }

    public TimeDependentAccessEdgeFilter(GraphHopperStorage graph, String encoderName) {
        this(graph, encoderName, true, true);
    }

    TimeDependentAccessEdgeFilter(GraphHopperStorage graph, String encoderName, boolean fwd, boolean bwd) {
        EncodingManager encodingManager = graph.getEncodingManager();
        conditionalEnc = encodingManager.getBooleanEncodedValue(EncodingManager.getKey(encoderName, ConditionalEdges.ACCESS));
        conditionalEdges = graph.getConditionalAccess(encoderName);
        this.fwd = fwd;
        this.bwd = bwd;
        this.dateTimeHelper = new DateTimeHelper(graph);
    }

    @Override
    public final boolean accept(EdgeIteratorState iter, long time) {
        if (fwd && iter.get(conditionalEnc) || bwd && iter.getReverse(conditionalEnc)) {
            int edgeId = EdgeKeys.getOriginalEdge(iter);
            // for now the filter is used only in the context of fwd search so only edges going out of the base node are explored
            ZonedDateTime zonedDateTime = (time == -1) ? null : dateTimeHelper.getZonedDateTime(iter, time);
            String value = conditionalEdges.getValue(edgeId);
            return accept(value, zonedDateTime);
        }
        return true;
    }

    boolean accept(String conditional, ZonedDateTime zonedDateTime) {
        boolean matchValue = false;

        try {
            ConditionalRestrictionParser crparser = new ConditionalRestrictionParser(new ByteArrayInputStream(conditional.getBytes()));

            List<Restriction> restrictions = crparser.restrictions();

            // iterate over restrictions starting from the last one in order to match to the most specific one
            for (int i = restrictions.size() - 1 ; i >= 0; i--) {
                Restriction restriction = restrictions.get(i);

                matchValue = "yes".equals(restriction.getValue());

                List<Condition> conditions = restriction.getConditions();

                // stop as soon as time matches the combined conditions
                if (TimeDependentConditionalEvaluator.match(conditions, zonedDateTime))
                    return matchValue;
            }

            // no restrictions with matching conditions found
            return !matchValue;

        } catch (ch.poole.conditionalrestrictionparser.ParseException e) {
            //nop
        }

        return false;
    }

    @Override
    public String toString() {
        return conditionalEnc + ", bwd:" + bwd + ", fwd:" + fwd;
    }
}
