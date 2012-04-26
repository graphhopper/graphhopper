package de.jetsli.quadtreecomparison;

import de.jetsli.graph.geohash.SpatialKeyTree;
import de.jetsli.graph.reader.MiniTest;
import de.jetsli.graph.reader.OSMReaderTrials;
import de.jetsli.graph.storage.Graph;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * Performance comparison of quadtree implementations
 */
public class App {

    // lucene/spatial4j -> we would need lucene
    // class LuceneTree implements SimplisticQuadTree {..}
    //
    public static void main(String[] args) throws Exception {
        if (args.length < 1)
            throw new IllegalStateException("Please specify a filename to the OSM file");

        // OSM file - e.g:
        // http://download.geofabrik.de/osm/europe/germany/bayern/unterfranken.osm.bz2        
        new App().start(args[0]);
    }

    public void start(String osmFile) throws FileNotFoundException {
        Graph g = OSMReaderTrials.defaultRead(osmFile, "/tmp/mmap-graph");
        System.out.println("graph contains " + g.getLocations() + " nodes");

//        for (int i = 0; i < 32; i++) {
//            System.out.println("\n\n #### skipLeft:" + i);
//            for (int j = 1; j < 32; j++) {
//                System.out.print("\nentriesPerBucket:" + j + "      ");
//                SimplisticQuadTree qt = new GHSpatialTree(i, j);
//                fillQuadTree(qt, g);
//                qt.toString();
//            }
//        }

        runBenchmark(GHSpatialTree.class, g);
//         runBenchmark(SimpleArray.class, g);
//        runBenchmark(GHTree.class, g);
//        runBenchmark(SISTree.class, g);
//        runBenchmark(JTSTree.class, g);
    }

    void runBenchmark(final Class qtClass, final Graph graph) {
        // test quadtree CONSTRUCTION performance
//        new MiniTest("fill " + qtClass.getSimpleName()) {
//
//            @Override public long doCalc(int run) {
//                try {
//                    SimplisticQuadTree quadTree = (SimplisticQuadTree) qtClass.newInstance();
//                    fillQuadTree(quadTree, graph);
//                    return quadTree.size();
//                } catch (Exception ex) {
//                    throw new RuntimeException(ex);
//                }
//            }
//        }.setMax(20).setSeed(0).start();

        // test neighbor SEARCH performance
        final SimplisticQuadTree quadTree;
        try {
            quadTree = (SimplisticQuadTree) qtClass.newInstance();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        fillQuadTree(quadTree, graph);
        final int latMin = 497354, latMax = 501594;
        final int lonMin = 91924, lonMax = 105784;

        // sis is a memory hog because there is no difference of data+branchnode
        // long emptyEntries = quadTree.getEmptyEntries(true);
        // long emptyAllEntries = quadTree.getEmptyEntries(false);
        // + " empty all entries:" + emptyAllEntries + " empty entries:" + emptyEntries
        for (int i = 10; i < 50; i *= 2) {
            final double dist = i;
            new MiniTest("query " + dist + "km " + qtClass.getSimpleName()) {

                @Override public long doCalc(int run) {
                    float lat = (random.nextInt(latMax - latMin) + latMin) / 10000.0f;
                    float lon = (random.nextInt(lonMax - lonMin) + lonMin) / 10000.0f;
                    return quadTree.countNodes(lat, lon, dist);
                }
            }.setMax(50).setSeed(0).start();
        }
    }

    void fillQuadTree(final SimplisticQuadTree qt, final Graph graph) {
        int locs = graph.getLocations();
        qt.init(locs);
        for (int i = 0; i < locs; i++) {
            qt.put(graph.getLatitude(i), graph.getLongitude(i));
        }
    }
}
