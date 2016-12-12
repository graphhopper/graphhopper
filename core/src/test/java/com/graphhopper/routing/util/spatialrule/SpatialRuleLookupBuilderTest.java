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

        assertEquals(AccessValue.NOT_ACCESSIBLE, spatialRuleLookup.lookupRule(50.680797145321655, 7.0751953125).isAccessible(track, ""));
        assertEquals(AccessValue.NOT_ACCESSIBLE, spatialRuleLookup.lookupRule(51.385495069223204, 10.17333984375).isAccessible(track, ""));

        // Berlin
        assertEquals(AccessValue.NOT_ACCESSIBLE, spatialRuleLookup.lookupRule(52.5243700, 13.4105300).isAccessible(track, ""));
        assertEquals(AccessValue.ACCESSIBLE, spatialRuleLookup.lookupRule(52.5243700, 13.4105300).isAccessible(primary, ""));

    }

}
