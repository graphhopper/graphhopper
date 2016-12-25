package com.graphhopper.routing.util.spatialrule;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.spatialrules.*;
import com.graphhopper.routing.util.spatialrules.countries.AustriaSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.GermanySpatialRule;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.BBox;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * @author Robin Boldt
 */
public class SpatialRuleLookupArrayTest {

    @Test
    public void testSmallScenario(){

        BBox bounds = new BBox(1,4, 1, 4);
        SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupArray(bounds ,1, false);
        spatialRuleLookup.addRule(getSpatialRule(new Polygon(new double[]{1, 1, 2, 2}, new double[]{1, 2, 2, 1})));
        spatialRuleLookup.addRule(getSpatialRule(new Polygon(new double[]{1, 1, 3.6, 3.6}, new double[]{3, 4, 4, 3})));
        assertEquals(AccessValue.EVENTUALLY_ACCESSIBLE, spatialRuleLookup.lookupRule(1.2, 1.7).isAccessible(null,""));
        assertEquals(AccessValue.EVENTUALLY_ACCESSIBLE, spatialRuleLookup.lookupRule(1.2, 3.7).isAccessible(null,""));
        // Not in the second Polygon anymore, but due to the resolution of 1, this should be still match the rule
        assertEquals(AccessValue.EVENTUALLY_ACCESSIBLE, spatialRuleLookup.lookupRule(3.9, 3.7).isAccessible(null,""));
        assertEquals(AccessValue.ACCESSIBLE, spatialRuleLookup.lookupRule(2.2, 1.7).isAccessible(null,""));

    }

    @Test
    public void testExact(){

        BBox bounds = new BBox(1,4, 1, 4);
        SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupArray(bounds ,1, true);
        spatialRuleLookup.addRule(getSpatialRule(new Polygon(new double[]{1, 1, 2, 2}, new double[]{1, 2, 2, 1})));
        spatialRuleLookup.addRule(getSpatialRule(new Polygon(new double[]{1, 1, 3.6, 3.6}, new double[]{3, 4, 4, 3})));
        assertEquals(AccessValue.EVENTUALLY_ACCESSIBLE, spatialRuleLookup.lookupRule(1.2, 1.7).isAccessible(null,""));
        assertEquals(AccessValue.EVENTUALLY_ACCESSIBLE, spatialRuleLookup.lookupRule(1.2, 3.7).isAccessible(null,""));
        // Not in the second Polygon anymore
        assertEquals(AccessValue.ACCESSIBLE, spatialRuleLookup.lookupRule(3.9, 3.7).isAccessible(null,""));
        assertEquals(AccessValue.ACCESSIBLE, spatialRuleLookup.lookupRule(2.2, 1.7).isAccessible(null,""));

    }

