package com.graphhopper.http.cli;

import com.graphhopper.GraphHopper;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.http.GraphHopperServerConfiguration;
import com.graphhopper.isochrone.algorithm.Isochrone;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.profiles.UnsignedIntEncodedValue;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

public class RestaurantImportCommand extends ConfiguredCommand<GraphHopperServerConfiguration> {

    private static final Logger logger = LoggerFactory.getLogger(RestaurantImportCommand.class);

    public RestaurantImportCommand() {
        super("restaurant_import", "creates the graphhopper files used for later (faster) starts");
    }

    @Override
    protected void run(Bootstrap<GraphHopperServerConfiguration> bootstrap, Namespace namespace, GraphHopperServerConfiguration configuration) throws Exception {
        GraphHopper graphHopper = new GraphHopperOSM();
        graphHopper.forServer();
        CmdArgs cfg = configuration.getGraphHopperConfiguration();
        if (cfg.has("graph.encoded_values") || cfg.has("graph.flag_encoders"))
            throw new IllegalArgumentException("Currently no customization of graph.encoded_values or graph.flag_encoders possible");

        graphHopper.init(cfg);
        UnsignedIntEncodedValue countEnc = new UnsignedIntEncodedValue("count", 8, false);
        CarFlagEncoder car = new CarFlagEncoder();
        graphHopper.setEncodingManager(new EncodingManager.Builder(4).add(countEnc).add(car).build());
        graphHopper.importOrLoad();

        LocationIndex index = graphHopper.getLocationIndex();
        if (!graphHopper.getGraphHopperStorage().getProperties().get("restaurant.import").equals("true")) {
            StopWatch sw = new StopWatch().start();
            Graph graph = graphHopper.getGraphHopperStorage();
            int counter = 0, validCounter = 0;
            // wget https://github.com/karussell/crashstats/blob/tmp/out.xml.gz?raw=true -O out.xml.gz
            // or create from any osm.bz2 file via: bzgrep -B 1 restaurant germany.osm.bz2 | grep node
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream("out.xml.gz"))))) {
                String str;
                while ((str = br.readLine()) != null) {
                    str = str.trim();
                    if (!str.startsWith("<node"))
                        continue;

                    int startLat = str.indexOf("lat=\"");
                    int endLat = str.indexOf("\"", startLat + 5);

                    int startLon = str.indexOf("lon=\"");
                    int endLon = str.indexOf("\"", startLon + 5);
                    if (startLat > 0 && startLon > 0 && endLat > 0 && endLon > 0) {
                        double lat = Double.parseDouble(str.substring(startLat + 5, endLat));
                        double lon = Double.parseDouble(str.substring(startLon + 5, endLon));
                        QueryResult res = index.findClosest(lat, lon, EdgeFilter.ALL_EDGES);
                        counter++;
                        // if the area you import is not identical to the location data or if certain roads are not accessible by any vehicle profile
                        // and because of OSM bugs it can happen that restaurants are too far away - exclude them. For Germany this count is rather low (valid 18228 of 18273)
                        if (res.isValid() && res.getQueryDistance() < 2000) {
                            validCounter++;
                            EdgeIteratorState edge = res.getClosestEdge();
                            // TODO: this is a bit ugly. We need to ensure that the edge is in "storage"-direction, otherwise we'll get:
                            // value for reverse direction would overwrite forward direction. Enable storeTwoDirections for this EncodedValue or don't use setReverse
                            edge = graph.getEdgeIteratorState(edge.getEdge(), Integer.MIN_VALUE);
                            edge.set(countEnc, edge.get(countEnc) + 1);
                        }
                    }
                }
            }

            // store restaurant changes to graph
            graphHopper.getGraphHopperStorage().getProperties().put("restaurant.import", "true");
            graphHopper.getGraphHopperStorage().flush();

            logger.info("took: " + sw.stop().getSeconds() + ", all restaurants: " + counter + ", valid: " + validCounter);
        } else {
            logger.info("restaurant data already imported");
        }

        // start in the middle of Germany
        QueryResult result = index.findClosest(52.515385, 13.394394, EdgeFilter.ALL_EDGES);
        if (!result.isValid())
            throw new IllegalArgumentException("start point is invalid");

        Histogram histo = new Histogram(0, 600, 20);

        StopWatch sw = new StopWatch().start();
        final Graph graph = graphHopper.getGraphHopperStorage();
        final EdgeExplorer explorer = graph.createEdgeExplorer(DefaultEdgeFilter.allEdges(car));
        final GHBitSetImpl uniqueEdges = new GHBitSetImpl(graph.getAllEdges().length());
        final GHBitSetImpl sortedNodesFromHeap = new GHBitSetImpl(graph.getNodes());
        final AtomicInteger integer = new AtomicInteger(0);
        Isochrone iso = new Isochrone(graph, new FastestWeighting(car), false);
        iso.setTimeLimit(Integer.MAX_VALUE);
        sortedNodesFromHeap.add(result.getClosestNode());
        logger.info("nodes: " + graph.getNodes() + ", edges:" + graph.getEdges());
        // TODO this tweaked search method could be useful in master too, as it seems small effort to provide sorted results
        iso.search(result.getClosestNode(), label -> {

            sortedNodesFromHeap.add(label.nodeId);
            // We need to traverse all edges. The problem is that the restaurant can be on any edge
            // not just on the optimal edges of the shortest path tree.
            EdgeIterator iter = explorer.setBaseNode(label.nodeId);
            while (iter.next()) {
                if (uniqueEdges.contains(iter.getEdge()))
                    continue;
                // skip not yet explored nodes as the edges are not optimal, i.e. order would be violated
                if (!sortedNodesFromHeap.contains(iter.getAdjNode()))
                    continue;

                uniqueEdges.add(iter.getEdge());
                int count = iter.get(countEnc);
                if (count > 0) {
                    histo.add(label.timeMillis / 1000.0 / 60.0);
                    // Do something useful like printing or early exit
//                    if (label.distance > 800_000)
//                        logger.info(count + " restaurants, " + Math.round(label.timeMillis / 1000.0 / 60.0) + "min, " + (float) (label.distance / 1000) + "km");
                    integer.addAndGet(count);
                }
            }
        });

        logger.info("took: " + sw.stop().getSeconds() + ", count:" + integer.get());

        System.out.println(histo.toCSV());

