package com.graphhopper.http;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.HttpHeaders;

@PreMatching
@Priority(Priorities.HEADER_DECORATOR)
public class TypeGPXFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext rc) {
        String maybeType = rc.getUriInfo().getQueryParameters().getFirst("type");
        if (maybeType != null && maybeType.equals("gpx")) {
            rc.getHeaders().putSingle(HttpHeaders.ACCEPT, "application/gpx+xml");
        }
    }

}
