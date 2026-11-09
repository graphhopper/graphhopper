package com.graphhopper.http;

import com.fasterxml.jackson.databind.JsonMappingException;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Returns the message of an IllegalArgumentException thrown while reading the JSON of a request,
 * e.g. for an invalid custom model parameter, instead of just "Unable to process JSON".
 */
@Provider
public class JsonIllegalArgumentExceptionMapper implements ExceptionMapper<JsonMappingException> {
    private final IllegalArgumentExceptionMapper illegalArgumentMapper =
            new IllegalArgumentExceptionMapper();
    private final JsonProcessingExceptionMapper defaultMapper = new JsonProcessingExceptionMapper();

    @Override
    public Response toResponse(JsonMappingException e) {
        return e.getCause() instanceof IllegalArgumentException cause
                ? illegalArgumentMapper.toResponse(cause) : defaultMapper.toResponse(e);
    }
}
