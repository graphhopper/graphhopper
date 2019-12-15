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

package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.routing.profiles.UnsignedIntEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;

import java.util.List;

public class OSMIDParser implements TagParser {

    private IntEncodedValue[] byteEncodedValue = new UnsignedIntEncodedValue[8];

    public OSMIDParser() {
    }

    private OSMIDParser(EncodingManager encodingManager) {
        for (int i=0; i<8; i++) {
            byteEncodedValue[i] = encodingManager.getIntEncodedValue("osm_id_byte_"+i);
        }
    }

    public static OSMIDParser fromEncodingManager(EncodingManager encodingManager) {
        return new OSMIDParser(encodingManager);
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        for (int i=0; i<8; i++) {
            byteEncodedValue[i] = new UnsignedIntEncodedValue("osm_id_byte_"+i, 8, false);
            registerNewEncodedValue.add(byteEncodedValue[i]);
        }
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access, IntsRef relationFlags) {
        byte[] osmIdBytes = longToBytes(way.getId());
        for (int i=0; i<8; i++) {
            byteEncodedValue[i].setInt(false, edgeFlags, toUnsignedInt(osmIdBytes[i]));
        }
        return edgeFlags;
    }

    public static int toUnsignedInt(byte x) {
        return ((int) x) & 0xff;
    }

    public long getOSMID(IntsRef edgeFlags) {
        byte[] osmIdBytes = new byte[8];
        for (int i=0; i<8; i++) {
            osmIdBytes[i] = (byte) byteEncodedValue[i].getInt(false, edgeFlags);;
        }
        return bytesToLong(osmIdBytes);
    }

    public static byte[] longToBytes(long l) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte)(l & 0xFF);
            l >>= 8;
        }
        return result;
    }

    public static long bytesToLong(byte[] b) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result <<= 8;
            result |= (b[i] & 0xFF);
        }
        return result;
    }

}
