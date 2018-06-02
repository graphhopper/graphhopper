package com.graphhopper.http;

import com.graphhopper.MultiException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class MultiExceptionMapper implements ExceptionMapper<MultiException> {
    @Override
    public Response toResponse(MultiException exception) {
        return Response.status(400).entity(exception).build();
    }
}
