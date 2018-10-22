package com.graphhopper.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.MultiException;
import com.graphhopper.http.WebHelper;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.routing.flex.FlexModel;
import com.graphhopper.routing.flex.FlexRequest;
import com.graphhopper.routing.weighting.FlexWeighting;
import com.graphhopper.routing.weighting.ScriptInterface;
import com.graphhopper.routing.weighting.ScriptWeighting;
import com.graphhopper.util.StopWatch;
import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.commons.compiler.IClassBodyEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Arrays;

@Path("flex")
public class FlexResource {
    private static final Logger logger = LoggerFactory.getLogger(RouteResource.class);

    private final GraphHopper graphHopper;
    private final Boolean hasElevation;
    private final ObjectMapper yamlOM;

    @Inject
    public FlexResource(GraphHopper graphHopper, @Named("hasElevation") Boolean hasElevation) {
        this.graphHopper = graphHopper;
        this.hasElevation = hasElevation;
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

        if (model.getMaxSpeed() < 1)
            throw new IllegalArgumentException("max_speed too low: " + model.getMaxSpeed());

        if (!model.getName().isEmpty())
            request.setWeighting(model.getName());
        else if (!model.getBase().isEmpty())
            request.setWeighting(model.getBase());
        else
            throw new IllegalArgumentException("'base' cannot be empty");

        request.getHints().put("ch.disable", true);

        // TODO read via request.getHints().get(Routing.INSTRUCTIONS)
        boolean instructions = true;
        boolean calcPoints = true;
        boolean enableElevation = false;
        boolean pointsEncoded = true;

        StopWatch sw = new StopWatch().start();
        GHResponse ghResponse = new GHResponse();

        if (model.getScript().isEmpty())
            graphHopper.calcPaths(request, ghResponse, new FlexWeighting(model));
        else
            graphHopper.calcPaths(request, ghResponse, createScriptWeighting(model));

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
        String script = model.getScript();
        for (String chars : Arrays.asList("{", "}", "import", "static", "file"))
            if (script.contains(chars))
                throw new IllegalArgumentException("Script contains illegal character " + chars);

        if (!script.contains("return"))
            script = "return " + script;
        if (!script.endsWith(";"))
            script = script + ";";

        try {
            IClassBodyEvaluator cbe = CompilerFactoryFactory.getDefaultCompilerFactory().newClassBodyEvaluator();
            cbe.setNoPermissions();
            cbe.setImplementedInterfaces(new Class[]{ScriptInterface.class});
            cbe.setDefaultImports(new String[]{"com.graphhopper.util.EdgeIteratorState",
                    "com.graphhopper.routing.profiles.*"});
            cbe.setClassName("UserScript");
            cbe.cook("public EnumEncodedValue road_class;\n"
                    + "  public EnumEncodedValue road_environment;\n"
                    + "  public IntEncodedValue toll;\n"
                    + "  public double getMillisFactor(EdgeIteratorState edge, boolean reverse) {\n"
                    // "return edge.get(road_class) == RoadClass.PRIMARY ? 1 : 10;"
                    + script
                    + "  }");
            Class<?> c = cbe.getClazz();
            return new ScriptWeighting(model.getBase(), model.getMaxSpeed(), (ScriptInterface) c.newInstance());
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }
}
