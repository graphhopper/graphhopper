package com.graphhopper.reader.osgb.dpn;

import static com.graphhopper.util.GHUtility.count;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;

public class OsDpnReaderTest extends AbstractOsDpnReaderTest
{
    private GraphStorage graphStorage;

    @Test
    public void testReadDpnSampleLayout() throws IOException
    {
        configure(0);

        assertEquals(5, graphStorage.getNodes());

        assertEquals(4, count(footExplorer.setBaseNode(0))); // Central Tower
        assertEquals(1, count(footExplorer.setBaseNode(1))); // Cross Road Vertex
        assertEquals(1, count(footExplorer.setBaseNode(2))); // Cross Road Vertex
        assertEquals(1, count(footExplorer.setBaseNode(3))); // Cross Road Vertex
        assertEquals(1, count(footExplorer.setBaseNode(4))); // Cross Road Vertex

        // Assert that this is true
        EdgeIterator iter = footExplorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals(4, iter.getAdjNode());
        assertTrue(iter.next());
        assertEquals(3, iter.getAdjNode());
        assertTrue(iter.next());
        assertEquals(2, iter.getAdjNode());
        assertTrue(iter.next());
        assertEquals(1, iter.getAdjNode());
        assertFalse(iter.next());

        iter = footExplorer.setBaseNode(1);
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertFalse(iter.next());

        iter = footExplorer.setBaseNode(2);
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertFalse(iter.next());

        iter = footExplorer.setBaseNode(3);
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertFalse(iter.next());

        iter = footExplorer.setBaseNode(4);
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertFalse(iter.next());
    }

    @Test
    public void testDirectoryIngestion() throws IOException
    {
        configure(0, "directory_ingestion");

        assertEquals(5, graphStorage.getNodes());

        assertEquals(4, count(footExplorer.setBaseNode(0))); // Central Tower
        assertEquals(1, count(footExplorer.setBaseNode(1))); // Cross Road Vertex
        assertEquals(1, count(footExplorer.setBaseNode(2))); // Cross Road Vertex
        assertEquals(1, count(footExplorer.setBaseNode(3))); // Cross Road Vertex
        assertEquals(1, count(footExplorer.setBaseNode(4))); // Cross Road Vertex

        // Assert that this is true
        EdgeIterator iter = footExplorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals(4, iter.getAdjNode());
        assertTrue(iter.next());
        assertEquals(3, iter.getAdjNode());
        assertTrue(iter.next());
        assertEquals(2, iter.getAdjNode());
        assertTrue(iter.next());
        assertEquals(1, iter.getAdjNode());
        assertFalse(iter.next());

        iter = footExplorer.setBaseNode(1);
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertFalse(iter.next());

        iter = footExplorer.setBaseNode(2);
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertFalse(iter.next());

        iter = footExplorer.setBaseNode(3);
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertFalse(iter.next());

        iter = footExplorer.setBaseNode(4);
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertFalse(iter.next());
    }

    @Test
    public void testReadDpnSampleName() throws IOException
    {

        configure(0);
        EdgeIterator iter = footExplorer.setBaseNode(0);
        assertTrue(iter.next());
        assertTrue(iter.next());
        assertTrue(iter.next());
        assertTrue(iter.next());
        assertEquals("Name field available so should be set", "Named Road", iter.getName());
    }

    @Test
    public void testReadDpnSampleNameWithAlternate() throws IOException
    {
        configure(0);
        EdgeIterator iter = footExplorer.setBaseNode(0);
        assertTrue(iter.next());
        assertTrue(iter.next());
        assertTrue(iter.next());
        assertEquals("Name fields both available so should be set",
                "Named Road Two (With Alternate)", iter.getName());
    }

    @Test
    public void testReadDpnSampleNameDefaultToTrackType() throws IOException
    {
        configure(0);
        EdgeIterator iter = footExplorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals("No Name field available so should report track type", "Alley", iter.getName());
    }

    @Test
    public void testReadDpnSampleNameDefaultToTrackFriendlyNameWhenNoPhysicalManifestation()
            throws IOException
    {
        configure(0);
        EdgeIterator iter = footExplorer.setBaseNode(0);
        assertTrue(iter.next());
        assertTrue(iter.next());
        assertEquals("No Name field available so should be report track type", "Route",
                iter.getName());
    }

    @Test
    public void testReadDpnWayGeometry() throws IOException
    {
        configure(0);
        EdgeIterator iter = footExplorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals(1, iter.fetchWayGeometry(0).size());
        assertTrue(iter.next());
        assertEquals(1, iter.fetchWayGeometry(0).size());
        assertTrue(iter.next());
        assertEquals(5, iter.fetchWayGeometry(0).size());
        assertTrue(iter.next());
        assertEquals(8, iter.fetchWayGeometry(0).size());
        assertFalse(iter.next());

    }

    @Test
    public void testReadDpnWayGeometryWithSimplifiedWayGeometry() throws IOException
    {
        configure(1);
        EdgeIterator iter = footExplorer.setBaseNode(0);
        assertTrue(iter.next());
        assertEquals(1, iter.fetchWayGeometry(0).size());
        assertTrue(iter.next());
        assertEquals(1, iter.fetchWayGeometry(0).size());
        assertTrue(iter.next());
        assertEquals(4, iter.fetchWayGeometry(0).size());
        assertTrue(iter.next());
        assertEquals(5, iter.fetchWayGeometry(0).size());
        assertFalse(iter.next());

    }

    /**
     *
     * @param maxWayPointDistance 0 disables DouglasPeuker simplification 1 = graphhopper default 1 metre
     * @throws IOException
     */
    private void configure(int maxWayPointDistance) throws IOException {
        graphStorage = readGraph(maxWayPointDistance);
        GHUtility.printInfo(graphStorage, 0, 30, EdgeFilter.ALL_EDGES);
        configureExplorer(graphStorage);
    }

    private void configure(int maxWayPointDistance, String filename) throws IOException {
        graphStorage = readGraph(maxWayPointDistance, filename);
        GHUtility.printInfo(graphStorage, 0, 30, EdgeFilter.ALL_EDGES);
        configureExplorer(graphStorage);
    }

    private void configureExplorer(final GraphStorage graphStorage)
    {
        footExplorer = graphStorage.createEdgeExplorer(new DefaultEdgeFilter(footEncoder, true, true));
    }

    private GraphStorage readGraph(int maxWayPointDistance) throws IOException
    {
        return readGraph(maxWayPointDistance, "os-dpn-sample.xml");
    }

    private GraphStorage readGraph(int maxWayPointDistance, String filename) throws IOException
    {
        final boolean turnRestrictionsImport = false;
        final boolean is3D = false;
        final GraphHopperStorage graphStorage = configureStorage(turnRestrictionsImport, is3D);

        final File file = new File(
                "./src/test/resources/com/graphhopper/reader/osgb/dpn/" + filename);
        readGraphFile(graphStorage, file, maxWayPointDistance);
        return graphStorage;
    }

}
