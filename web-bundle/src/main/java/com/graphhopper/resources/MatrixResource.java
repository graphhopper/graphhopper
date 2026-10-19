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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.matrix.MatrixException;
import com.graphhopper.matrix.MatrixRequest;
import com.graphhopper.routing.matrix.MatrixEngine;
import com.graphhopper.util.shapes.GHPoint;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict synchronous API for the versioned local matrix contract. */
@Path("matrix")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MatrixResource {
    public static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final Logger LOGGER = LoggerFactory.getLogger(MatrixResource.class);
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final Set<String> FIELDS = Set.of("profile", "points", "from_points", "to_points", "out_arrays", "fail_fast", "local");

    private final GraphHopperConfig config;
    private final MatrixEngine engine;

    @Inject
    public MatrixResource(GraphHopperConfig config, MatrixEngine engine) {
        this.config = config;
        this.engine = engine;
    }

    @POST
    public Response doPost(InputStream body, @Context UriInfo uriInfo) {
        try {
            if (!uriInfo.getQueryParameters().isEmpty())
                throw invalid("Matrix query parameters are unsupported; supply the documented options in the JSON body");
            MatrixRequest request = parse(body);
            Map<String, Object> result = new LinkedHashMap<>(engine.route(request));
            @SuppressWarnings("unchecked")
            Map<String, Object> info = new LinkedHashMap<>((Map<String, Object>) result.getOrDefault("info", Map.of()));
            info.putIfAbsent("copyrights", config.getCopyrights());
            result.put("info", info);
            return Response.ok(result).build();
        } catch (MatrixException exception) {
            return error(status(exception.getCode()), exception.getCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error("Local matrix request failed", exception);
            return error(500, "INTERNAL_ERROR", "Local matrix request failed");
        }
    }

    private static MatrixRequest parse(InputStream body) {
        byte[] bytes;
        try {
            bytes = body == null ? new byte[0] : body.readNBytes(MAX_BODY_BYTES + 1);
        } catch (IOException exception) {
            throw new MatrixException("INVALID_JSON", "Cannot read the JSON request body", exception);
        }
        if (bytes.length > MAX_BODY_BYTES)
            throw new MatrixException("REQUEST_LIMIT_EXCEEDED", "Matrix request body exceeds " + MAX_BODY_BYTES + " bytes");
        JsonNode root;
        try (JsonParser parser = JSON.createParser(bytes)) {
            root = JSON.readTree(parser);
            if (parser.nextToken() != null)
                throw new MatrixException("INVALID_JSON", "The request body must contain exactly one JSON object");
        } catch (IOException exception) {
            throw new MatrixException("INVALID_JSON", "Malformed JSON request body", exception);
        }
        if (root == null || !root.isObject())
            throw invalid("The request body must be a JSON object");
        rejectUnknown(root, FIELDS, "request");
        JsonNode profile = root.get("profile");
        if (profile == null || !profile.isTextual() || profile.textValue().isBlank())
            throw invalid("'profile' must be a nonempty string");

        List<GHPoint> origins;
        List<GHPoint> destinations;
        if (root.has("points")) {
            if (root.has("from_points") || root.has("to_points"))
                throw invalid("Use either 'points' or both 'from_points' and 'to_points'");
            origins = axis(root.get("points"), "points");
            destinations = origins;
        } else {
            origins = axis(root.get("from_points"), "from_points");
            destinations = axis(root.get("to_points"), "to_points");
        }

        List<String> arrays = List.of("times", "distances");
        if (root.has("out_arrays")) {
            JsonNode values = root.get("out_arrays");
            if (!values.isArray() || values.isEmpty())
                throw invalid("'out_arrays' must be a nonempty array");
            arrays = new ArrayList<>();
            for (JsonNode value : values) {
                if (!value.isTextual())
                    throw invalid("'out_arrays' values must be strings");
                arrays.add(value.textValue());
            }
        }
        boolean failFast = true;
        if (root.has("fail_fast")) {
            if (!root.get("fail_fast").isBoolean())
                throw invalid("'fail_fast' must be a boolean");
            failFast = root.get("fail_fast").booleanValue();
        }
        String algorithm = "auto";
        if (root.has("local")) {
            JsonNode local = root.get("local");
            if (!local.isObject())
                throw invalid("'local' must be an object");
            rejectUnknown(local, Set.of("algorithm"), "local");
            if (local.has("algorithm")) {
                if (!local.get("algorithm").isTextual())
                    throw invalid("'local.algorithm' must be a string");
                algorithm = local.get("algorithm").textValue();
            }
        }
        return new MatrixRequest(profile.textValue(), origins, destinations, arrays, failFast, algorithm);
    }

    private static List<GHPoint> axis(JsonNode values, String name) {
        if (values == null || !values.isArray() || values.isEmpty())
            throw invalid("'" + name + "' must be a nonempty coordinate array");
        List<GHPoint> points = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            JsonNode point = values.get(index);
            if (!point.isArray() || point.size() != 2 || !point.get(0).isNumber() || !point.get(1).isNumber())
                throw invalid("'" + name + "' index " + index + " must be [longitude, latitude] with two numbers");
            points.add(new GHPoint(point.get(1).doubleValue(), point.get(0).doubleValue()));
        }
        return points;
    }

    private static void rejectUnknown(JsonNode object, Set<String> allowed, String name) {
        Iterator<String> fields = object.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field))
                throw invalid("Unsupported field '" + field + "' in " + name);
        }
    }

    private static MatrixException invalid(String message) {
        return new MatrixException("INVALID_REQUEST", message);
    }

    private static int status(String code) {
        return switch (code) {
            case "INVALID_REQUEST", "INVALID_JSON", "UNKNOWN_PROFILE", "INVALID_SNAP", "CONNECTION_NOT_FOUND",
                    "UNSUPPORTED_PROFILE", "UNSUPPORTED_ALGORITHM" -> 400;
            case "REQUEST_LIMIT_EXCEEDED" -> 413;
            case "SERVICE_BUSY" -> 503;
            case "DEADLINE_EXCEEDED" -> 504;
            case "SEARCH_LIMIT_EXCEEDED" -> 422;
            default -> 500;
        };
    }

    private static Response error(int status, String code, String message) {
        return Response.status(status).entity(Map.of("message", message,
                "hints", List.of(Map.of("message", message, "details", code)))).build();
    }
}
