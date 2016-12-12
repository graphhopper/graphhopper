package com.graphhopper.routing.util.spatialrule;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.spatialrules.*;
import com.graphhopper.routing.util.spatialrules.countries.GermanySpatialRule;
import com.graphhopper.util.shapes.BBox;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Robin Boldt
 */
public class SpatialRuleLookupBuilderTest {

    @Test
    public void test(){
        SpatialRuleLookup spatialRuleLookup = SpatialRuleLookupBuilder.build();

        ReaderWay track = new ReaderWay(0);
        track.setTag("highway", "track");

        ReaderWay primary = new ReaderWay(0);
        primary.setTag("highway", "primary");

        ReaderWay livingStreet = new ReaderWay(0);
        livingStreet.setTag("highway", "living_street");

        // Berlin
        assertEquals(AccessValue.EVENTUALLY_ACCESSIBLE, spatialRuleLookup.lookupRule(52.5243700, 13.4105300).isAccessible(track, ""));
        assertEquals(AccessValue.ACCESSIBLE, spatialRuleLookup.lookupRule(52.5243700, 13.4105300).isAccessible(primary, ""));

        // Paris
        assertEquals(AccessValue.ACCESSIBLE, spatialRuleLookup.lookupRule(48.864716, 2.349014).isAccessible(track, ""));
        assertEquals(AccessValue.ACCESSIBLE, spatialRuleLookup.lookupRule(48.864716, 2.349014).isAccessible(primary, ""));

        // Vienna
        assertEquals(AccessValue.ACCESSIBLE, spatialRuleLookup.lookupRule(48.210033, 16.363449).isAccessible(track, ""));
        assertEquals(AccessValue.ACCESSIBLE, spatialRuleLookup.lookupRule(48.210033, 16.363449).isAccessible(primary, ""));
        assertEquals(AccessValue.EVENTUALLY_ACCESSIBLE, spatialRuleLookup.lookupRule(48.210033, 16.363449).isAccessible(livingStreet, ""));
    }

}
