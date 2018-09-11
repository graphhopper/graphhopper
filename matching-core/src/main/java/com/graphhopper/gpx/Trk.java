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
package com.graphhopper.gpx;

import com.graphhopper.util.GPXEntry;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@XmlType
public class Trk {

    @XmlElementWrapper(name="trkseg")
    @XmlElement(name="trkpt")
    @XmlJavaTypeAdapter(Adapter.class)
    public List<GPXEntry> trkpt;
    static class Adapter extends XmlAdapter<Trkpnt, GPXEntry> {
        @Override
        public GPXEntry unmarshal(Trkpnt trkpnt) {
            return new GPXEntry(trkpnt.lat, trkpnt.lon, trkpnt.ele, trkpnt.time != null ? trkpnt.time.getTime() : 0);
        }

        @Override
        public Trkpnt marshal(GPXEntry gpxEntry) {
            return new Trkpnt(gpxEntry.lat, gpxEntry.lon, gpxEntry.getEle(), gpxEntry.getTime());
        }
    }


    public List<GPXEntry> getEntries() {
        return trkpt;
    }

    // Pretend that everyone is declaring the correct namespace in their XML.
    static class XMLReaderWithFakeNamespace extends StreamReaderDelegate {
        public XMLReaderWithFakeNamespace(XMLStreamReader reader) {
            super(reader);
        }
        @Override
        public String getAttributeNamespace(int arg0) {
            return null;
        }
        @Override
        public String getNamespaceURI() {
            return "http://www.topografix.com/GPX/1/1";
        }
    }

    public static Trk doImport(String fileStr) {
        try (InputStream is = new FileInputStream(fileStr)) {
            XMLStreamReader xsr = XMLInputFactory.newFactory().createXMLStreamReader(is);
            XMLReaderWithFakeNamespace xr = new XMLReaderWithFakeNamespace(xsr);
            JAXBContext jaxbContext = JAXBContext.newInstance(Gpx.class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            Gpx gpx = (Gpx) jaxbUnmarshaller.unmarshal(xr);
            return gpx.trk;
        } catch (IOException | JAXBException | XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

}
