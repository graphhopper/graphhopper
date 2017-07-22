package com.graphhopper.routing;

import com.graphhopper.routing.bwdcompat.AnnotationAccessor;
import com.graphhopper.routing.bwdcompat.BoolAccessor;
import com.graphhopper.routing.bwdcompat.SpeedAccessor;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EncodingManager;
import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.routing.util.EncodingManager08;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.*;

public class PathExtract {
    private Path path;
    private SpeedAccessor speedAccessor;
    private BoolAccessor roundaboutAccess;

    PathExtract(Path path, EncodingManager08 encodingManager) {
        this.path = path;

        try {
            final FlagEncoder encoder = path.weighting.getFlagEncoder();
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
        } catch (Exception ex) {
            EncodingManager em2 = (EncodingManager) encodingManager;
            final DecimalEncodedValue maxspeed = em2.getEncodedValue("maxspeed", DecimalEncodedValue.class);
            speedAccessor = new SpeedAccessor() {

                @Override
                public double getSpeed(EdgeIteratorState edge) {
                    return edge.get(maxspeed);
                }
            };
            final BooleanEncodedValue roundabout = em2.getEncodedValue("roundabout", BooleanEncodedValue.class);
            roundaboutAccess = new BoolAccessor() {
                @Override
                public boolean get(EdgeIteratorState edge) {
                    return edge.get(roundabout);
                }
            };
        }
    }

    AnnotationAccessor createAnnotationAccessor(final Translation tr) {
        // TODO ugly hack to support instructions for EncodingManager where no FlagEncoders exist
        try {
            final FlagEncoder encoder = path.weighting.getFlagEncoder();
            return new AnnotationAccessor() {
                @Override
                public InstructionAnnotation get(EdgeIteratorState edge) {
                    return encoder.getAnnotation(edge.getFlags(), tr);
                }
            };
        } catch (Exception ex) {
            return new AnnotationAccessor() {
                @Override
                public InstructionAnnotation get(EdgeIteratorState edge) {
                    return InstructionAnnotation.EMPTY;
                }
            };
        }
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
