package com.graphhopper.routing;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.json.LeafStatement;
import com.graphhopper.json.Statement;
import com.graphhopper.routing.ev.FootTemporalAccess;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.Helper;
import com.graphhopper.util.details.PathDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class CustomizableConditionalRestrictionsTest {

    private static final String GH_LOCATION = "target/routing-conditional-access-gh";

    @BeforeEach
    @AfterEach
    public void setup() {
        Helper.removeDir(new File(GH_LOCATION));
    }

    @Test
    public void testConditionalAccess() {
        GraphHopper hopper = new GraphHopper().
                setStoreOnFlush(false).
                setEncodedValuesString(FootTemporalAccess.KEY);

        hopper.init(new GraphHopperConfig().
                setProfiles(List.of(TestProfiles.accessAndSpeed("foot", "foot"))).
                putObject("graph.location", GH_LOCATION).
                putObject("graph.encoded_values", "foot_temporal_access, foot_access, foot_average_speed").
                putObject("datareader.file", "../core/files/conditional-restrictions.osm.xml").
                putObject("prepare.min_network_size", "0").
                putObject("import.osm.ignored_highways", "").
                putObject("datareader.date_range_parser_day", "2023-08-01"));
        hopper.importOrLoad();

        String PD_KEY = "access_conditional";
        GHResponse rsp = hopper.route(new GHRequest(50.909136, 14.213924, 50.90918, 14.213549).
                setProfile("foot").
                setPathDetails(Arrays.asList(PD_KEY)));
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        List<PathDetail> details = rsp.getBest().getPathDetails().get(PD_KEY);
        assertEquals("no@(Jan15-Aug15)", details.get(0).getValue());
        assertEquals(2, details.size());
        assertEquals(32, rsp.getBest().getDistance(), 1);

        rsp = hopper.route(new GHRequest(50.909136, 14.213924, 50.90918, 14.213549).
                setProfile("foot").
                setCustomModel(new CustomModel().addToPriority(LeafStatement.If("foot_temporal_access == NO", Statement.Op.MULTIPLY, "0"))).
                setPathDetails(Arrays.asList(PD_KEY)));
        assertFalse(rsp.hasErrors(), rsp.getErrors().toString());
        assertEquals(16, rsp.getBest().getDistance(), 1);
        details = rsp.getBest().getPathDetails().get(PD_KEY);
        assertEquals(1, details.size());
    }
}
