package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.routing.ev.RoadAccess;
import com.graphhopper.routing.ev.RoadClass;
import org.junit.Test;
import org.locationtech.jts.geom.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Robin Boldt
 * @author Thomas Butz
 */
public class SpatialRuleLookupJTSTest {
    
    private static final GeometryFactory FAC = new GeometryFactory();

    @Test
    public void testSpatialLookup() {
        Polygon deBorder = FAC.createPolygon(new Coordinate[] { new Coordinate(1, 1), new Coordinate(2, 1), new Coordinate(2, 2), new Coordinate(1, 2), new Coordinate(1, 1) });
        List<SpatialRule> spatialRules = new ArrayList<>();
        SpatialRule germany = new AbstractSpatialRule(deBorder) {
            @Override
            public String getId() {
                return "DEU";
            }
        };
        spatialRules.add(germany);
        
        Polygon atBorder = FAC.createPolygon(new Coordinate[] { new Coordinate(5, 5), new Coordinate(6, 5), new Coordinate(6, 6), new Coordinate(5, 6), new Coordinate(5, 5) });
        SpatialRule austria = new AbstractSpatialRule(atBorder) {
            @Override
            public String getId() {
                return "AUT";
            }
        };
        spatialRules.add(austria);

        // create lookup with bbox just for DEU (for space reduction)
        SpatialRuleLookupJTS lookup = new SpatialRuleLookupJTS(spatialRules, deBorder.getEnvelopeInternal());
        SpatialRule rule = getFirstRule(lookup, 1.5, 1.5);
        assertSame(germany, rule);
        assertEquals("DEU", rule.getId());
        int id = lookup.getRules().indexOf(rule);
        assertTrue(id > -1);
        assertEquals(rule, lookup.getRules().get(id));
    }

    @Test
    public void testPrecision() {
        // Taken from here: https://github.com/johan/world.geo.json/blob/master/countries/DEU.geo.json
        String germanPolygonJson = "[9.921906,54.983104],[9.93958,54.596642],[10.950112,54.363607],[10.939467,54.008693],[11.956252,54.196486],[12.51844,54.470371],[13.647467,54.075511],[14.119686,53.757029],[14.353315,53.248171],[14.074521,52.981263],[14.4376,52.62485],[14.685026,52.089947],[14.607098,51.745188],[15.016996,51.106674],[14.570718,51.002339],[14.307013,51.117268],[14.056228,50.926918],[13.338132,50.733234],[12.966837,50.484076],[12.240111,50.266338],[12.415191,49.969121],[12.521024,49.547415],[13.031329,49.307068],[13.595946,48.877172],[13.243357,48.416115],[12.884103,48.289146],[13.025851,47.637584],[12.932627,47.467646],[12.62076,47.672388],[12.141357,47.703083],[11.426414,47.523766],[10.544504,47.566399],[10.402084,47.302488],[9.896068,47.580197],[9.594226,47.525058],[8.522612,47.830828],[8.317301,47.61358],[7.466759,47.620582],[7.593676,48.333019],[8.099279,49.017784],[6.65823,49.201958],[6.18632,49.463803],[6.242751,49.902226],[6.043073,50.128052],[6.156658,50.803721],[5.988658,51.851616],[6.589397,51.852029],[6.84287,52.22844],[7.092053,53.144043],[6.90514,53.482162],[7.100425,53.693932],[7.936239,53.748296],[8.121706,53.527792],[8.800734,54.020786],[8.572118,54.395646],[8.526229,54.962744],[9.282049,54.830865],[9.921906,54.983104]";
        Polygon germanPolygon = parsePolygonString(germanPolygonJson);
        SpatialRule germanySpatialRule = new AbstractSpatialRule(germanPolygon) {
            @Override
            public String getId() {
                return "DEU";
            }
        };

        SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupJTS(Collections.singletonList(germanySpatialRule), new Envelope(-180, 180, -90, 90));

        // Far from the border of Germany, in Germany
        assertSame(germanySpatialRule, getFirstRule(spatialRuleLookup, 48.777106, 9.180769));
        assertSame(germanySpatialRule, getFirstRule(spatialRuleLookup, 51.806281, 7.269380));
        assertSame(germanySpatialRule, getFirstRule(spatialRuleLookup, 50.636710, 12.514561));

        // Far from the border of Germany, not in Germany
        assertSame(null, getFirstRule(spatialRuleLookup, 48.029533, 7.250122));
        assertSame(null, getFirstRule(spatialRuleLookup, 51.694467, 15.209218));
        assertSame(null, getFirstRule(spatialRuleLookup, 47.283669, 11.167381));

        // Close to the border of Germany, in Germany - Whereas the borders are defined by the GeoJson above and do not strictly follow the actual border
        assertSame(germanySpatialRule, getFirstRule(spatialRuleLookup, 50.017714, 12.356129));
        assertSame(germanySpatialRule, getFirstRule(spatialRuleLookup, 49.949930, 6.225853));
        assertSame(germanySpatialRule, getFirstRule(spatialRuleLookup, 47.580866, 9.707582));
        assertSame(germanySpatialRule, getFirstRule(spatialRuleLookup, 47.565101, 9.724267));
        assertSame(germanySpatialRule, getFirstRule(spatialRuleLookup, 47.557166, 9.738343));

        // Close to the border of Germany, not in Germany
        assertSame(null, getFirstRule(spatialRuleLookup, 50.025342, 12.386262));
        assertSame(null, getFirstRule(spatialRuleLookup, 49.932900, 6.174023));
        assertSame(null, getFirstRule(spatialRuleLookup, 47.547463, 9.741948));
    }
    
