/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.reader;

import com.graphhopper.reader.pbf.Sink;
import com.graphhopper.reader.pbf.PbfReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.util.LinkedList;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

/**
 * A readable OSM file.
 *
 * @author Nop
 */
public class OSMInputFile implements Sink
{

    private boolean eof;
    private InputStream bis;
    private boolean autoClose;

    // for xml parsing
    private XMLStreamReader parser;

    // for pbf parsing
    private boolean binary = false;
    private final LinkedList<OSMElement> itemQueue = new LinkedList<OSMElement>();
    private boolean incomingData;

    public OSMInputFile( File file ) throws IOException, XMLStreamException {
        final InputStream stream = decode( file );
        bis = stream;
        if( binary )
            openPBFReader( stream );
        else
            openXMLStream( stream );
        autoClose = true;
    }

    private InputStream decode( File file )
            throws IOException, XMLStreamException {
        final String name = file.getName();

        InputStream ips = null;
        try {
            ips = new BufferedInputStream( new FileInputStream( file ), 50000 );
        }
        catch( FileNotFoundException e ) {
            throw new RuntimeException( e );
        }
        ips.mark( 10 );

        // check file header
        byte header[] = new byte[6];
        ips.read( header );

        /*     can parse bz2 directly with additional lib
         if (header[0] == 'B' && header[1] == 'Z')
         {
         return new CBZip2InputStream(ips);
         }
         */
        if( header[0] == 31 && header[1] == -117 ) {
            ips.reset();
            return new GZIPInputStream( ips, 50000 );
        }
        else if( header[0] == 0 && header[1] == 0 && header[2] == 0
                && header[3] == 13 && header[4] == 10 && header[5] == 9 ) {
            ips.reset();
            binary = true;
            return ips;
        }
        else if( header[0] == 'P' && header[1] == 'K' ) {
            ips.reset();
            ZipInputStream zip = new ZipInputStream( ips );
            zip.getNextEntry();

            return zip;
        }
        else if( name.endsWith( ".osm" ) || name.endsWith( ".xml" ) ) {
            ips.reset();
            return ips;
        }
        else {
            throw new IllegalArgumentException( "Input file is not of valid type " + file.getPath() );
        }
    }

    public OSMInputFile( InputStream in ) throws XMLStreamException {
        openXMLStream( in );
    }

    private void openXMLStream( InputStream in )
            throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        parser = factory.createXMLStreamReader( bis, "UTF-8" );

        int event = parser.next();
        if( event != XMLStreamConstants.START_ELEMENT || !parser.getLocalName().equalsIgnoreCase( "osm" ) ) {
            throw new IllegalArgumentException( "File is not a valid OSM stream" );
        }

        eof = false;
    }

    private int mq = 0;

    public OSMElement getNext() throws XMLStreamException {
        if( eof ) {
            throw new IllegalStateException( "EOF reached" );
        }

        OSMElement item = null;
        if( binary ) {
            item = getNextPBF();
        }
        else {
            item = getNextXML();
        }
        if( item != null ) {
            return item;
        }

        eof = true;
        return null;
    }

    private OSMElement getNextXML() throws XMLStreamException {

        int event = parser.next();
        while( event != XMLStreamConstants.END_DOCUMENT ) {
            if( event == XMLStreamConstants.START_ELEMENT ) {
                String name = parser.getLocalName();
                long id = 0;
                switch( name.charAt( 0 ) ) {
                    case 'n':
                        id = Long.parseLong( parser.getAttributeValue( null, "id" ) );
                        return new OSMNode( id, parser );

                    case 'w': {
                        id = Long.parseLong( parser.getAttributeValue( null, "id" ) );
                        return new OSMWay( id, parser );
                    }
                    case 'r':
                        id = Long.parseLong( parser.getAttributeValue( null, "id" ) );
                        return new OSMRelation( id, parser );
                }
            }
            event = parser.next();
        }
        parser.close();
        return null;
    }

    public boolean eof() {
        return eof;
    }

    public void close() throws XMLStreamException, IOException {
        if( !binary )
            parser.close();
        eof = true;
        if( autoClose ) {
            bis.close();
        }
    }

    private void openPBFReader( InputStream stream ) {
        incomingData = true;
        PbfReader reader = new PbfReader( stream, this, 2 );
        new Thread( reader, "PBF Reader" ).start();
    }

    @Override
    public void process( OSMElement item ) {
        synchronized( itemQueue ) {
            itemQueue.addLast( item );
            itemQueue.notifyAll();
            // keep queue from overrunning
            if( itemQueue.size() > 50000 ) {
                try {
                    itemQueue.wait();
                }
                catch( InterruptedException e ) {
                    // ignore
                }
            }
        }
    }

    @Override
    public void complete() {
        synchronized( itemQueue ) {
            incomingData = false;
            itemQueue.notifyAll();
        }
    }

    private OSMElement getNextPBF() {
        OSMElement next = null;
        do {
            synchronized( itemQueue ) {
                // try to read next object
                next = itemQueue.pollFirst();

                if( next == null ) {
                    // if we have no items to process but parser is still working: wait
                    if( incomingData )
                        try {
                            itemQueue.wait();
                        }
                        catch( InterruptedException e ) {
                            // ignored
                        }
                        // we are done, stop waiting
                    else
                        break;
                }
                else
                    itemQueue.notifyAll();
            }
        }
        while( next == null );

        return next;
    }

}