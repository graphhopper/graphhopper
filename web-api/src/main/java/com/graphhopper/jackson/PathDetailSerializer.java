package com.graphhopper.jackson;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.graphhopper.util.details.PathDetail;

import java.io.IOException;

public class PathDetailSerializer extends JsonSerializer<PathDetail> {

    @Override
    public void serialize(PathDetail value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartArray();

        gen.writeNumber(value.getFirst());
        gen.writeNumber(value.getLast());

        if (value.getValue() instanceof Double)
            gen.writeNumber((Double) value.getValue());
        else if (value.getValue() instanceof Long)
            gen.writeNumber((Long) value.getValue());
        else if (value.getValue() instanceof Integer)
            gen.writeNumber((Integer) value.getValue());
        else if (value.getValue() instanceof Boolean)
            gen.writeBoolean((Boolean) value.getValue());
        else if (value.getValue() instanceof String)
            gen.writeString((String) value.getValue());
        else
            throw new JsonGenerationException("Unsupported type for PathDetail.value" + value.getValue().getClass(), gen);

        gen.writeEndArray();
    }
}
