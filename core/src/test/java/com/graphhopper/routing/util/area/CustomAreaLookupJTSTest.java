package com.graphhopper.routing.util.area;

import com.graphhopper.config.CustomArea;
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
public class CustomAreaLookupJTSTest {
    
    private static final GeometryFactory FAC = new GeometryFactory();

    @Test
    public void testAreaLookup() {
        Polygon deBorder = FAC.createPolygon(new Coordinate[] { new Coordinate(1, 1), new Coordinate(2, 1), new Coordinate(2, 2), new Coordinate(1, 2), new Coordinate(1, 1) });
        List<CustomArea> areas = new ArrayList<>();
        CustomArea germany = new CustomArea("DEU", Collections.singletonList(deBorder));
        areas.add(germany);
        
        Polygon atBorder = FAC.createPolygon(new Coordinate[] { new Coordinate(5, 5), new Coordinate(6, 5), new Coordinate(6, 6), new Coordinate(5, 6), new Coordinate(5, 5) });

        CustomArea austria = new CustomArea("AUT", Collections.singletonList(atBorder));
        areas.add(austria);

        // create lookup with bbox just for DEU (for space reduction)
        CustomAreaLookup lookup = new CustomAreaLookupJTS(areas);
        CustomArea area = getFirstArea(lookup, 1.5, 1.5);
        assertSame(germany, area);
        assertEquals("DEU", area.getId());
        int id = lookup.getAreas().indexOf(area);
        assertTrue(id > -1);
        assertEquals(area, lookup.getAreas().get(id));
    }

    @Test
    public void testPrecision() {
        // Taken from here: https://github.com/johan/world.geo.json/blob/master/countries/DEU.geo.json
        String germanPolygonJson = "[9.921906,54.983104],[9.93958,54.596642],[10.950112,54.363607],[10.939467,54.008693],[11.956252,54.196486],[12.51844,54.470371],[13.647467,54.075511],[14.119686,53.757029],[14.353315,53.248171],[14.074521,52.981263],[14.4376,52.62485],[14.685026,52.089947],[14.607098,51.745188],[15.016996,51.106674],[14.570718,51.002339],[14.307013,51.117268],[14.056228,50.926918],[13.338132,50.733234],[12.966837,50.484076],[12.240111,50.266338],[12.415191,49.969121],[12.521024,49.547415],[13.031329,49.307068],[13.595946,48.877172],[13.243357,48.416115],[12.884103,48.289146],[13.025851,47.637584],[12.932627,47.467646],[12.62076,47.672388],[12.141357,47.703083],[11.426414,47.523766],[10.544504,47.566399],[10.402084,47.302488],[9.896068,47.580197],[9.594226,47.525058],[8.522612,47.830828],[8.317301,47.61358],[7.466759,47.620582],[7.593676,48.333019],[8.099279,49.017784],[6.65823,49.201958],[6.18632,49.463803],[6.242751,49.902226],[6.043073,50.128052],[6.156658,50.803721],[5.988658,51.851616],[6.589397,51.852029],[6.84287,52.22844],[7.092053,53.144043],[6.90514,53.482162],[7.100425,53.693932],[7.936239,53.748296],[8.121706,53.527792],[8.800734,54.020786],[8.572118,54.395646],[8.526229,54.962744],[9.282049,54.830865],[9.921906,54.983104]";
        Polygon germanPolygon = parsePolygonString(germanPolygonJson);
        CustomArea germany = new CustomArea("DEU", Collections.singletonList(germanPolygon));

        CustomAreaLookup customAreaLookup = new CustomAreaLookupJTS(Collections.singletonList(germany));

        // Far from the border of Germany, in Germany
        assertSame(germany, getFirstArea(customAreaLookup, 48.777106, 9.180769));
        assertSame(germany, getFirstArea(customAreaLookup, 51.806281, 7.269380));
        assertSame(germany, getFirstArea(customAreaLookup, 50.636710, 12.514561));

        // Far from the border of Germany, not in Germany
        assertSame(null, getFirstArea(customAreaLookup, 48.029533, 7.250122));
        assertSame(null, getFirstArea(customAreaLookup, 51.694467, 15.209218));
        assertSame(null, getFirstArea(customAreaLookup, 47.283669, 11.167381));

        // Close to the border of Germany, in Germany - Whereas the borders are defined by the GeoJson above and do not strictly follow the actual border
        assertSame(germany, getFirstArea(customAreaLookup, 50.017714, 12.356129));
        assertSame(germany, getFirstArea(customAreaLookup, 49.949930, 6.225853));
        assertSame(germany, getFirstArea(customAreaLookup, 47.580866, 9.707582));
        assertSame(germany, getFirstArea(customAreaLookup, 47.565101, 9.724267));
        assertSame(germany, getFirstArea(customAreaLookup, 47.557166, 9.738343));

        // Close to the border of Germany, not in Germany
        assertSame(null, getFirstArea(customAreaLookup, 50.025342, 12.386262));
        assertSame(null, getFirstArea(customAreaLookup, 49.932900, 6.174023));
        assertSame(null, getFirstArea(customAreaLookup, 47.547463, 9.741948));
    }
    
