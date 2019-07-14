/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.MultiException;
import com.graphhopper.http.WebHelper;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.routing.weighting.FlexWeighting;
import com.graphhopper.routing.weighting.ScriptInterface;
import com.graphhopper.routing.weighting.ScriptWeighting;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.flex.FlexModel;
import com.graphhopper.util.flex.FlexRequest;
import org.codehaus.janino.ExpressionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

@Path("flex")
public class FlexResource {
    private static final Logger logger = LoggerFactory.getLogger(RouteResource.class);

    private final GraphHopper graphHopper;
    private final ObjectMapper yamlOM;
    private final CmdArgs cmdArgs;

    @Inject
    public FlexResource(GraphHopper graphHopper, CmdArgs cmdArgs) {
        this.graphHopper = graphHopper;
        this.cmdArgs = cmdArgs;
        this.yamlOM = Jackson.init(new ObjectMapper(new YAMLFactory()));
    }

    @POST
    @Consumes({"text/x-yaml", "application/x-yaml"})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/gpx+xml"})
    public Response doPost(String yaml) {
        FlexRequest flexRequest;
        try {
            flexRequest = yamlOM.readValue(yaml, FlexRequest.class);
        } catch (Exception ex) {
            // TODO should we really provide this much details?
            throw new IllegalArgumentException("Incorrect YAML: " + ex.getMessage(), ex);
        }
        return doPost(flexRequest);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/gpx+xml"})
    public Response doPost(FlexRequest flex) {
        if (flex == null)
            throw new IllegalArgumentException("FlexRequest cannot be empty");

        FlexModel model = flex.getModel();
        GHRequest request = flex.getRequest();

        if (!model.getName().isEmpty())
            request.setWeighting(model.getName());
        else if (!model.getBase().isEmpty())
            request.setWeighting(model.getBase());
        else
            throw new IllegalArgumentException("'base' cannot be empty");

        if (model.getMaxSpeed() < 1)
            model.setMaxSpeed((request.getWeighting().startsWith("car") || request.getWeighting().startsWith("truck")) ? 100 : 10);

        request.getHints().put("ch.disable", true);

        // TODO read via request.getHints().get(Routing.INSTRUCTIONS)
        boolean instructions = true;
        boolean calcPoints = true;
        boolean enableElevation = false;
        boolean pointsEncoded = true;

        StopWatch sw = new StopWatch().start();
        GHResponse ghResponse = new GHResponse();

        if (model.getScript().isEmpty()) {
            graphHopper.calcPaths(request, ghResponse, new FlexWeighting(model));
        } else {
            // Enabling scripting is currently a security problem. Do not enable it for public facing services!
            if (!cmdArgs.getBool("routing.scripting", false))
                throw new IllegalArgumentException("Scripting not allowed");

            graphHopper.calcPaths(request, ghResponse, createScriptWeighting(model));
        }

        float took = sw.stop().getSeconds();
        if (ghResponse.hasErrors()) {
            logger.error("errors:" + ghResponse.getErrors());
            throw new MultiException(ghResponse.getErrors());
        } else {
            logger.info("alternatives: " + ghResponse.getAll().size()
                    + ", distance0: " + ghResponse.getBest().getDistance()
                    + ", weight0: " + ghResponse.getBest().getRouteWeight()
                    + ", time0: " + Math.round(ghResponse.getBest().getTime() / 60000f) + "min"
                    + ", points0: " + ghResponse.getBest().getPoints().getSize()
                    + ", debugInfo: " + ghResponse.getDebugInfo());
            return Response.ok(WebHelper.jsonObject(ghResponse, instructions, calcPoints, enableElevation, pointsEncoded, took)).
                    header("X-GH-Took", "" + Math.round(took * 1000)).
                    build();
        }
    }

    public ScriptWeighting createScriptWeighting(FlexModel model) {
        final String expressions = model.getScript();
        if (expressions.isEmpty())
            throw new IllegalArgumentException("Script cannot be empty");
        for (String chars : Arrays.asList("{", "}", "import", "static", "file"))
            if (expressions.contains(chars))
                throw new IllegalArgumentException("Script contains illegal character " + chars);

        ExpressionEvaluator ee = new ExpressionEvaluator();
        final ScriptInterface.HelperVariables baseClass;
        try {
            ee.setStaticMethod(false);
            ee.setDefaultImports(new String[]{"com.graphhopper.util.EdgeIteratorState",
                    "com.graphhopper.util.shapes.BBox",
                    "com.graphhopper.routing.profiles.*"});
            ee.setExtendedClass(ScriptInterface.HelperVariables.class);
            ee.setParameters(new String[]{"edge", "reverse"}, new Class[]{EdgeIteratorState.class, boolean.class});
            ee.cook(expressions.split(";"));
            baseClass = (ScriptInterface.HelperVariables) ee.getMethod().getDeclaringClass().getDeclaredConstructor().newInstance();
            baseClass.nodeAccess = graphHopper.getGraphHopperStorage().getNodeAccess();
        } catch (Exception ex) {
            logger.info("script problem: " + ex, ex);
            throw new IllegalArgumentException(ex);
        }

        ScriptInterface scriptInterface = new Script(ee, baseClass, expressions);
        return new ScriptWeighting(model.getBase(), model.getMaxSpeed(), scriptInterface);
    }

    private static class Script implements ScriptInterface {
        ExpressionEvaluator ee;
        ScriptInterface.HelperVariables baseClass;
        String expression;

        public Script(ExpressionEvaluator ee, HelperVariables baseClass, String expression) {
            this.ee = ee;
            this.baseClass = baseClass;
            this.expression = expression;
        }

        @Override
        public double getMillisFactor(EdgeIteratorState edge, boolean reverse) {
            try {
                return ((Number) ee.getMethod().invoke(baseClass, edge, reverse)).doubleValue();
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        public ScriptInterface.HelperVariables getHelperVariables() {
            return baseClass;
        }

        @Override
        public String toString() {
            return expression;
        }
    }
}
