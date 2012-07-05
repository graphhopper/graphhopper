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
package de.jetsli.compare.misc;

import de.genvlin.core.data.DoubleVectorInterface;
import de.genvlin.core.data.HistogrammInterface;
import de.genvlin.core.data.MainPool;
import de.genvlin.core.data.XYVectorInterface;
import de.genvlin.gui.plot.GPlotPanel;
import de.jetsli.graph.geohash.SpatialHashtable;
import de.jetsli.graph.reader.OSMReader;
import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.trees.QuadTree;
import de.jetsli.graph.util.CoordTrig;
import de.jetsli.graph.util.CoordTrigLongEntry;
import de.jetsli.graph.util.Helper;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * @author Peter Karich
 */
public class PrintStats {

    public static void main(String[] args) throws Exception {
        final Graph g = OSMReader.osm2Graph(Helper.readCmdArgs(args));
        final int locs = g.getNodes();
        System.out.println("graph contains " + locs + " nodes");

        // make sure getBucketIndex is okayish fast
//        new MiniPerfTest("test") {
//
//            @Override
//            public long doCalc(int run) {
//                try {
//                    SpatialKeyTree qt = new SpatialKeyTree(10, 4).init(locs);
//                    PerfTest.fillQuadTree(qt, g);
//                    return qt.size();
//                } catch (Exception ex) {
//                    ex.printStackTrace();
//                    return -1;
//                }
//            }
//        }.setMax(50).start();

        final GPlotPanel panel = new GPlotPanel();
        SwingUtilities.invokeLater(new Runnable() {

            @Override public void run() {
                int frameHeight = 800;
                int frameWidth = 1200;
                JFrame frame = new JFrame("UI - Fast&Ugly");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setSize(frameWidth, frameHeight);
                frame.add(panel.getComponent());
                frame.setVisible(true);
            }
        });

        try {
            for (int skipLeft = 8; skipLeft < 40; skipLeft += 2) {
                int entriesPerBuck = 3;
//                for (; entriesPerBuck < 20; entriesPerBuck += 8) {
                log("\n");
                TmpHashtable qt = new TmpHashtable(skipLeft, entriesPerBuck);
                qt.init(locs);
                int epb = qt.getEntriesPerBucket();
                String title = "skipLeft:" + skipLeft + " entries/buck:" + epb;
                log(title);
                try {
                    QuadTree.Util.fill(qt, g);
                } catch (Exception ex) {
//                    ex.printStackTrace();
                    log(ex.getMessage());
                    continue;
                }

                XYVectorInterface entries = qt.getEntries("Entr " + title);
                panel.addData(entries);
                HistogrammInterface hist = (HistogrammInterface) entries.getY();
                log("entries: [" + hist.getMin() + "," + hist.getMax() + "] mean:" + (float) hist.getMean() + " rms:" + (float) hist.getRMSError());

                XYVectorInterface overflow = qt.getOverflowEntries("Ovrfl " + title);
                panel.addData(overflow);
                hist = (HistogrammInterface) overflow.getY();
                log("ovrflow: [" + hist.getMin() + "," + hist.getMax() + "] mean:" + (float) hist.getMean() + " rms:" + (float) hist.getRMSError());

//                XYVectorInterface overflowOff = qt.getOverflowOffset("Off " + title);
//                panel.addData(overflowOff);
//                hist = (HistogrammInterface) overflowOff.getY();
//                log("offsets: [" + hist.getMin() + "," + hist.getMax() + "] mean:" + (float)hist.getMean() + " rms:" + (float)hist.getRMSError());

                XYVectorInterface dataHist = qt.getSum("Unuse " + title);
                hist = (HistogrammInterface) dataHist.getY();
                log("unused:  [" + hist.getMin() + "," + hist.getMax() + "] mean:" + (float) hist.getMean() + " rms:" + (float) hist.getRMSError());
                panel.addData(dataHist);
                panel.repaint();
//                }
            }
        } catch (Exception ex) {
            // do not crash the UI if 'overflow'
            ex.printStackTrace();
        }
    }

    public static void log(Object o) {
        System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "# " + o);
    }

    public static class TmpHashtable extends SpatialHashtable {

        public TmpHashtable(int skipKeyBeginningBits, int initialEntriesPerBucket) {
            super(skipKeyBeginningBits, initialEntriesPerBucket);
        }

        public XYVectorInterface getUnusedBytes(String title) {
            MainPool pool = MainPool.getDefault();
            XYVectorInterface xy = pool.createXYVector(DoubleVectorInterface.class, HistogrammInterface.class);
            for (int bucketIndex = 0; bucketIndex < maxBuckets; bucketIndex++) {
                int entryBytes = getNoOfEntries(bucketIndex * bytesPerBucket) * bytesPerEntry;
                int ovflBytes = getNoOfOverflowEntries(bucketIndex * bytesPerBucket) * bytesPerOverflowEntry;
                int unusedBytes = bytesPerBucket - 1 - (entryBytes + ovflBytes);
                xy.add(bucketIndex, unusedBytes);
            }
            xy.setTitle(title);
            return xy;
        }

        XYVectorInterface getOverflowOffset(String title) {
            MainPool pool = MainPool.getDefault();
            XYVectorInterface xy = pool.createXYVector(DoubleVectorInterface.class, HistogrammInterface.class);
            for (int bucketIndex = 0; bucketIndex < maxBuckets; bucketIndex++) {
                xy.add(bucketIndex, getLastOffset(bucketIndex));
            }
            xy.setTitle(title);
            return xy;
        }

        XYVectorInterface getOverflowEntries(String title) {
            MainPool pool = MainPool.getDefault();
            XYVectorInterface xy = pool.createXYVector(DoubleVectorInterface.class, HistogrammInterface.class);
            for (int bucketIndex = 0; bucketIndex < maxBuckets; bucketIndex++) {
                xy.add(bucketIndex, getNoOfOverflowEntries(bucketIndex * bytesPerBucket));
            }
            xy.setTitle(title);
            return xy;
        }

        XYVectorInterface getEntries(String title) {
            MainPool pool = MainPool.getDefault();
            XYVectorInterface xy = pool.createXYVector(DoubleVectorInterface.class, HistogrammInterface.class);
            for (int bucketIndex = 0; bucketIndex < maxBuckets; bucketIndex++) {
                final List<CoordTrig<Long>> res = new ArrayList<CoordTrig<Long>>();
                getNodes(new SpatialHashtable.LeafWorker() {

                    @Override public boolean doWork(long key, long value) {
                        CoordTrig<Long> coord = new CoordTrigLongEntry();
                        algo.decode(key, coord);
                        coord.setValue(value);
                        res.add(coord);
                        return true;
                    }
                }, bucketIndex, null);
                xy.add(bucketIndex, res.size());
            }
            xy.setTitle(title);
            return xy;
        }

        XYVectorInterface getSum(String title) {
            MainPool pool = MainPool.getDefault();
            XYVectorInterface xy = pool.createXYVector(DoubleVectorInterface.class, HistogrammInterface.class);
            for (int bucketIndex = 0; bucketIndex < maxBuckets; bucketIndex++) {
                int overflBytes = getNoOfOverflowEntries(bucketIndex * bytesPerBucket) * bytesPerOverflowEntry;
                int entryBytes = getNoOfEntries(bucketIndex * bytesPerBucket) * bytesPerEntry;
                int unusedBytes = bytesPerBucket - 1 - (entryBytes + overflBytes);
                xy.add(bucketIndex, (double) unusedBytes / bytesPerEntry);
            }
            xy.setTitle(title);
            return xy;
        }
    }
}
