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
package de.jetsli.graph.routing.util;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Peter Karich
 */
public class CarStreetTypeTest {

    @Test
    public void testBasics() {
        CarStreetType fl = new CarStreetType(CarStreetType.flagsDefault(true));
        assertTrue(fl.isForward());
        assertTrue(fl.isBackward());

        fl = new CarStreetType(CarStreetType.flagsDefault(false));
        assertTrue(fl.isForward());
        assertFalse(fl.isBackward());
        assertEquals(CarStreetType.DEFAULT_SPEED, fl.getSpeedPart());
    }

    @Test
    public void testSwapDir() {
        CarStreetType fl = new CarStreetType(CarStreetType.swapDirection(CarStreetType.flagsDefault(true)));
        assertTrue(fl.isForward());
        assertTrue(fl.isBackward());
        assertEquals(CarStreetType.DEFAULT_SPEED, fl.getSpeedPart());

        fl = new CarStreetType(CarStreetType.swapDirection(CarStreetType.flagsDefault(false)));
        assertFalse(fl.isForward());
        assertTrue(fl.isBackward());
        assertEquals(CarStreetType.DEFAULT_SPEED, fl.getSpeedPart());
    }

    @Test
    public void testService() {
        Map<String, Object> p = new HashMap<String, Object>();
        p.put("car", CarStreetType.SPEED.get("service"));
        CarStreetType fl = new CarStreetType(new AcceptStreet(true, false, false, false).toFlags(p));
        assertTrue(fl.isForward());
        assertTrue(fl.isBackward());
        assertTrue(fl.isService());
    }
}