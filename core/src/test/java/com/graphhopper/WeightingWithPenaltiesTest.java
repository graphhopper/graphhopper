package com.graphhopper;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.Random;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WeightingWithPenaltiesTest {
    final Random random = new Random();
    @Ignore
    @Test
    public void updateVisitedEdgesAndGetPenalty() {
        final FlagEncoder flagEncoder = mock(FlagEncoder.class);
        when(flagEncoder.isRegistered()).thenReturn(true);
        final WeightingWithPenalties weightingWithPenalties = new WeightingWithPenalties(flagEncoder, mock(HintsMap.class), Collections.EMPTY_LIST);

        int prevEdge = 0;
        for (int i = 0; i < Integer.MAX_VALUE; ++i) {
            final PointList pointList = new PointList();
            pointList.add(random.nextDouble(), random.nextDouble());
            final EdgeIteratorState edgeIteratorState = mock(EdgeIteratorState.class);
            when(edgeIteratorState.fetchWayGeometry(3)).thenReturn(pointList);
            final int current = random.nextInt();
            when(edgeIteratorState.getEdge()).thenReturn(current);
            weightingWithPenalties.updateVisitedEdgesAndGetPenalty(edgeIteratorState, false,prevEdge);
            prevEdge = current;
        }

    }
}