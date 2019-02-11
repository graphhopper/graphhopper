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
package com.graphhopper.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Peter Karich
 */
public class DouglasPeuckerTest {

    // get some real life points from graphhopper API
    // http://217.92.216.224:8080/?point=49.945642,11.571436&point=49.946001,11.580706
    String points1 = "[[11.571499218899739,49.945605917549265],[11.571664621792689,49.94570668665409],[11.571787742639804,49.94578156499077],[11.572065649302282,49.94590338198625],[11.572209445511016,49.94595944760649],[11.57229438213172,49.94598850487147],"
            + "[11.573315297960832,49.946237913062525],[11.57367665112786,49.946338495902836],[11.573895511937787,49.94641784458796],[11.574013417378367,49.94646347939514],[11.574228180368875,49.94654916107392],[11.574703899950622,49.94677509993557],"
            + "[11.575003599561832,49.946924670344394],[11.575434615658997,49.94711838544425],[11.575559971680342,49.94716010869652],[11.57563783024932,49.947186185729194],[11.57609697228887,49.94727875919518],[11.57656188852851,49.947290121330845],"
            + "[11.576840167720023,49.94727782787258],[11.576961425921949,49.94725827009808],[11.577226852861648,49.947215242994176],[11.577394863457863,49.94717668623872],[11.577511092517772,49.94715005041249],[11.577635517216523,49.947112238715114],"
            + "[11.577917149169382,49.94702655703634],[11.577969116970207,49.947010724552214],[11.578816061738493,49.94673523932849],[11.579533552666014,49.94648974269233],[11.580073719771365,49.946299007824784],[11.580253092503245,49.946237913062525],"
            + "[11.580604946179799,49.94608871518274],[11.580740546749693,49.94603041438826]]";
    String points2 = "[[9.961074440801317,50.203764443183644],[9.96106605889796,50.20365789987872],[9.960999562464645,50.20318963087774],[9.96094144793469,50.202952888673984],[9.96223002587773,50.20267889356641],[9.962200968612752,50.20262022024289],"
            + "[9.961859918278305,50.201853928011374],[9.961668810881722,50.20138565901039],[9.96216874485095,50.20128507617008],[9.961953795595925,50.20088553877664],[9.961899033827313,50.200686794534775],[9.961716680863127,50.20014066696481],[9.961588158344957,50.199798499043254]]";

    @Test
    public void testParse() {
        PointList pointList = new PointList();
        pointList.parse2DJSON("[[11.571499218899739,49.945605917549265],[11.571664621792689,49.94570668665409]]");
        assertEquals(49.945605917549265, pointList.getLatitude(0), 1e-6);
        assertEquals(11.571499218899739, pointList.getLongitude(0), 1e-6);
        assertEquals(49.94570668665409, pointList.getLatitude(1), 1e-6);
        assertEquals(11.571664621792689, pointList.getLongitude(1), 1e-6);
    }

    @Test
    public void testPathSimplify() {
        PointList pointList = new PointList();
        pointList.parse2DJSON(points1);
        assertEquals(32, pointList.getSize());
        new DouglasPeucker().setMaxDistance(.5).simplify(pointList);
        // Arrays.asList(2, 4, 6, 7, 8, 9, 12, 14, 15, 17, 18, 19, 20, 22, 24, 27, 28, 29, 31, 33),
        assertEquals(20, pointList.getSize());
    }

    @Test
    public void testSimplifyCheckPointCount() {
        PointList pointList = new PointList();
        pointList.parse2DJSON(points1);
        DouglasPeucker dp = new DouglasPeucker().setMaxDistance(.5);
        assertEquals(32, pointList.getSize());
        dp.simplify(pointList);
        assertEquals(20, pointList.getSize());
        assertFalse(pointList.toString(), pointList.toString().contains("NaN"));

        pointList.clear();
        pointList.parse2DJSON(points1);
        dp.simplify(pointList, 0, pointList.size() -1);
        assertEquals(20, pointList.getSize());

        pointList.clear();
        pointList.parse2DJSON(points1);
        int removed1 = dp.simplify(pointList.copy(10, 20));

        pointList.clear();
        pointList.parse2DJSON(points1);
        int removed2 = dp.simplify(pointList, 10, 19);

        assertEquals(removed1, removed2);
    }

    @Test
    public void testSimplifyCheckPointOrder() {
        PointList pointList = new PointList();
        pointList.parse2DJSON(points2);
        assertEquals(13, pointList.getSize());
        new DouglasPeucker().setMaxDistance(.5).simplify(pointList);
        assertEquals(11, pointList.getSize());
        assertFalse(pointList.toString(), pointList.toString().contains("NaN"));
        assertEquals("(50.203764443183644,9.961074440801317), (50.20318963087774,9.960999562464645), (50.202952888673984,9.96094144793469), (50.20267889356641,9.96223002587773), (50.201853928011374,9.961859918278305), "
                        + "(50.20138565901039,9.961668810881722), (50.20128507617008,9.96216874485095), (50.20088553877664,9.961953795595925), (50.200686794534775,9.961899033827313), (50.20014066696481,9.961716680863127), (50.199798499043254,9.961588158344957)",
                pointList.toString());
    }
}
