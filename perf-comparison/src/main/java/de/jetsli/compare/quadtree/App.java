package de.jetsli.compare.quadtree;

import de.jetsli.graph.reader.MiniPerfTest;
import de.jetsli.graph.reader.OSMReader;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.CmdArgs;
import de.jetsli.graph.util.Helper;

/**
 * Performance comparison of quadtree implementations
 */
public class App {

    // lucene/spatial4j -> we would need lucene
    // class LuceneTree implements SimplisticQuadTree {..}
    //
    public static void main(String[] args) throws Exception {        
        // OSM file - e.g:
        // http://download.geofabrik.de/osm/europe/germany/bayern/unterfranken.osm.bz2        
        new App().start(Helper.readCmdArgs(args));
    }

    public void start(CmdArgs args) throws Exception {
        Graph g = OSMReader.osm2Graph(args);
        System.out.println("graph contains " + g.getNodes() + " nodes");

//        for (int i = 0; i < 32; i++) {
//            System.out.println("\n\n #### skipLeft:" + i);
//            for (int j = 1; j < 32; j++) {
//                System.out.print("\nentriesPerBucket:" + j + "      ");
//                SimplisticQuadTree qt = new GHSpatialTree(i, j);
//                fillQuadTree(qt, g);
//                qt.toString();
//            }
//        }

//        runBenchmark(GHSpatialTree.class, g);
//         runBenchmark(SimpleArray.class, g);
        runBenchmark(GHTree.class, g);
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
        for (int iterDist = 10; iterDist < 50; iterDist *= 2) {
            final double dist = iterDist;
            new MiniPerfTest("query " + dist + "km " + qtClass.getSimpleName()) {

                @Override public long doCalc(int run) {
                    float lat = (random.nextInt(latMax - latMin) + latMin) / 10000.0f;
                    float lon = (random.nextInt(lonMax - lonMin) + lonMin) / 10000.0f;
                    return quadTree.countNodes(lat, lon, dist);
                }
            }.setMax(100).setSeed(0).start();
        }
    }

    void fillQuadTree(final SimplisticQuadTree qt, final Graph graph) {
        // this method is similar to: QuadTree.Util.fill(qt, graph);
        int locs = graph.getNodes();
        qt.init(locs);
        for (int i = 0; i < locs; i++) {
            qt.put(graph.getLatitude(i), graph.getLongitude(i));
        }
    }
}
