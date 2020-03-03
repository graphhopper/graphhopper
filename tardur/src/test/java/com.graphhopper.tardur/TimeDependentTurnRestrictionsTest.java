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

package com.graphhopper.tardur;

import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.jackson.Jackson;
import org.junit.Test;

public class TimeDependentTurnRestrictionsTest {

    @Test
    public void testTimeDependentTurnRestrictions() throws InterruptedException {
        GraphHopperConfig config = new GraphHopperConfig();
        config.put("datareader.file", "files/test-timedependent-turn-restrictions.xml");
        config.put("graph.flag_encoders", "car");
        config.put("graph.location", "wurst");
        GraphHopper graphHopper = new TardurGraphHopperManaged(config, Jackson.newObjectMapper()).getGraphHopper();
        graphHopper.importOrLoad();
    }

}
