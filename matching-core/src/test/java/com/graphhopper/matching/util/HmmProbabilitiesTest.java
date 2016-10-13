package com.graphhopper.matching.util;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class HmmProbabilitiesTest {

    @Test
    public void testTransitionLogProbability() {
        HmmProbabilities instance = new HmmProbabilities();
        // see #13 for the real world problem
        assertEquals(0, instance.transitionLogProbability(1, 1, 0), 0.001);
    }
}