    @Test
    public void testExactCountry(){

        SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupArray(new BBox(-180,180,-90,90), .1, true);

        // Taken from here: https://github.com/johan/world.geo.json/blob/master/countries/DEU.geo.json
        String germanPolygonJson = "[9.921906,54.983104],[9.93958,54.596642],[10.950112,54.363607],[10.939467,54.008693],[11.956252,54.196486],[12.51844,54.470371],[13.647467,54.075511],[14.119686,53.757029],[14.353315,53.248171],[14.074521,52.981263],[14.4376,52.62485],[14.685026,52.089947],[14.607098,51.745188],[15.016996,51.106674],[14.570718,51.002339],[14.307013,51.117268],[14.056228,50.926918],[13.338132,50.733234],[12.966837,50.484076],[12.240111,50.266338],[12.415191,49.969121],[12.521024,49.547415],[13.031329,49.307068],[13.595946,48.877172],[13.243357,48.416115],[12.884103,48.289146],[13.025851,47.637584],[12.932627,47.467646],[12.62076,47.672388],[12.141357,47.703083],[11.426414,47.523766],[10.544504,47.566399],[10.402084,47.302488],[9.896068,47.580197],[9.594226,47.525058],[8.522612,47.830828],[8.317301,47.61358],[7.466759,47.620582],[7.593676,48.333019],[8.099279,49.017784],[6.65823,49.201958],[6.18632,49.463803],[6.242751,49.902226],[6.043073,50.128052],[6.156658,50.803721],[5.988658,51.851616],[6.589397,51.852029],[6.84287,52.22844],[7.092053,53.144043],[6.90514,53.482162],[7.100425,53.693932],[7.936239,53.748296],[8.121706,53.527792],[8.800734,54.020786],[8.572118,54.395646],[8.526229,54.962744],[9.282049,54.830865],[9.921906,54.983104]";
        Polygon germanPolygon = parsePolygonString(germanPolygonJson);

        SpatialRule germanySpatialRule = new GermanySpatialRule();
        germanySpatialRule.addBorder(germanPolygon);
        spatialRuleLookup.addRule(germanySpatialRule);

        //Far from the border of Germany, in Germany
        assertEquals("DEU", spatialRuleLookup.lookupRule(48.777106,9.180769).getCountryIsoA3Name());
        assertEquals("DEU", spatialRuleLookup.lookupRule(51.806281, 7.269380).getCountryIsoA3Name());
        assertEquals("DEU", spatialRuleLookup.lookupRule(50.636710, 12.514561).getCountryIsoA3Name());

        //Far from the border of Germany, not in Germany
        assertEquals("", spatialRuleLookup.lookupRule(48.029533, 7.250122).getCountryIsoA3Name());
        assertEquals("", spatialRuleLookup.lookupRule(51.694467, 15.209218).getCountryIsoA3Name());
        assertEquals("", spatialRuleLookup.lookupRule(47.283669, 11.167381).getCountryIsoA3Name());

        //Close to the border of Germany, in Germany - Whereas the borders are defined by the GeoJson above and do not strictly follow the acutal border
        assertEquals("DEU", spatialRuleLookup.lookupRule(50.017714, 12.356129).getCountryIsoA3Name());
        assertEquals("DEU", spatialRuleLookup.lookupRule(49.949930, 6.225853).getCountryIsoA3Name());
        assertEquals("DEU", spatialRuleLookup.lookupRule(47.580866, 9.707582).getCountryIsoA3Name());
        assertEquals("DEU", spatialRuleLookup.lookupRule(47.565101, 9.724267).getCountryIsoA3Name());
        assertEquals("DEU", spatialRuleLookup.lookupRule(47.557166, 9.738343).getCountryIsoA3Name());

        //Close to the border of Germany, not in Germany
        assertEquals("", spatialRuleLookup.lookupRule(50.025342, 12.386262).getCountryIsoA3Name());
        assertEquals("", spatialRuleLookup.lookupRule(49.932900, 6.174023).getCountryIsoA3Name());
        assertEquals("", spatialRuleLookup.lookupRule(47.547463, 9.741948).getCountryIsoA3Name());

        System.out.println("Started Performance Test");
        StopWatch w = new StopWatch().start();
        Random r = new Random();
        double lat;
        double lon;
        for (int i = 0; i < 10000000; i++) {
            // Somewhere in south west Germany, even close to the border
            lat = 47+r.nextDouble();
            lon = 9+r.nextDouble();
            spatialRuleLookup.lookupRule(lat, lon);
        }
        w.stop();
        System.out.println("Finished Performance Test in: "+w.getSeconds()+" seconds");
    }

    @Test
    public void testExactAdjacentBorder(){
        BBox bounds = new BBox(1,4, 1, 4);
        SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupArray(bounds ,1, true);
        // Two rules that divide the tile in half
        spatialRuleLookup.addRule(getSpatialRule(new Polygon(new double[]{1, 1, 1.5, 1.5}, new double[]{1, 2, 2, 1}), "top"));
        spatialRuleLookup.addRule(getSpatialRule(new Polygon(new double[]{1.5, 1.5, 2, 2}, new double[]{1, 2, 2, 1}), "bot"));

        assertEquals("top", spatialRuleLookup.lookupRule(1.4,1.5).getCountryIsoA3Name());
        assertEquals("bot", spatialRuleLookup.lookupRule(1.6,1.5).getCountryIsoA3Name());
    }

