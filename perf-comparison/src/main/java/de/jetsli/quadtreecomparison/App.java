package de.jetsli.quadtreecomparison;

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
        OSMReaderTrials osm = new OSMReaderTrials("/tmp/mmap-graph", 5 * 1000 * 1000);
        // use existing OR create new and overwrite old
        boolean createNew = false;
        if (createNew) {
            osm.init(true);
            osm.writeOsm2Binary(new FileInputStream(osmFile));
        } else {
            osm.init(false);
        }
        Graph g = osm.readGraph();
        System.out.println("graph contains " + g.getLocations() + " nodes");
        // runBenchmark(SimpleArray.class, g);
        runBenchmark(GHTree.class, g);
        runBenchmark(SISTree.class, g);
        runBenchmark(JTSTree.class, g);
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
//        }.setMax(20).start();

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
        for (int i = 10; i < 50; i *= 2) {
            final double dist = i;
            new MiniTest("query " + dist + " " + qtClass.getSimpleName()) {

                @Override public long doCalc(int run) {
                    float lat = (random.nextInt(latMax - latMin) + latMin) / 10000.0f;
                    float lon = (random.nextInt(lonMax - lonMin) + lonMin) / 10000.0f;
                    return quadTree.countNodes(lat, lon, dist);
                }
            }.setMax(20).start();
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
