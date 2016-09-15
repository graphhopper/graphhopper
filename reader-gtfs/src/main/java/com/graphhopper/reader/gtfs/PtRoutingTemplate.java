package com.graphhopper.reader.gtfs;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.routing.template.ViaRoutingTemplate;
import com.graphhopper.storage.index.LocationIndex;

class PtRoutingTemplate extends ViaRoutingTemplate {

	PtRoutingTemplate(GHRequest ghRequest, GHResponse ghRsp, LocationIndex locationIndex) {
		super(ghRequest, ghRsp, locationIndex);
	}

}
