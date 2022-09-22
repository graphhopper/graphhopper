/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.Country;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

import java.util.*;
import java.util.stream.Collectors;

import static com.graphhopper.util.GHUtility.readCountries;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AreaIndexTest {

    @Test
    void basic() {
        GeometryFactory geometryFactory = new GeometryFactory();
        Polygon border1 = geometryFactory.createPolygon(new Coordinate[]{
                new Coordinate(1, 1), new Coordinate(2, 1), new Coordinate(2, 2), new Coordinate(1, 2),
                new Coordinate(1, 1)});
        Polygon border2 = geometryFactory.createPolygon(new Coordinate[]{
                new Coordinate(5, 5), new Coordinate(6, 5), new Coordinate(6, 6), new Coordinate(5, 6),
                new Coordinate(5, 5)});
        Polygon border3 = geometryFactory.createPolygon(new Coordinate[]{
                new Coordinate(9, 9), new Coordinate(10, 9), new Coordinate(10, 10), new Coordinate(9, 10),
                new Coordinate(9, 9)});
        AreaIndex<CustomArea> index = new AreaIndex<>(Arrays.asList(
                createCustomArea("1", border1),
                createCustomArea("2", border2),
                createCustomArea("3", border3),
                createCustomArea("4", border2, border3)));
        testQuery(index, 0, 0);
        testQuery(index, 1.5, 1.5, "1");
        testQuery(index, 1.00001, 1.00001, "1");
        testQuery(index, 1.5, 1.00001, "1");
        testQuery(index, 1.00001, 1.5, "1");
        testQuery(index, 5.5, 5.5, "2", "4");
        testQuery(index, 9.5, 9.5, "3", "4");
    }

    @Test
    void testPrecision() {
        // Taken from here: https://github.com/johan/world.geo.json/blob/master/countries/DEU.geo.json
        String germanPolygonJson = "[9.921906,54.983104],[9.93958,54.596642],[10.950112,54.363607],[10.939467,54.008693],[11.956252,54.196486],[12.51844,54.470371],[13.647467,54.075511],[14.119686,53.757029],[14.353315,53.248171],[14.074521,52.981263],[14.4376,52.62485],[14.685026,52.089947],[14.607098,51.745188],[15.016996,51.106674],[14.570718,51.002339],[14.307013,51.117268],[14.056228,50.926918],[13.338132,50.733234],[12.966837,50.484076],[12.240111,50.266338],[12.415191,49.969121],[12.521024,49.547415],[13.031329,49.307068],[13.595946,48.877172],[13.243357,48.416115],[12.884103,48.289146],[13.025851,47.637584],[12.932627,47.467646],[12.62076,47.672388],[12.141357,47.703083],[11.426414,47.523766],[10.544504,47.566399],[10.402084,47.302488],[9.896068,47.580197],[9.594226,47.525058],[8.522612,47.830828],[8.317301,47.61358],[7.466759,47.620582],[7.593676,48.333019],[8.099279,49.017784],[6.65823,49.201958],[6.18632,49.463803],[6.242751,49.902226],[6.043073,50.128052],[6.156658,50.803721],[5.988658,51.851616],[6.589397,51.852029],[6.84287,52.22844],[7.092053,53.144043],[6.90514,53.482162],[7.100425,53.693932],[7.936239,53.748296],[8.121706,53.527792],[8.800734,54.020786],[8.572118,54.395646],[8.526229,54.962744],[9.282049,54.830865],[9.921906,54.983104]";
        Polygon germanPolygon = parsePolygonString(germanPolygonJson);
        AreaIndex<CustomArea> index = new AreaIndex<>(Collections.singletonList(createCustomArea("germany", germanPolygon)));

        // Far from the border of Germany, in Germany
        testQuery(index, 48.777106, 9.180769, "germany");
        testQuery(index, 51.806281, 7.269380, "germany");
        testQuery(index, 50.636710, 12.514561, "germany");

        // Far from the border of Germany, not in Germany
        testQuery(index, 48.029533, 7.250122);
        testQuery(index, 51.694467, 15.209218);
        testQuery(index, 47.283669, 11.167381);

        // Close to the border of Germany, in Germany - Whereas the borders are defined by the GeoJson above and do not strictly follow the actual border
        testQuery(index, 50.017714, 12.356129, "germany");
        testQuery(index, 49.949930, 6.225853, "germany");
        testQuery(index, 47.580866, 9.707582, "germany");
        testQuery(index, 47.565101, 9.724267, "germany");
        testQuery(index, 47.557166, 9.738343, "germany");

        // Close to the border of Germany, not in Germany
        testQuery(index, 50.025342, 12.386262);
        testQuery(index, 49.932900, 6.174023);
        testQuery(index, 47.547463, 9.741948);
    }

    @Test
    public void testHole() {
        GeometryFactory gf = new GeometryFactory();
        LinearRing shell = gf.createLinearRing(new Coordinate[]{
                new Coordinate(1, 1), new Coordinate(7, 1), new Coordinate(7, 7), new Coordinate(1, 7),
                new Coordinate(1, 1)});
        LinearRing hole = gf.createLinearRing(new Coordinate[]{
                new Coordinate(4, 2), new Coordinate(6, 2), new Coordinate(6, 4), new Coordinate(4, 6),
                new Coordinate(4, 2)});
        {
            Polygon p = gf.createPolygon(shell, new LinearRing[]{hole});
            AreaIndex<CustomArea> index = new AreaIndex<>(Collections.singletonList(createCustomArea("1", p)));
            testQuery(index, 3, 5);
        }
        {
            Polygon p = gf.createPolygon(hole);
            AreaIndex<CustomArea> index = new AreaIndex<>(Collections.singletonList(createCustomArea("2", p)));
            testQuery(index, 3, 5, "2");
        }
    }

    @Test
    public void testOverlap() {
        GeometryFactory gf = new GeometryFactory();
        // Let the two polygons overlap
        Polygon border1 = gf.createPolygon(new Coordinate[]{
                new Coordinate(1, 1), new Coordinate(2, 1), new Coordinate(2, 2), new Coordinate(1, 2),
                new Coordinate(1, 1)});
        Polygon border2 = gf.createPolygon(new Coordinate[]{
                new Coordinate(0.5, 1), new Coordinate(1.5, 1), new Coordinate(1.5, 2), new Coordinate(0.5, 2),
                new Coordinate(0.5, 1)});

        AreaIndex<CustomArea> index = new AreaIndex<>(Arrays.asList(
                createCustomArea("1", border1),
                createCustomArea("2", border2)
        ));
        testQuery(index, 1.5, 1.25, "1", "2");
        testQuery(index, 1.5, 0.99, "2");
        testQuery(index, 1.5, 1.0001, "1", "2");
        testQuery(index, 1.5, 1.4999, "1", "2");
        testQuery(index, 1.5, 1.51, "1");
    }

    @Test
    public void testCountries() {
        AreaIndex<CustomArea> countryIndex = createCountryIndex();
        assertEquals("DEU", countryIndex.query(52.52437, 13.41053).get(0).getProperties().get(Country.ISO_ALPHA3));
        assertEquals("FRA", countryIndex.query(48.86471, 2.349014).get(0).getProperties().get(Country.ISO_ALPHA3));
        assertEquals("USA", countryIndex.query(35.67514, -105.94665).get(0).getProperties().get(Country.ISO_ALPHA3));
        assertEquals("AUT", countryIndex.query(48.20448, 16.10788).get(0).getProperties().get(Country.ISO_ALPHA3));
    }

    private AreaIndex<CustomArea> createCountryIndex() {
        return new AreaIndex<>(readCountries());
    }

    private static Polygon parsePolygonString(String polygonString) {
        String[] polygonStringArr = polygonString.split("],\\[");
        Coordinate[] shell = new Coordinate[polygonStringArr.length + 1];
        for (int i = 0; i < polygonStringArr.length; i++) {
            String temp = polygonStringArr[i];
            temp = temp.replaceAll("\\[", "");
            temp = temp.replaceAll("]", "");
            String[] coords = temp.split(",");
            shell[i] = new Coordinate(Double.parseDouble(coords[0]), Double.parseDouble(coords[1]));
        }
        shell[shell.length - 1] = shell[0];
        return new GeometryFactory().createPolygon(shell);
    }

    private static void testQuery(AreaIndex<CustomArea> index, double lat, double lon, String... ids) {
        List<CustomArea> result = index.query(lat, lon);
        Set<String> resultIds = result.stream().map(CustomArea::getProperties).map(p -> (String) p.get("id")).collect(Collectors.toSet());
        assertEquals(new HashSet<>(Arrays.asList(ids)), resultIds);
        assertEquals(ids.length, result.size());
    }

    private static CustomArea createCustomArea(String id, Polygon... borders) {
        Map<String, Object> properties = new HashMap<>(1);
        properties.put("id", id);
        return new CustomArea(properties, Arrays.asList(borders));
    }
}