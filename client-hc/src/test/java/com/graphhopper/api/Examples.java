package com.graphhopper.api;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.details.PathDetail;
import com.graphhopper.util.shapes.GHPoint;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Disabled
public class Examples {

    String apiKey = "<YOUR_API_KEY>";

    @Test
    public void routing() {
        // Hint: create this thread safe instance only once in your application to allow the underlying library to cache the costly initial https handshake
        GraphHopperWeb gh = new GraphHopperWeb();
        // insert your key here
        gh.setKey(apiKey);
        // change timeout, default is 5 seconds
        gh.setDownloader(new OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build());

        // specify at least two coordinates
        GHRequest req = new GHRequest().
                addPoint(new GHPoint(49.6724, 11.3494)).
                addPoint(new GHPoint(49.6550, 11.4180));
        // Set profile like car, bike, foot, ...
        req.setProfile("bike");
        // Optionally enable/disable elevation in output PointList, default is false
        req.putHint("elevation", false);
        // Optionally enable/disable turn instruction information, defaults is true
        req.putHint("instructions", true);
        // Optionally enable/disable path geometry information, default is true
        req.putHint("calc_points", true);
        // note: turn off instructions and calcPoints if you just need the distance or time
        // information to make calculation and transmission faster

        // Optionally set specific locale for instruction information, supports already over 25 languages,
        // defaults to English
        req.setLocale(Locale.GERMAN);

        // Optionally add path details
        req.setPathDetails(Arrays.asList(
                Parameters.Details.STREET_NAME,
                Parameters.Details.AVERAGE_SPEED,
                Parameters.Details.EDGE_ID
        ));

        GHResponse fullRes = gh.route(req);

        if (fullRes.hasErrors())
            throw new RuntimeException(fullRes.getErrors().toString());

        // get best path (you will get more than one path here if you requested algorithm=alternative_route)
        ResponsePath res = fullRes.getBest();
        // get path geometry information (latitude, longitude and optionally elevation)
        PointList pl = res.getPoints();
        // distance of the full path, in meter
        double distance = res.getDistance();
        // time of the full path, in milliseconds
        long millis = res.getTime();
        // get information per turn instruction
        InstructionList il = res.getInstructions();
        for (Instruction i : il) {
            // System.out.println(i.getName());
            
            // to get the translated turn instructions you call:
            // System.out.println(i.getTurnDescription(null));
            // Note, that you can control the language only in via the request setLocale method and cannot change it only the client side
        }
        // get path details
        List<PathDetail> pathDetails = res.getPathDetails().get(Parameters.Details.STREET_NAME);
        for (PathDetail detail : pathDetails) {
//            System.out.println(detail.getValue());
        }
    }

    @Test
    public void matrix() {
        // Hint: create this thread safe instance only once in your application to allow the underlying library to cache the costly initial https handshake
        GraphHopperMatrixWeb matrixClient = new GraphHopperMatrixWeb();
        // for very large matrices you need:
        // GraphHopperMatrixWeb matrixClient = new GraphHopperMatrixWeb(new GHMatrixBatchRequester());
        matrixClient.setKey(apiKey);

        GHMRequest ghmRequest = new GHMRequest();
        ghmRequest.setOutArrays(Arrays.asList("distances", "times"));
        ghmRequest.setProfile("car");

        // Option 1: init points for a symmetric matrix
        List<GHPoint> allPoints = Arrays.asList(new GHPoint(49.6724, 11.3494), new GHPoint(49.6550, 11.4180));
        ghmRequest.setPoints(allPoints);
        MatrixResponse responseSymm = matrixClient.route(ghmRequest);
        if (responseSymm.hasErrors())
            throw new RuntimeException(responseSymm.getErrors().toString());
        // get time from first to second point:
        // System.out.println(response.getTime(0, 1));

        // Option 2: for an asymmetric matrix do:
        ghmRequest = new GHMRequest();
        ghmRequest.setOutArrays(Arrays.asList("distances", "times"));
        ghmRequest.setProfile("car");
        ghmRequest.setFromPoints(Arrays.asList(new GHPoint(49.6724, 11.3494)));
        // or init e.g. a one-to-many matrix:
        ghmRequest.setToPoints(Arrays.asList(new GHPoint(49.6724, 11.3494), new GHPoint(49.6550, 11.4180)));

        MatrixResponse responseAsymm = matrixClient.route(ghmRequest);
        if (responseAsymm.hasErrors())
            throw new RuntimeException(responseAsymm.getErrors().toString());

        // get time from first to second point:
        // System.out.println(response.getTime(0, 1));
    }
}
