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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.CHProfileConfig;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.LMProfileConfig;
import com.graphhopper.util.CmdArgs;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

public class GraphHopperConfigDeserializer extends JsonDeserializer<GraphHopperConfig> {

    @Override
    public GraphHopperConfig deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        jsonParser.setCodec(mapper);
        ObjectNode tree = jsonParser.readValueAsTree();
        TreeNode chProfilesNode = tree.get("prepare.ch.profiles");
        TreeNode lmProfilesNode = tree.get("prepare.ch.profiles");
        List<CHProfileConfig> chProfiles = chProfilesNode != null ? mapper.convertValue(chProfilesNode, new TypeReference<List<CHProfileConfig>>() {
        }) : emptyList();
        List<LMProfileConfig> lmProfiles = lmProfilesNode != null ? mapper.convertValue(lmProfilesNode, new TypeReference<List<LMProfileConfig>>() {
        }) : emptyList();
        tree.remove("prepare.ch.profiles");
        tree.remove("prepare.lm.profiles");
        Map<String, String> map = mapper.convertValue(tree, new TypeReference<Map<String, String>>() {
        });
        GraphHopperConfig ghConfig = new GraphHopperConfig(new CmdArgs(map));
        ghConfig.addCHProfiles(chProfiles);
        ghConfig.addLMProfiles(lmProfiles);
        return ghConfig;
    }
}
