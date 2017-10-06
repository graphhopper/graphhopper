/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.http;

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.inject.Provides;
import com.google.inject.servlet.ServletModule;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.details.PathDetail;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Peter Karich
 */
public class GraphHopperServletModule extends ServletModule {
    protected final CmdArgs args;
    protected Map<String, String> params = new HashMap<String, String>();

    public GraphHopperServletModule(CmdArgs args) {
        this.args = args;
        params.put("mimeTypes", "text/html,"
                + "text/plain,"
                + "text/xml,"
                + "application/xhtml+xml,"
                + "application/gpx+xml,"
                + "application/xml,"
                + "text/css,"
                + "application/json,"
                + "application/javascript,"
                + "image/svg+xml");
    }

    @Override
    protected void configureServlets() {
        filter("*").through(HeadFilter.class);
        bind(HeadFilter.class).in(Singleton.class);

        filter("*").through(CORSFilter.class, params);
        bind(CORSFilter.class).in(Singleton.class);

        filter("*").through(IPFilter.class);
        bind(IPFilter.class).toInstance(new IPFilter(args.get("jetty.whiteips", ""), args.get("jetty.blackips", "")));

        serve("/i18n*").with(I18NServlet.class);
        bind(I18NServlet.class).in(Singleton.class);

        serve("/info*").with(InfoServlet.class);
        bind(InfoServlet.class).in(Singleton.class);

        serve("/route*").with(GraphHopperServlet.class);
        bind(GraphHopperServlet.class).in(Singleton.class);

        serve("/nearest*").with(NearestServlet.class);
        bind(NearestServlet.class).in(Singleton.class);

        if (args.getBool("web.change_graph.enabled", false)) {
            serve("/change*").with(ChangeGraphServlet.class);
            bind(ChangeGraphServlet.class).in(Singleton.class);
        }

        // Can't do this because otherwise we can't add more paths _after_ this module.
        // Instead, put this route explicitly into Jetty.
        // (We really need a web service framework.)
        // serve("/*").with(InvalidRequestServlet.class);
        bind(InvalidRequestServlet.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setDateFormat(new ISO8601DateFormat());
        objectMapper.registerModule(new JtsModule());

        SimpleModule pathDetailModule = new SimpleModule();
        pathDetailModule.addSerializer(PathDetail.class, new PathDetailSerializer());
        pathDetailModule.addDeserializer(PathDetail.class, new PathDetailDeserializer());
        objectMapper.registerModule(pathDetailModule);

        // Because VirtualEdgeIteratorState has getters which throw Exceptions.
        // http://stackoverflow.com/questions/35359430/how-to-make-jackson-ignore-properties-if-the-getters-throw-exceptions
        objectMapper.registerModule(new SimpleModule().setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties) {
                return beanProperties.stream().map(bpw -> new BeanPropertyWriter(bpw) {
                    @Override
                    public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
                        try {
                            super.serializeAsField(bean, gen, prov);
                        } catch (Exception e) {
                            // Ignoring expected exception, see above.
                        }
                    }
                }).collect(Collectors.toList());
            }
        }));
        return objectMapper;
    }

    public static class PathDetailSerializer extends JsonSerializer<PathDetail> {

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

    public static class PathDetailDeserializer extends JsonDeserializer<PathDetail> {

        @Override
        public PathDetail deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            JsonNode pathDetail = jp.readValueAsTree();
            if (pathDetail.size() != 3)
                throw new JsonParseException(jp, "PathDetail array must have exactly 3 entries but was " + pathDetail.size());

            JsonNode from = pathDetail.get(0);
            JsonNode to = pathDetail.get(1);
            JsonNode val = pathDetail.get(2);

            PathDetail pd;
            if (val.isBoolean())
                pd = new PathDetail(val.asBoolean());
            else if (val.isLong())
                pd = new PathDetail(val.asLong());
            else if (val.isDouble())
                pd = new PathDetail(val.asDouble());
            else if (val.isTextual())
                pd = new PathDetail(val.asText());
            else
                throw new JsonParseException(jp, "Unsupported type of PathDetail value " + pathDetail.getNodeType().name());

            pd.setFirst(from.asInt());
            pd.setLast(to.asInt());
            return pd;
        }
    }
}