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

package com.graphhopper.gtfs;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;

public class PtEncodedValues {

    private BooleanEncodedValue accessEnc;
    private IntEncodedValue timeEnc;
    private IntEncodedValue transfersEnc;
    private IntEncodedValue validityIdEnc;
    private EnumEncodedValue<GtfsStorage.EdgeType> typeEnc;

    private PtEncodedValues(EncodingManager encodingManager) {
        accessEnc = encodingManager.getBooleanEncodedValue("is_forward_pt_edge");
        validityIdEnc = encodingManager.getIntEncodedValue("pt_validity_id");
        transfersEnc = encodingManager.getIntEncodedValue("pt_transfers");
        typeEnc = encodingManager.getEnumEncodedValue("pt_edge_type", GtfsStorage.EdgeType.class);
        timeEnc = encodingManager.getIntEncodedValue("pt_time");
    }

    public static PtEncodedValues fromEncodingManager(EncodingManager encodingManager) {
        return new PtEncodedValues(encodingManager);
    }

    public static EncodingManager.Builder createAndAddEncodedValues(EncodingManager.Builder builder) {
        builder.add(new SimpleBooleanEncodedValue("is_forward_pt_edge", true));
        builder.add(new IntEncodedValueImpl("pt_validity_id", 20, false));
        builder.add(new IntEncodedValueImpl("pt_transfers", 1, false));
        builder.add(new EnumEncodedValue<>("pt_edge_type", GtfsStorage.EdgeType.class));
        builder.add(new IntEncodedValueImpl("pt_time", 17, false));
        return builder;
    }

    public IntEncodedValue getTimeEnc() {
        return timeEnc;
    }

    public IntEncodedValue getTransfersEnc() {
        return transfersEnc;
    }

    public IntEncodedValue getValidityIdEnc() {
        return validityIdEnc;
    }

    public EnumEncodedValue<GtfsStorage.EdgeType> getTypeEnc() {
        return typeEnc;
    }

    public BooleanEncodedValue getAccessEnc() {
        return accessEnc;
    }

}
