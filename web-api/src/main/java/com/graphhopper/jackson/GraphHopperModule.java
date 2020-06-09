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
package com.graphhopper.jackson;

import com.graphhopper.api.GHResponse;
import com.graphhopper.api.GraphHopperConfig;
import com.graphhopper.api.ResponsePath;
import com.graphhopper.api.GHRequest;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.graphhopper.*;
import com.graphhopper.api.config.LMProfile;
import com.graphhopper.api.config.Profile;
import com.graphhopper.api.util.InstructionList;
import com.graphhopper.api.util.details.PathDetail;
import com.graphhopper.api.util.shapes.BBox;
import com.graphhopper.api.util.shapes.GHPoint;

public class GraphHopperModule extends SimpleModule {

    public GraphHopperModule() {
        setMixInAnnotation(GHRequest.class, GHRequestMixIn.class);
        setMixInAnnotation(Profile.class, ProfileMixIn.class);
        setMixInAnnotation(LMProfile.class, LMProfileMixIn.class);
        setMixInAnnotation(GraphHopperConfig.class, GraphHopperConfigMixIn.class);
        addDeserializer(GHResponse.class, new GHResponseDeserializer());
        addDeserializer(ResponsePath.class, new ResponsePathDeserializer());
        addDeserializer(BBox.class, new BBoxDeserializer());
        addSerializer(BBox.class, new BBoxSerializer());
        addDeserializer(GHPoint.class, new GHPointDeserializer());
        addSerializer(GHPoint.class, new GHPointSerializer());
        addDeserializer(PathDetail.class, new PathDetailDeserializer());
        addSerializer(PathDetail.class, new PathDetailSerializer());
        addSerializer(InstructionList.class, new InstructionListSerializer());
        addSerializer(MultiException.class, new MultiExceptionSerializer());
    }

}
