/*
 *  Copyright 2012 Peter Karich
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
package com.graphhopper;

import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GeoPoint;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main wrapper of the offline API for a simple and efficient usage.
 *
 * @author Peter Karich
 */
public class GraphHopperWeb implements GraphHopperAPI {

    public static void main(String[] args) {
        GraphHopperAPI gh = new GraphHopperWeb().load("http://217.92.216.224:8080/api");
        PathHelper ph = gh.route(new GeoPoint(53.080827, 9.074707), new GeoPoint(50.597186, 11.184082));
        System.out.println(ph);
    }
    private Logger logger = LoggerFactory.getLogger(getClass());
    private String serviceUrl;
    private String algo;
    private double minPathPrecision;

    public GraphHopperWeb() {
    }

    /**
     * Example url: http://localhost:8989/api or http://217.92.216.224:8080/api
     */
    @Override
    public GraphHopperAPI load(String url) {
        this.serviceUrl = url;
        return this;
    }

    @Override
    public GraphHopperWeb algorithm(String algo) {
        this.algo = algo;
        return this;
    }

    @Override
    public GraphHopperWeb minPathPrecision(double precision) {
        minPathPrecision = precision;
        return this;
    }

    // TODO
//    public String whatIsHere(GeoPoint point) {
//        return "address";
//    }
//    public GeoPoint findAddress(String from) {
//        return new GeoPoint(lat, lon);
//    }
    @Override
    public PathHelper route(GeoPoint from, GeoPoint to) {
        StopWatch sw = new StopWatch().start();
        float took = 0;
        try {
            String url = serviceUrl
                    + "?from=" + from.lat + "," + from.lon
                    + "&to=" + to.lat + "," + to.lon
                    + "&type=bin"
                    + "&minPathPrecision=" + minPathPrecision
                    + "&algo=" + algo;
            DataInputStream is = new DataInputStream(fetch(url));
            int magix = is.readInt();
            if (magix != 123456)
                throw new IOException("Wrong magix " + magix);

            took = is.readFloat();
            float distance = is.readFloat();
            int time = is.readInt();
            int nodes = is.readInt();
            List<GeoPoint> list = new ArrayList<GeoPoint>(nodes);
            for (int i = 0; i < nodes; i++) {
                float lat = is.readFloat();
                float lon = is.readFloat();
                list.add(new GeoPoint(lat, lon));
            }
            return new PathHelper(list).distance(distance).time(time);
        } catch (IOException ex) {
            throw new RuntimeException("Problem while fetching path " + from + "->" + to, ex);
        } finally {
            logger.info("Full request took:" + sw.stop().getSeconds() + ", API took:" + took);
        }
    }

    InputStream fetch(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) createConnection(url);
        // create connection but before reading get the correct inputstream based on the compression
        conn.connect();
        String encoding = conn.getContentEncoding();
        InputStream is;
        if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
            is = new GZIPInputStream(conn.getInputStream());
        } else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
            is = new InflaterInputStream(conn.getInputStream(), new Inflater(true));
        } else {
            is = conn.getInputStream();
        }
        return is;
    }

    HttpURLConnection createConnection(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setUseCaches(true);
        conn.setRequestProperty("Referrer", "http://graphhopper.com");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Online GraphHopperAPI)");
        // suggest respond to be gzipped or deflated (which is just another compression)
        // http://stackoverflow.com/q/3932117
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
        conn.setReadTimeout(4000);
        conn.setConnectTimeout(4000);
        return conn;
    }
}
