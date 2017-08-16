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
package com.graphhopper.routing;

import com.graphhopper.routing.bwdcompat.AnnotationAccessor;
import com.graphhopper.routing.bwdcompat.BoolAccessor;
import com.graphhopper.routing.bwdcompat.SpeedAccessor;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.*;

public class PathExtract {
    private Path path;
    private SpeedAccessor speedAccessor;
    private BoolAccessor roundaboutAccess;

    PathExtract(Path path, EncodingManager encodingManager) {
        this.path = path;
        final FlagEncoder encoder = path.weighting.getFlagEncoder();
        if (encoder != null) {
            if (encoder instanceof DataFlagEncoder) {
                final DataFlagEncoder dataFlagEncoder = (DataFlagEncoder) encoder;
                speedAccessor = new SpeedAccessor() {
                    public double getSpeed(EdgeIteratorState edge) {
                        return dataFlagEncoder.getMaxspeed(edge, 0, false);
                    }
                };
            } else {
                speedAccessor = new SpeedAccessor() {
                    public double getSpeed(EdgeIteratorState edge) {
                        return encoder.getSpeed(edge.getFlags());
                    }
                };
            }

            roundaboutAccess = new BoolAccessor() {
                @Override
                public boolean get(EdgeIteratorState edge) {
                    return encoder.isBool(edge.getFlags(), FlagEncoder.K_ROUNDABOUT);
                }
            };
        } else {
            final DecimalEncodedValue maxspeed = encodingManager.getEncodedValue("maxspeed", DecimalEncodedValue.class);
            speedAccessor = new SpeedAccessor() {

                @Override
                public double getSpeed(EdgeIteratorState edge) {
                    return edge.get(maxspeed);
                }
            };
            final BooleanEncodedValue roundabout = encodingManager.getEncodedValue("roundabout", BooleanEncodedValue.class);
            roundaboutAccess = new BoolAccessor() {
                @Override
                public boolean get(EdgeIteratorState edge) {
                    return edge.get(roundabout);
                }
            };
        }
    }

    AnnotationAccessor createAnnotationAccessor(final Translation tr) {
        final FlagEncoder encoder = path.weighting.getFlagEncoder();
        if (encoder == null)
            return new AnnotationAccessor() {
                @Override
                public InstructionAnnotation get(EdgeIteratorState edge) {
                    return InstructionAnnotation.EMPTY;
                }
            };

        return new AnnotationAccessor() {
            @Override
            public InstructionAnnotation get(EdgeIteratorState edge) {
                return encoder.getAnnotation(edge.getFlags(), tr);
            }
        };
    }

    /**
     * @return the list of instructions for this path.
     */
    public InstructionList calcInstructions(final Translation tr) {
        final InstructionList ways = new InstructionList(path.edgeIds.size() / 4, tr);
        if (path.edgeIds.isEmpty()) {
            if (path.isFound()) {
                ways.add(new FinishInstruction(path.nodeAccess, path.endNode));
            }
            return ways;
        }
        path.forEveryEdge(new InstructionsFromEdges(path.getFromNode(), path.graph, path.weighting,
                path.nodeAccess, createAnnotationAccessor(tr), speedAccessor, roundaboutAccess, ways));
        return ways;
    }
}