//        AllEdgesIterator allIter = graph.getAllEdges();
//        while (allIter.next()) {
//            if (allIter.get(countEnc) > 0)
//                logger.info("edge: " + allIter.getEdge() + ", restaurant count: " + allIter.get(countEnc));
//        }
    }

    class Histogram {
        final int buckets[];
        final double offset, bucketLength;

        public Histogram(double from, double to, double bucketLength) {
            if (from >= to)
                throw new IllegalArgumentException("to must be greater than from: " + from + ", " + to);
            this.offset = from;
            this.bucketLength = bucketLength;
            int size = (int) Math.round((to - from) / bucketLength);
            buckets = new int[size + 1];
        }

        public void add(double number) {
            int bucket = (int) Math.round((number - offset) / bucketLength);
            if (bucket >= buckets.length)
                bucket = buckets.length - 1;
            if (bucket < 0)
                bucket = 0;
            buckets[bucket]++;
        }

        public String toCSV() {
            String str = "";
            for (int i = 0; i < buckets.length; i++) {
                if (!str.isEmpty())
                    str += "\n";

                str += toInt(Math.round(i * bucketLength + offset), 3) + ", " + buckets[i];
            }
            return str;
        }

        // "5%",4 => "  5%"
        String toInt(Object number, int chars) {
            String out = number.toString();
            while (out.length() < chars)
                out = " " + out;
            return out;
        }

        String toChars(int count, String charLook) {
            if (count <= 0)
                return "";
            String out = "";
            for (int i = 0; i < count; i += 4) {
                out += charLook;
            }
            return out;
        }
    }
}
