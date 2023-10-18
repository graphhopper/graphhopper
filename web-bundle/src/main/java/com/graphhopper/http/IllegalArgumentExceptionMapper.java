package com.graphhopper.http;

import com.graphhopper.jackson.MultiException;
import com.graphhopper.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
    private static final Logger logger = LoggerFactory.getLogger(IllegalArgumentExceptionMapper.class);

    @Override
    public Response toResponse(IllegalArgumentException e) {
        logger.info("bad request: " + (Helper.isEmpty(e.getMessage()) ? "unknown reason" : e.getMessage()), e);
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new MultiException(e))
                .build();
    }
}