    @Test
    public void testCountryScenario(){

        // TODO: Currently almost similar to SpatialRuleLookupBuilderTest, maybe delete this one?, but uses different polygones...
        // Osm countryspecific rules are documented here:
        // https://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Access-Restrictions

        SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupArray(new BBox(-180,180,-90,90), .1, false);

        // Taken from here: https://github.com/johan/world.geo.json/blob/master/countries/DEU.geo.json
        String germanPolygonJson = "[9.921906,54.983104],[9.93958,54.596642],[10.950112,54.363607],[10.939467,54.008693],[11.956252,54.196486],[12.51844,54.470371],[13.647467,54.075511],[14.119686,53.757029],[14.353315,53.248171],[14.074521,52.981263],[14.4376,52.62485],[14.685026,52.089947],[14.607098,51.745188],[15.016996,51.106674],[14.570718,51.002339],[14.307013,51.117268],[14.056228,50.926918],[13.338132,50.733234],[12.966837,50.484076],[12.240111,50.266338],[12.415191,49.969121],[12.521024,49.547415],[13.031329,49.307068],[13.595946,48.877172],[13.243357,48.416115],[12.884103,48.289146],[13.025851,47.637584],[12.932627,47.467646],[12.62076,47.672388],[12.141357,47.703083],[11.426414,47.523766],[10.544504,47.566399],[10.402084,47.302488],[9.896068,47.580197],[9.594226,47.525058],[8.522612,47.830828],[8.317301,47.61358],[7.466759,47.620582],[7.593676,48.333019],[8.099279,49.017784],[6.65823,49.201958],[6.18632,49.463803],[6.242751,49.902226],[6.043073,50.128052],[6.156658,50.803721],[5.988658,51.851616],[6.589397,51.852029],[6.84287,52.22844],[7.092053,53.144043],[6.90514,53.482162],[7.100425,53.693932],[7.936239,53.748296],[8.121706,53.527792],[8.800734,54.020786],[8.572118,54.395646],[8.526229,54.962744],[9.282049,54.830865],[9.921906,54.983104]";
        Polygon germanPolygon = parsePolygonString(germanPolygonJson);

        SpatialRule germanySpatialRule = new GermanySpatialRule();
        germanySpatialRule.addBorder(germanPolygon);
        spatialRuleLookup.addRule(germanySpatialRule);

        // Taken from here: https://github.com/johan/world.geo.json/blob/master/countries/AUT.geo.json
        String austrianPolygonJson = "[16.979667,48.123497],[16.903754,47.714866],[16.340584,47.712902],[16.534268,47.496171],[16.202298,46.852386],[16.011664,46.683611],[15.137092,46.658703],[14.632472,46.431817],[13.806475,46.509306],[12.376485,46.767559],[12.153088,47.115393],[11.164828,46.941579],[11.048556,46.751359],[10.442701,46.893546],[9.932448,46.920728],[9.47997,47.10281],[9.632932,47.347601],[9.594226,47.525058],[9.896068,47.580197],[10.402084,47.302488],[10.544504,47.566399],[11.426414,47.523766],[12.141357,47.703083],[12.62076,47.672388],[12.932627,47.467646],[13.025851,47.637584],[12.884103,48.289146],[13.243357,48.416115],[13.595946,48.877172],[14.338898,48.555305],[14.901447,48.964402],[15.253416,49.039074],[16.029647,48.733899],[16.499283,48.785808],[16.960288,48.596982],[16.879983,48.470013],[16.979667,48.123497]";
        Polygon austrianPolygon = parsePolygonString(austrianPolygonJson);

        SpatialRule austrianSpatialRule = new AustriaSpatialRule();
        austrianSpatialRule.addBorder(austrianPolygon);
        spatialRuleLookup.addRule(austrianSpatialRule);



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

    private Polygon parsePolygonString(String polygonString){
        String[] germanPolygonArr = polygonString.split("\\],\\[");
        double[] lats = new double[germanPolygonArr.length];
        double[] lons = new double[germanPolygonArr.length];
        for (int i = 0; i < germanPolygonArr.length; i++) {
            String temp = germanPolygonArr[i];
            temp = temp.replaceAll("\\[", "");
            temp = temp.replaceAll("\\]", "");
            String[] coords = temp.split(",");
            lats[i] = Double.parseDouble(coords[1]);
            lons[i] = Double.parseDouble(coords[0]);
        }

        return new Polygon(lats, lons);
    }

    private SpatialRule getSpatialRule(Polygon p){
        return getSpatialRule(p, null);
    }

    private SpatialRule getSpatialRule(Polygon p, final String name){
        SpatialRule rule = new AbstractSpatialRule() {
            @Override
            public int getMaxSpeed(ReaderWay readerWay, String transportationMode) {
                return 0;
            }

            @Override
            public AccessValue isAccessible(ReaderWay readerWay, String transportationMode) {
                return AccessValue.EVENTUALLY_ACCESSIBLE;
            }

            @Override
            public String getCountryIsoA3Name() {
                return name;
            }
        };
        rule.addBorder(p);
        return rule;
    }

}
