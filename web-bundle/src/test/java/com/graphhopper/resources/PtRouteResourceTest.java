package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import com.graphhopper.gtfs.PtRouter;
import com.graphhopper.gtfs.Request;
import com.graphhopper.http.DurationParam;
import com.graphhopper.http.GHLocationParam;
import com.graphhopper.http.OffsetDateTimeParam;
import com.graphhopper.jackson.ResponsePathSerializer;
import com.graphhopper.util.*;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(DropwizardExtensionsSupport.class)
public class PtRouteResourceTest {

    private PtRouter mockPtRouter;
    private PtRouteResource ptRouteResource;


    @BeforeEach
    public void setup() {
        mockPtRouter = Mockito.mock(PtRouter.class);

        ptRouteResource = new PtRouteResource(mockPtRouter);
    }

    @Test
    public void testRouteReturnsProperlyEncodedResponse() {
        GHResponse ghResponse = new GHResponse();
        ResponsePath responsePath = new ResponsePath();
        PointList points = new PointList();
        points.add(0, 0);
        points.add(1, 1);
        responsePath.setPoints(points);
        InstructionList instructions = new InstructionList(usTR);
        responsePath.setInstructions(instructions);
        ghResponse.add(responsePath);

        when(mockPtRouter.route(any(Request.class))).thenReturn(ghResponse);

        ObjectNode response = ptRouteResource.route(Arrays.asList(
                new GHLocationParam("0,0"),
                new GHLocationParam("1,1")),
                true,
                true,
                false,
                false,
                new OffsetDateTimeParam("2022-01-01T00:00:00Z"),
                new DurationParam("PT1H"),
                false,
                "en",
                false,
                false,
                10,
                new DurationParam("PT1H"),
                new DurationParam("PT1H"),
                "foot",
                1.0,
                "foot",
                1.0);

        ObjectNode encodedResponse = ptRouteResource.route(Arrays.asList(
                        new GHLocationParam("0,0"),
                        new GHLocationParam("1,1")),
                true,
                true,
                false,
                true,
                new OffsetDateTimeParam("2022-01-01T00:00:00Z"),
                new DurationParam("PT1H"),
                false,
                "en",
                false,
                false,
                10,
                new DurationParam("PT1H"),
                new DurationParam("PT1H"),
                "foot",
                1.0,
                "foot",
                1.0);

        assertEquals(false, response.get("paths").get(0).get("points_encoded").asBoolean());
        assertEquals(true, encodedResponse.get("paths").get(0).get("points_encoded").asBoolean());

    }

    private static Translation usTR = new Translation() {
        @Override
        public String tr(String key, Object... params) {
            if (key.equals("roundabout_exit_onto"))
                return "At roundabout, take exit 2 onto streetname";
            return key;
        }

        @Override
        public Map<String, String> asMap() {
            return Collections.emptyMap();
        }

        @Override
        public Locale getLocale() {
            return Locale.US;
        }

        @Override
        public String getLanguage() {
            return "en";
        }
    };
}