    @Test
    public void testHole() {
        LinearRing shell = FAC.createLinearRing(new Coordinate[] { new Coordinate(1, 1), new Coordinate(7, 1), new Coordinate(7, 7), new Coordinate(1, 7), new Coordinate(1, 1)});
        LinearRing hole = FAC.createLinearRing(new Coordinate[] { new Coordinate(4, 2), new Coordinate(6, 2), new Coordinate(6, 4), new Coordinate(4, 6), new Coordinate(4, 2)});
        Polygon p1 = FAC.createPolygon(shell, new LinearRing[] { hole });
        CustomArea c1 = new CustomArea("1", Collections.singletonList(p1));
        List<CustomArea> customAreas = new ArrayList<>();
        customAreas.add(c1);
        
        CustomAreaLookup customAreaLookup = new CustomAreaLookupJTS(customAreas);
        
        assertSame(null, getFirstArea(customAreaLookup, 3, 5));

        Polygon p2 = FAC.createPolygon(hole);
        CustomArea c2 = new CustomArea("2", Collections.singletonList(p2));
        customAreas.add(c2);
        customAreaLookup = new CustomAreaLookupJTS(customAreas);
        
        assertSame(c2, getFirstArea(customAreaLookup, 3, 5));
    }

    @Test
    public void testCustomAreaOrder() {
        List<CustomArea> customAreas = new ArrayList<>();
        Polygon p1 = FAC.createPolygon(new Coordinate[] { new Coordinate(1, 1), new Coordinate(2, 1), new Coordinate(2, 1.5), new Coordinate(1, 1.5), new Coordinate(1, 1)});
        Polygon p2 = FAC.createPolygon(new Coordinate[] { new Coordinate(1, 1.5), new Coordinate(2, 1.5), new Coordinate(2, 2), new Coordinate(1, 2), new Coordinate(1, 1.5)});
        Polygon p3 = FAC.createPolygon(new Coordinate[] { new Coordinate(1, 1.5), new Coordinate(2, 1.5), new Coordinate(2, 2), new Coordinate(1, 2), new Coordinate(1, 1.5)});
        Polygon p4 = FAC.createPolygon(new Coordinate[] { new Coordinate(1, 1.5), new Coordinate(2, 1.5), new Coordinate(2, 2), new Coordinate(1, 2), new Coordinate(1, 1.5)});
        customAreas.add(new CustomArea("0", Collections.singletonList(p1)));
        customAreas.add(new CustomArea("1", Collections.singletonList(p2)));
        customAreas.add(new CustomArea("2", Collections.singletonList(p3)));
        customAreas.add(new CustomArea("3", Collections.singletonList(p4)));

        CustomAreaLookup customAreaLookup = new CustomAreaLookupJTS(customAreas);

        assertEquals("0", customAreaLookup.getAreas().get(0).getId());
        assertEquals("3", customAreaLookup.getAreas().get(3).getId());
    }
    
    @Test
    public void testOverlap() {
        // Let the two polygons overlap
        Polygon deBorder = FAC.createPolygon(new Coordinate[] { new Coordinate(1, 1),
                        new Coordinate(2, 1), new Coordinate(2, 2), new Coordinate(1, 2),
                        new Coordinate(1, 1) });
        List<CustomArea> customAreas = new ArrayList<>();
        CustomArea germany = new CustomArea("DEU", Collections.singletonList(deBorder));
        customAreas.add(germany);

        Polygon atBorder = FAC.createPolygon(new Coordinate[] { new Coordinate(0.5, 1),
                        new Coordinate(1.5, 1), new Coordinate(1.5, 2), new Coordinate(0.5, 2),
                        new Coordinate(0.5, 1) });

        CustomArea austria = new CustomArea("AUT", Collections.singletonList(atBorder));
        customAreas.add(austria);

        CustomAreaLookup customAreaLookup = new CustomAreaLookupJTS(customAreas);
        assertEquals(2, customAreaLookup.lookup(1.5, 1.25).getAreas().size());
        assertEquals(1, customAreaLookup.lookup(1.5, 0.99).getAreas().size());
        assertEquals(2, customAreaLookup.lookup(1.5, 1.00).getAreas().size());
        assertEquals(2, customAreaLookup.lookup(1.5, 1.50).getAreas().size());
        assertEquals(1, customAreaLookup.lookup(1.5, 1.51).getAreas().size());
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
    
    private static CustomArea getFirstArea(CustomAreaLookup areaLookup, double lat, double lon) {
        List<CustomArea> areas = areaLookup.lookup(lat, lon).getAreas();
        if (areas.isEmpty()) {
            return null;
        }
        return areas.get(0);
    }
}
