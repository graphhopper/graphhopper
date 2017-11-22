package com.graphhopper.routing;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.Lane;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Tests the InstructionsFromEdges class.
 */
@RunWith(MockitoJUnitRunner.class)
public class InstructionsFromEdgesTest {

    @Mock
    private NodeAccess nodeAccess;
    @Mock
    private Graph graph;
    @Mock
    private CarFlagEncoder encoder;

    private InstructionsFromEdges instructionsFromEdges;

    private List<Lane> lanes;
    private long flags = 1;

    /**
     *  Initialize the class to test and setup the default mock actions.
     */
    @Before
    public void setup() {
        lanes = new ArrayList<>();
        when(encoder.isLaneInfoEnabled()).thenReturn(true);
        when(encoder.getDouble(anyLong(), anyInt())).thenReturn(1.0);
        when(encoder.decodeTurnLanesToList(anyLong())).thenReturn(lanes);
        instructionsFromEdges = new InstructionsFromEdges(0, graph, null, encoder, nodeAccess, null, null);
    }

    @Test
    public void getLanesInfoSingleLaneNone() throws Exception {
        int sign = Instruction.CONTINUE_ON_STREET;
        lanes.add(new Lane("none", CarFlagEncoder.NONE_LANE_CODE));

        List<Lane> lanesInfo = instructionsFromEdges.getLanesInfo(flags, sign);

        assertNotNull(lanesInfo);
        assertFalse(lanesInfo.isEmpty());
        assertEquals("none", lanesInfo.get(0).getDirection());
        assertTrue(lanesInfo.get(0).isValid());
    }

    @Test
    public void getLanesInfoSingleLaneRight() throws Exception {
        int sign = Instruction.CONTINUE_ON_STREET;
        lanes.add(new Lane("right", CarFlagEncoder.RIGHT_LANE_CODE));

        List<Lane> lanesInfo = instructionsFromEdges.getLanesInfo(flags, sign);

        assertNotNull(lanesInfo);
        assertFalse(lanesInfo.isEmpty());
        assertEquals("right", lanesInfo.get(0).getDirection());
        assertTrue(lanesInfo.get(0).isValid());
    }

    @Test
    public void getLanesInfoMultipleLanesRight() throws Exception {
        int sign = Instruction.TURN_RIGHT;
        lanes.add(new Lane("none", CarFlagEncoder.NONE_LANE_CODE));
        lanes.add(new Lane("right", CarFlagEncoder.RIGHT_LANE_CODE));

        List<Lane> lanesInfo = instructionsFromEdges.getLanesInfo(flags, sign);

        assertNotNull(lanesInfo);
        assertFalse(lanesInfo.isEmpty());
        assertEquals("none", lanesInfo.get(0).getDirection());
        assertFalse(lanesInfo.get(0).isValid());
        assertEquals("right", lanesInfo.get(1).getDirection());
        assertTrue(lanesInfo.get(1).isValid());
    }

    @Test
    public void getLanesInfoMultipleLanesThrough() throws Exception {
        int sign = Instruction.CONTINUE_ON_STREET;
        lanes.add(new Lane("left", CarFlagEncoder.LEFT_LANE_CODE));
        lanes.add(new Lane("none", CarFlagEncoder.NONE_LANE_CODE));
        lanes.add(new Lane("none", CarFlagEncoder.NONE_LANE_CODE));
        lanes.add(new Lane("none", CarFlagEncoder.NONE_LANE_CODE));
        lanes.add(new Lane("right", CarFlagEncoder.RIGHT_LANE_CODE));

        List<Lane> lanesInfo = instructionsFromEdges.getLanesInfo(flags, sign);

        assertNotNull(lanesInfo);
        assertFalse(lanesInfo.isEmpty());
        assertEquals("left", lanesInfo.get(0).getDirection());
        assertFalse(lanesInfo.get(0).isValid());
        assertEquals("none", lanesInfo.get(1).getDirection());
        assertTrue(lanesInfo.get(1).isValid());
        assertEquals("none", lanesInfo.get(2).getDirection());
        assertTrue(lanesInfo.get(2).isValid());
        assertEquals("none", lanesInfo.get(3).getDirection());
        assertTrue(lanesInfo.get(3).isValid());
        assertEquals("right", lanesInfo.get(4).getDirection());
        assertFalse(lanesInfo.get(4).isValid());
    }

    @Test
    public void getLanesInfoMultipleDirectionsRight() throws Exception {
        int sign = Instruction.TURN_RIGHT;
        lanes.add(new Lane("through;right", 6));

        List<Lane> lanesInfo = instructionsFromEdges.getLanesInfo(flags, sign);

        assertNotNull(lanesInfo);
        assertFalse(lanesInfo.isEmpty());
        assertEquals("through;right", lanesInfo.get(0).getDirection());
        assertTrue(lanesInfo.get(0).isValid());
    }

    @Test
    public void getLanesInfoMultipleDirectionsThrough() throws Exception {
        int sign = Instruction.CONTINUE_ON_STREET;
        lanes.add(new Lane("left;through;right", 15));
        lanes.add(new Lane("left", CarFlagEncoder.LEFT_LANE_CODE));

        List<Lane> lanesInfo = instructionsFromEdges.getLanesInfo(flags, sign);

        assertNotNull(lanesInfo);
        assertFalse(lanesInfo.isEmpty());
        assertTrue(lanesInfo.get(0).isValid());
        assertFalse(lanesInfo.get(1).isValid());
    }
}