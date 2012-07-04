/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.reader;

import de.jetsli.graph.geohash.SpatialHashtable;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.trees.QuadTree;
import de.jetsli.graph.util.Helper;
import java.util.Date;

/**
 * This class tests SpatialHashtable ... for the performance comparison of different quadtree
 * implementations see the subproject perf-comparison!
 * 
 * Memory usage calculation according to
 *
 * http://www.ibm.com/developerworks/opensource/library/j-codetoheap/index.html?ca=drs
 * http://kohlerm.blogspot.de/2009/02/how-to-really-measure-memory-usage-of.html
 *
 * TODO respect padding:
 *
 * http://www.codeinstructions.com/2008/12/java-objects-memory-structure.html
 *
 * @author Peter Karich
 */
public class PerfTest {

    public static void main(String[] args) throws Exception {
        Graph g = OSMReader.osm2Graph(Helper.readCmdArgs(args));
        new PerfTest(g).start();
    }
    Graph g;

    public PerfTest(Graph graph) {
        g = graph;
    }
    int latMin = 497354, latMax = 501594;
    int lonMin = 91924, lonMax = 105784;
    // Try to use MemoryMeter https://github.com/jbellis/jamm

    {
        // QuadTreeSimple (with spatial key)
        // for fill: 1.82sec/iter
        // for query: 16 entriesPerNode seems to be fast and not such a memory waste
        // => approx 46 bytes/entry + sizeOf(Integer)
        // current results for 64 bits:
        // 10km search => 0.048s, ~  70k nodes per search retrieved
        // 20km search => 0.185s, ~ 300k
        // 40km search => 0.620s, ~ 850k
        // increase speed about
        //  => ~2%    when using int   instead double    in BBox (multiplied with 1e+7 before) => but too complicated
        //  => ~2%    when using float instead of double in CoordTrig => but bad in other cases. if double and float implementation => too complicated
        //  => ~10%   when using int   instead double    in SpatialKeyAlgo for de/encode => but problems with precision if allBits >= 46
        //  => ~30%   when using int   instead long      in SpatialKeyAlgo for de/encode => but problems with precision if allBits >= 46
        //  => ~1000% when using only 32 bits for encoding instead >=48
    }

    public void start() {
        System.out.println("locations:" + g.getNodes());
        int maxDist = 20;
        int maxEntriesPerL = 30;
        int minBits = 4;
        System.out.println(new Date() + "# maxDist:" + maxDist + ", maxEntries/leaf:" + maxEntriesPerL + ", minBits:" + minBits);

//        measureFill(minBits, maxEntriesPerL);
        measureSearch(minBits, maxDist, maxEntriesPerL);
    }

    private void measureFill(int minBits, int maxEPerL) {
        for (int bits = minBits; bits <= 30; bits += 2) {
            int entriesPerLeaf = 3;
//            for (; entriesPerLeaf < maxEPerL; entriesPerLeaf *= 2) {
            //final QuadTree<Long> quadTree = new QuadTreeSimple<Long>(entriesPerLeaf, bits);
            final QuadTree<Long> quadTree = new SpatialHashtable(bits, entriesPerLeaf).init(g.getNodes());
            QuadTree.Util.fill(quadTree, g);
            System.gc();
            System.gc();
            float mem = (float) quadTree.getMemoryUsageInBytes(1) / Helper.MB;
            quadTree.clear();
            System.out.println(new Date() + "# entries/leaf:" + entriesPerLeaf + ", bits:" + bits + ", mem:" + mem);
            final int epl = entriesPerLeaf;
            final int b = bits;
            new MiniPerfTest("fill") {

                @Override public long doCalc(int run) {
                    //QuadTree<Long> quadTree = new QuadTreeSimple<Long>(epl, b);
                    QuadTree<Long> quadTree = new SpatialHashtable(b, epl).init(g.getNodes());
                    QuadTree.Util.fill(quadTree, g);
                    return quadTree.size();
                }
            }.setMax(20).start();
//            }
        }
    }

    private void measureSearch(int minBits, int maxDist, int maxEPerL) {
        for (int bits = minBits; bits <= 30; bits += 2) {
            int entriesPerLeaf = 3;
            final QuadTree<Long> quadTree = new SpatialHashtable(bits, entriesPerLeaf).init(g.getNodes());
            QuadTree.Util.fill(quadTree, g);
            for (int distance = 5; distance < maxDist; distance *= 2) {
//                for (; entriesPerLeaf < maxEPerL; entriesPerLeaf *= 2) {
                //final QuadTree<Long> quadTree = new QuadTreeSimple<Long>(entriesPerLeaf, bits);

                System.gc();
                System.gc();
                float mem = (float) quadTree.getMemoryUsageInBytes(1) / Helper.MB;
                long emptyEntries = quadTree.getEmptyEntries(true);
                long emptyAllEntries = quadTree.getEmptyEntries(false);
                final int tmp = distance;
                new MiniPerfTest("neighbour search e/leaf:" + entriesPerLeaf + ", bits:" + bits
                        + ", dist:" + distance + ", mem:" + mem + ", empty entries:" + emptyEntries
                        + ", empty all entries:" + emptyAllEntries) {

                    @Override public long doCalc(int run) {
                        float lat = (random.nextInt(latMax - latMin) + latMin) / 10000.0f;
                        float lon = (random.nextInt(lonMax - lonMin) + lonMin) / 10000.0f;
                        return quadTree.getNodes(lat, lon, tmp).size();
                    }
                }.setMax(10).setShowProgress(true).setSeed(0).start();
//                }
            }
        }
    }
}
