package com.graphhopper.matching.http;

import com.graphhopper.http.WebHelper;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import java.io.BufferedInputStream;
import java.io.File;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class MatchServletTest extends BaseServletTester {

    private static final String pbf = "../map-data/leipzig_germany.osm.pbf";
    private static final String dir = "../target/mapmatchingtest";

    @AfterClass
    public static void cleanUp() {
        // do not remove imported graph
        // Helper.removeDir(new File(dir));
        shutdownJetty(true);
    }

    @Before
    public void setUp() {
        CmdArgs args = new CmdArgs().
                put("graph.flagEncoders", "car").
                put("prepare.chWeighting", "false").
                put("osmreader.osm", pbf).
                put("graph.location", dir);
        setUpJetty(args);
    }

    @Test
    public void testDoPost() throws Exception {
        String xmlStr = Helper.isToString(getClass().getResourceAsStream("tour2-with-loop.gpx"));
        String jsonStr = post("/match", 200, xmlStr);
        JSONObject json = new JSONObject(jsonStr);

        // {"hints":{},
        //  "paths":[{"instructions":[{"distance":417.326,"sign":0,"interval":[0,3],"text":"Continue onto Gustav-Adolf-Straße","time":60093},{"distance":108.383,"sign":-2,"interval":[3,4],"text":"Turn left onto Leibnizstraße","time":15607},{"distance":218.914,"sign":-2,"interval":[4,6],"text":"Turn left onto Hinrichsenstraße","time":26269},{"distance":257.727,"sign":-2,"interval":[6,8],"text":"Turn left onto Tschaikowskistraße","time":30926},{"distance":0,"sign":4,"interval":[8,8],"text":"Finish!","time":0}],
        //  "descend":0,"ascend":0,"distance":1002.35,"bbox":[12.35853,51.342524,12.36419,51.345381],"weight":1002.35,"time":132895,"points_encoded":true,"points":"{}jxHwwljAsBuOaA{GcAyH}DlAhAdIz@jGvDeB|FiC"}],
        //  "info":{"copyrights":["GraphHopper","OpenStreetMap contributors"]}
        // }
        JSONObject path = json.getJSONArray("paths").getJSONObject(0);
        assertEquals(5, path.getJSONArray("instructions").length());
        assertEquals(9, WebHelper.decodePolyline(path.getString("points"), 10, false).size());

        assertEquals(132.9, path.getLong("time") / 1000f, 0.1);
        assertEquals(1002, path.getDouble("distance"), 1);        
    }
}
