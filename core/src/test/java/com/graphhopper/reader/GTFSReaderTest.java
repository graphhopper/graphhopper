/*
 * Copyright 2013 Thomas Buerli <tbuerli@student.ethz.ch>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.reader;

import com.graphhopper.GHPublicTransit;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.PublicTransitFlagEncoder;
import com.graphhopper.storage.AbstractGraphTester;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author Thomas Buerli <tbuerli@student.ethz.ch>
 */
public class GTFSReaderTest {

    private String dir = "./target/tmp/";
    private String file2 = "test-gtfs2.zip";
    private PublicTransitFlagEncoder encoder = new PublicTransitFlagEncoder();
    private EdgeFilter outFilter = new DefaultEdgeFilter(encoder, false, true);
    private int defaultAlightTime = 240;

    @Before
    public void setUp() {
        new File(dir).mkdirs();
    }

    @After
    public void tearDown() {
        Helper.removeDir(new File(dir));
    }

    GraphStorage buildGraph( String directory, EncodingManager encodingManager ) {
        return new GraphStorage(new RAMDirectory(directory, false), encodingManager);
    }


    private File getFile(String file) throws URISyntaxException  {
        return new File(getClass().getResource(file).toURI());
    }

    private void setDefaultAlightTime(int time) {
        this.defaultAlightTime = time;
    }

    @Test
    public void testRead() {
        GHPublicTransit hopper = new GTFSReaderTest.GHPublicTransitTest(file2).importOrLoad();
        Graph graph = hopper.graph();
        assertEquals(142, graph.getNodes());
        assertEquals(0, AbstractGraphTester.getIdOf(graph, 36.915682));
        assertEquals(88, AbstractGraphTester.getIdOf(graph, 36.425288));
        assertEquals(72, AbstractGraphTester.getIdOf(graph, 36.868446));
        assertEquals(42, AbstractGraphTester.getIdOf(graph, 36.88108));
        assertEquals(2, GHUtility.count(graph.getEdges(0, outFilter)));

        PublicTransitFlagEncoder flags = encoder;
        EdgeIterator iter = graph.getEdges(0, outFilter);
        assertTrue(iter.next());

        // Exit node
        assertEquals(1, iter.getAdjNode());
        assertEquals(0, iter.getDistance(), 1e-3);
        assertTrue(flags.isExit(iter.getFlags()));
        assertTrue(flags.isForward(iter.getFlags()));
        assertFalse(flags.isBackward(iter.getFlags()));
        assertFalse(flags.isTransit(iter.getFlags()));
        assertFalse(flags.isBoarding(iter.getFlags()));
        assertFalse(flags.isAlight(iter.getFlags()));

        // Entry Node
        assertTrue(iter.next());
        assertEquals(21600, iter.getDistance(), 1);
        assertEquals(3, iter.getAdjNode());
        assertTrue(flags.isTransit(iter.getFlags()));
        assertTrue(flags.isEntry(iter.getFlags()));
        assertTrue(flags.isForward(iter.getFlags()));
        assertFalse(flags.isBackward(iter.getFlags()));
        assertFalse(flags.isBoarding(iter.getFlags()));
        assertFalse(flags.isAlight(iter.getFlags()));
        assertFalse(iter.next());

        iter = graph.getEdges(3, outFilter);
        assertTrue(iter.next());
        assertTrue(iter.next());
        assertEquals(2, iter.getAdjNode());
    }
    
    @Test
    public void testTransfer() {
        GHPublicTransit hopper = new GTFSReaderTest.GHPublicTransitTest(file2).importOrLoad();
        Graph graph = hopper.graph();
        
    }

    private class GHPublicTransitTest extends GHPublicTransit {

        private String testFile;

        private GHPublicTransitTest(String file) {
            this.testFile = file;
            setDefaultAlightTime(defaultAlightTime);
            setGraphHopperLocation(dir);
        }

        @Override
        protected GTFSReader importGTFS(String ignore) {
            
            File tmpFile;
            try {
                tmpFile = getFile(testFile);
            } catch (URISyntaxException ex) {
                throw new RuntimeException("Could not open " + testFile );
            }
            if (!tmpFile.exists()) {
                throw new IllegalStateException("Your specified GTFS file does not exist:" + tmpFile.getAbsolutePath());
            }
            EncodingManager manager = new EncodingManager("TRANSIT:com.graphhopper.routing.util.PublicTransitFlagEncoder");
            GTFSReader reader = new GTFSReader(buildGraph(dir, manager));
            try {
                reader.setDefaultAlightTime(defaultAlightTime);
                reader.load(tmpFile);
            } catch (IOException ex) {
                throw new RuntimeException("Could not load GTFS file ", ex);
            }
            return reader;

        }
    }
}