    @Test
    public void testHole() {
        LinearRing shell = FAC.createLinearRing(new Coordinate[] { new Coordinate(1, 1), new Coordinate(7, 1), new Coordinate(7, 7), new Coordinate(1, 7), new Coordinate(1, 1)});
        LinearRing hole = FAC.createLinearRing(new Coordinate[] { new Coordinate(4, 2), new Coordinate(6, 2), new Coordinate(6, 4), new Coordinate(4, 6), new Coordinate(4, 2)});
        Polygon p1 = FAC.createPolygon(shell, new LinearRing[] { hole });
        SpatialRule s1 = getSpatialRule(p1, "1");
        List<SpatialRule> spatialRules = new ArrayList<>();
        spatialRules.add(s1);
        
        SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupJTS(spatialRules, p1.getEnvelopeInternal());
        
        assertSame(null, getFirstRule(spatialRuleLookup, 3, 5));

        Polygon p2 = FAC.createPolygon(hole);
        SpatialRule s2 = getSpatialRule(p2, "2");
        spatialRules.add(s2);
        spatialRuleLookup = new SpatialRuleLookupJTS(spatialRules, p1.getEnvelopeInternal());
        
        assertSame(s2, getFirstRule(spatialRuleLookup, 3, 5));
    }

    @Test
    public void testSpatialRuleForId() {
        List<SpatialRule> spatialRules = new ArrayList<>();
        Polygon p1 = FAC.createPolygon(new Coordinate[] { new Coordinate(1, 1), new Coordinate(2, 1), new Coordinate(2, 1.5), new Coordinate(1, 1.5), new Coordinate(1, 1)});
        Polygon p2 = FAC.createPolygon(new Coordinate[] { new Coordinate(1, 1.5), new Coordinate(2, 1.5), new Coordinate(2, 2), new Coordinate(1, 2), new Coordinate(1, 1.5)});
        Polygon p3 = FAC.createPolygon(new Coordinate[] { new Coordinate(1, 1.5), new Coordinate(2, 1.5), new Coordinate(2, 2), new Coordinate(1, 2), new Coordinate(1, 1.5)});
        Polygon p4 = FAC.createPolygon(new Coordinate[] { new Coordinate(1, 1.5), new Coordinate(2, 1.5), new Coordinate(2, 2), new Coordinate(1, 2), new Coordinate(1, 1.5)});
        spatialRules.add(getSpatialRule(p1, "0"));
        spatialRules.add(getSpatialRule(p2, "1"));
        spatialRules.add(getSpatialRule(p3, "2"));
        spatialRules.add(getSpatialRule(p4, "3"));

        SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupJTS(spatialRules, new Envelope(1, 2, 1, 2));

        assertEquals("0", spatialRuleLookup.getRules().get(0).getId());
        assertEquals("3", spatialRuleLookup.getRules().get(3).getId());
    }
    
