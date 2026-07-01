package com.graphhopper.util;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static com.graphhopper.util.Parameters.Details.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for roundabout exit turn description fallback cascade.
 * Verifies fixes for #3213 (destination tag priority) and #3215 (name leak on unnamed exits).
 */
public class RoundaboutInstructionTest {
    private final Translation tr = new TranslationMap().doImport().getWithFallBack(Locale.US);

    private RoundaboutInstruction createExitedRoundabout(String name) {
        RoundaboutInstruction ri = new RoundaboutInstruction(Instruction.USE_ROUNDABOUT, name, new PointList());
        ri.setExited();
        ri.increaseExitNumber();
        return ri;
    }

    @Test
    public void testExitOntoNamedStreet() {
        RoundaboutInstruction ri = createExitedRoundabout("Main Street");
        assertEquals("at roundabout, take exit 1 onto Main Street", ri.getTurnDescription(tr));
    }

    @Test
    public void testExitOntoUnnamedStreet_noLeak() {
        // Issue #3215: unnamed exit should NOT inherit roundabout name
        RoundaboutInstruction ri = createExitedRoundabout("");
        assertEquals("at roundabout, take exit 1", ri.getTurnDescription(tr));
    }

    @Test
    public void testExitWithDestination_whenNoName() {
        // Issue #3213: destination should be used when street has no name
        RoundaboutInstruction ri = createExitedRoundabout("");
        ri.setExtraInfo(STREET_DESTINATION, "Euskirchen, BN-Hardtberg");
        assertEquals("at roundabout, take exit 1 toward Euskirchen, BN-Hardtberg", ri.getTurnDescription(tr));
    }

    @Test
    public void testExitWithDestination_whenNameEqualsRef() {
        // Issue #3213: when name == ref (OSM copies ref into name), prefer destination
        RoundaboutInstruction ri = createExitedRoundabout("L 113");
        ri.setExtraInfo(STREET_REF, "L 113");
        ri.setExtraInfo(STREET_DESTINATION, "Euskirchen, BN-Hardtberg");
        assertEquals("at roundabout, take exit 1 toward Euskirchen, BN-Hardtberg", ri.getTurnDescription(tr));
    }

    @Test
    public void testExitWithRefOnly_noDestination() {
        // When only ref is available (no name, no destination), use ref
        RoundaboutInstruction ri = createExitedRoundabout("");
        ri.setExtraInfo(STREET_REF, "L 113");
        // _getName() falls back to ref when name is empty, so streetName = "L 113"
        assertEquals("at roundabout, take exit 1 onto L 113", ri.getTurnDescription(tr));
    }

    @Test
    public void testExitWithNameDifferentFromRef() {
        // Normal case: name differs from ref, name takes priority over destination
        RoundaboutInstruction ri = createExitedRoundabout("Bonner Weg");
        ri.setExtraInfo(STREET_REF, "L 113");
        ri.setExtraInfo(STREET_DESTINATION, "Euskirchen");
        assertEquals("at roundabout, take exit 1 onto Bonner Weg", ri.getTurnDescription(tr));
    }
}
