/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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
package com.graphhopper.routing;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.*;
import gnu.trove.list.TDoubleList;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.procedure.TDoubleProcedure;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class PathTest
{
    @Test
    public void testFound()
    {
        Path p = new Path(null, null);
        assertFalse(p.isFound());
        assertEquals(0, p.getDistance(), 1e-7);
        assertEquals(0, p.calcNodes().size());
    }

    @Test
    public void testTime()
    {
        FlagEncoder encoder = new EncodingManager("CAR").getEncoder("CAR");
        Path p = new Path(null, encoder);
        p.calcTime(100000, encoder.flags(100, true));
        assertEquals(60 * 60, p.getTime());
    }
}