    @Test
    public void testOverlapWithPriority() {
        // Let the two polygons overlap
        Polygon deBorder = FAC.createPolygon(new Coordinate[] { new Coordinate(1, 1),
                        new Coordinate(2, 1), new Coordinate(2, 2), new Coordinate(1, 2),
                        new Coordinate(1, 1) });
        List<SpatialRule> spatialRules = new ArrayList<>();
        SpatialRule germany = new AbstractSpatialRule(deBorder) {
            @Override
            public String getId() {
                return "DEU";
            }
        };
        spatialRules.add(germany);

        Polygon atBorder = FAC.createPolygon(new Coordinate[] { new Coordinate(0.5, 1),
                        new Coordinate(1.5, 1), new Coordinate(1.5, 2), new Coordinate(0.5, 2),
                        new Coordinate(0.5, 1) });
        SpatialRule austria = new AbstractSpatialRule(atBorder) {
            @Override
            public String getId() {
                return "AUT";
            }

            @Override
            public int getPriority() {
                return 500;
            }
        };
        spatialRules.add(austria);

        SpatialRuleLookupJTS lookup = new SpatialRuleLookupJTS(spatialRules,
                        new Envelope(0, 3, 0, 3));
        SpatialRuleSet set = lookup.lookupRules(1.5, 1.25);
        assertEquals(2, set.getRules().size());
        // Since Austria is priority 500, Germany should be the first, Austria
        // the second
        assertEquals(germany.getId(), set.getRules().get(0).getId());
        assertEquals(austria.getId(), set.getRules().get(1).getId());
        assertEquals(1, lookup.lookupRules(1.5, 0.99).getRules().size());
        assertEquals(2, lookup.lookupRules(1.5, 1.00).getRules().size());
        assertEquals(2, lookup.lookupRules(1.5, 1.50).getRules().size());
        assertEquals(1, lookup.lookupRules(1.5, 1.51).getRules().size());
    }

    private Polygon parsePolygonString(String polygonString) {
        String[] germanPolygonArr = polygonString.split("\\],\\[");
        Coordinate[] shell = new Coordinate[germanPolygonArr.length + 1];
        for (int i = 0; i < germanPolygonArr.length; i++) {
            String temp = germanPolygonArr[i];
            temp = temp.replaceAll("\\[", "");
            temp = temp.replaceAll("\\]", "");
            String[] coords = temp.split(",");
            shell[i] = new Coordinate(Double.parseDouble(coords[0]), Double.parseDouble(coords[1]));
        }
        shell[shell.length - 1] = shell[0];

        return FAC.createPolygon(shell);
    }

    private SpatialRule getSpatialRule(Polygon p, final String name) {
        return new AbstractSpatialRule(p) {
            
            @Override
            public double getMaxSpeed(RoadClass roadClass, TransportationMode transport, double currentMaxSpeed) {
                return currentMaxSpeed;
            }

            @Override
            public RoadAccess getAccess(RoadClass roadClass, TransportationMode transport, RoadAccess currentRoadAccess) {
                return RoadAccess.DESTINATION;
            }

            @Override
            public String getId() {
                return name;
            }
        };
    }
    
    private static SpatialRule getFirstRule(SpatialRuleLookup spatialRuleLookup, double lat, double lon) {
        SpatialRuleSet set = spatialRuleLookup.lookupRules(lat, lon);
        if (set.getRules().isEmpty()) {
            return null;
        }
        return set.getRules().get(0);
    }
}
