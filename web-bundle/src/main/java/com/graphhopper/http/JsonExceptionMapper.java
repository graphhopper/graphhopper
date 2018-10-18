package com.graphhopper.http;

import com.fasterxml.jackson.core.JsonProcessingException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * This mapper should be used for debugging purposes only as it might exposes too much of the internals.
 */
public class JsonExceptionMapper implements ExceptionMapper<JsonProcessingException> {

    @Override
    public Response toResponse(JsonProcessingException exception) {
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("code", 400);

        {
            String message = exception.getOriginalMessage();
            if (message != null) {
                int pos = message.indexOf('\n');
                if (pos == -1) {
                    pos = message.indexOf(" at [Source");
                }
                if (pos > 0) {
                    message = message.substring(0, pos);
                }
                entity.put("message", message);
            }
        }

        if (exception.getLocation() != null) {
            entity.put("location", String.format(Locale.UK, "line %d, column %d",
                    exception.getLocation().getLineNr(),
                    exception.getLocation().getColumnNr()));
        }

        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(entity)
                .build();
    }
}
