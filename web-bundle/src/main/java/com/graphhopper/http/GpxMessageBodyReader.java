package com.graphhopper.http;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.graphhopper.jackson.Gpx;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.io.IOException;

@Provider
@Consumes({"application/gpx+xml", "application/xml"})
public class GpxMessageBodyReader implements MessageBodyReader<Gpx> {
    final private XmlMapper xmlMapper = new XmlMapper();
    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return type == Gpx.class;
    }

    @Override
    public Gpx readFrom(Class<Gpx> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                        MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException
    {
        return xmlMapper.readValue(entityStream, Gpx.class);
    }
}
