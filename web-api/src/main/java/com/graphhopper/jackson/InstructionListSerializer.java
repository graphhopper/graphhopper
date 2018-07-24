package com.graphhopper.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.graphhopper.util.InstructionList;

import java.io.IOException;

public class InstructionListSerializer extends JsonSerializer<InstructionList> {
    @Override
    public void serialize(InstructionList instructions, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeObject(instructions.createJson());
    }
}
