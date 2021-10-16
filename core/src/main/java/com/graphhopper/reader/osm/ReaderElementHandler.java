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

package com.graphhopper.reader.osm;

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;

import java.text.ParseException;

interface ReaderElementHandler {
    default void handleElement(ReaderElement elem) throws ParseException {
        switch (elem.getType()) {
            case ReaderElement.NODE:
                handleNode((ReaderNode) elem);
                break;
            case ReaderElement.WAY:
                handleWay((ReaderWay) elem);
                break;
            case ReaderElement.RELATION:
                handleRelation((ReaderRelation) elem);
                break;
            case ReaderElement.FILEHEADER:
                handleFileHeader((OSMFileHeader) elem);
                break;
            default:
                throw new IllegalStateException("Unknown reader element type: " + elem.getType());
        }
    }

    default void handleNode(ReaderNode node) {
    }

    default void handleWay(ReaderWay way) {
    }

    default void handleRelation(ReaderRelation relation) {
    }

    default void handleFileHeader(OSMFileHeader fileHeader) throws ParseException {
    }

    default void onFinish() {
    }
}
