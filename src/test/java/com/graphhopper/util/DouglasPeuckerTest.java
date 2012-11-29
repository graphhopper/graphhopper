/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.util;

import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.FastestCarCalc;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.RAMDirectory;
import gnu.trove.list.array.TIntArrayList;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class DouglasPeuckerTest {

    // start from 2 so that we test point to index conversations too
    private static int OFFSET = 2;
    // get some real life points from graphhopper API
    // http://217.92.216.224:8080/api?from=49.945642,11.571436&to=49.946001,11.580706
    String points1 = "[[11.571499218899739,49.945605917549265],[11.571664621792689,49.94570668665409],[11.571787742639804,49.94578156499077],[11.572065649302282,49.94590338198625],[11.572209445511016,49.94595944760649],[11.57229438213172,49.94598850487147],[11.573315297960832,49.946237913062525],[11.57367665112786,49.946338495902836],[11.573895511937787,49.94641784458796],[11.574013417378367,49.94646347939514],[11.574228180368875,49.94654916107392],[11.574703899950622,49.94677509993557],[11.575003599561832,49.946924670344394],[11.575434615658997,49.94711838544425],[11.575559971680342,49.94716010869652],[11.57563783024932,49.947186185729194],[11.57609697228887,49.94727875919518],[11.57656188852851,49.947290121330845],[11.576840167720023,49.94727782787258],[11.576961425921949,49.94725827009808],[11.577226852861648,49.947215242994176],[11.577394863457863,49.94717668623872],[11.577511092517772,49.94715005041249],[11.577635517216523,49.947112238715114],[11.577917149169382,49.94702655703634],[11.577969116970207,49.947010724552214],[11.578816061738493,49.94673523932849],[11.579533552666014,49.94648974269233],[11.580073719771365,49.946299007824784],[11.580253092503245,49.946237913062525],[11.580604946179799,49.94608871518274],[11.580740546749693,49.94603041438826]]";

    Graph createGraph() {
        return new GraphStorage(new RAMDirectory()).createNew(10);
    }

    int parse(Graph g, String str) {
        int counter = OFFSET;
        for (String latlon : str.split("\\[")) {
            if (latlon.trim().isEmpty())
                continue;

            String ll[] = latlon.split(",");
            String lat = ll[1].replace("]", "").trim();
            // oh, again geoJson
            g.setNode(counter, Double.parseDouble(lat), Double.parseDouble(ll[0].trim()));
            counter++;
        }
        return counter;
    }

    @Test
    public void testParse() {
        Graph g = createGraph();
        parse(g, "[[11.571499218899739,49.945605917549265],[11.571664621792689,49.94570668665409]]");
        assertEquals(49.945605917549265, g.getLatitude(2), 1e-6);
        assertEquals(11.571499218899739, g.getLongitude(2), 1e-6);
        assertEquals(49.94570668665409, g.getLatitude(3), 1e-6);
        assertEquals(11.571664621792689, g.getLongitude(3), 1e-6);
    }

    @Test
    public void testPathSimplify() {
        Graph g = createGraph();
        int pointCount = parse(g, points1);
        Path path = new Path(g, FastestCarCalc.DEFAULT);
        for (int i = OFFSET; i < pointCount; i++) {
            path.add(i);
        }
        int deleted = path.simplify(new DouglasPeucker(g).setMaxDist(.5));
        assertEquals(Arrays.asList(2, 4, 6, 7, 8, 9, 12, 14, 15, 17, 18, 19, 20, 22, 24, 27, 28, 29, 31, 33), path.toNodeList());
        assertEquals(12, deleted);
    }

    @Test
    public void testSimplify() {
        Graph g = createGraph();
        int pointCount = parse(g, points1);
        DouglasPeucker dp = new DouglasPeucker(g).setMaxDist(.5);
        TIntArrayList points = new TIntArrayList();
        for (int i = OFFSET; i < pointCount; i++) {
            points.add(i);
        }

        dp.simplify(points);
//        String str = "{\"arr\":[";
//        for (int i = 0; i < points.size(); i++) {
//            int index = points.get(i);
//            if (index == DouglasPeucker.EMPTY)
//                continue;
//            if (index > 0)
//                str += ",";
//            str += "[" + g.getLongitude(index) + "," + g.getLatitude(index) + "]";
//        }
//        str += "]}";
//        System.out.println(str);

        // .5m
        assertEquals("{2, -1, 4, -1, 6, 7, 8, 9, -1, -1, 12, -1, 14, 15, -1, 17, 18, 19, 20, -1,"
                + " 22, -1, 24, -1, -1, 27, 28, 29, -1, 31, -1, 33}", points.toString());
        // 20m
//        assertEquals("{0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 16, -1, -1, -1, "
//                + "-1, -1, 22, -1, -1, -1, -1, -1, -1, -1, -1, 31}", points.toString());
    }
}
