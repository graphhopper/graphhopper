package com.graphhopper.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.MultiException;
import com.graphhopper.PathWrapper;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;

public class GraphHopperModule extends SimpleModule {

    public GraphHopperModule() {
        setMixInAnnotation(GHRequest.class, GHRequestMixIn.class);
        addDeserializer(GHResponse.class, new GHResponseDeserializer());
        addDeserializer(PathWrapper.class, new PathWrapperDeserializer());
        addDeserializer(BBox.class, new BBoxDeserializer());
        addSerializer(BBox.class, new BBoxSerializer());
        addDeserializer(GHPoint.class, new GHPointDeserializer());
        addSerializer(GHPoint.class, new GHPointSerializer());
        addDeserializer(PathDetail.class, new PathDetailDeserializer());
        addSerializer(PathDetail.class, new PathDetailSerializer());
        addSerializer(InstructionList.class, new InstructionListSerializer());
        addDeserializer(CmdArgs.class, new CmdArgsDeserializer());
        addSerializer(MultiException.class, new MultiExceptionSerializer());
    }

}
