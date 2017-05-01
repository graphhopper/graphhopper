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
package com.graphhopper.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.routing.util.HintsMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;

/**
 * @author Peter Karich
 */
public class GHBaseServlet extends HttpServlet {
    protected static final Logger logger = LoggerFactory.getLogger(GHBaseServlet.class);
    protected final JsonNodeFactory jsonNodeFactory = new JsonNodeFactory(false);

    @Inject
    protected ObjectMapper objectMapper;

    @Inject
    @Named("jsonp_allowed")
    private boolean jsonpAllowed;

    protected void writeJson(HttpServletRequest req, HttpServletResponse res, JsonNode json) throws IOException {
        String type = getParam(req, "type", "json");
        res.setCharacterEncoding("UTF-8");
        final boolean indent = getBooleanParam(req, "debug", false) || getBooleanParam(req, "pretty", false);
        ObjectWriter objectWriter = indent ? objectMapper.writer().with(SerializationFeature.INDENT_OUTPUT) : objectMapper.writer();
        if ("jsonp".equals(type)) {
            res.setContentType("application/javascript");
            if (!jsonpAllowed) {
                writeError(res, SC_BAD_REQUEST, "Server is not configured to allow jsonp!");
                return;
            }
            String callbackName = getParam(req, "callback", null);
            if (callbackName == null) {
                writeError(res, SC_BAD_REQUEST, "No callback provided, necessary if type=jsonp");
                return;
            }
            writeResponse(res, callbackName + "(" + objectWriter.writeValueAsString(json) + ")");
        } else {
            res.setContentType("application/json");
            writeResponse(res, objectWriter.writeValueAsString(json));
        }
    }

    protected void writeError(HttpServletResponse res, int code, String message) {
        ObjectNode json = jsonNodeFactory.objectNode();
        json.put("message", message);
        writeJsonError(res, code, json);
    }

    protected void writeJsonError(HttpServletResponse res, int code, JsonNode json) {
        try {
            // no type parameter check here as jsonp does not work if an error
            // also no debug parameter yet
            res.setContentType("application/json");
            res.setCharacterEncoding("UTF-8");
            res.setStatus(code);
            res.getWriter().append(objectMapper.writer().with(SerializationFeature.INDENT_OUTPUT).writeValueAsString(json));
        } catch (IOException ex) {
            throw new RuntimeException("Cannot write JSON Error " + code, ex);
        }
    }

    protected String getParam(HttpServletRequest req, String key, String _default) {
        String[] l = req.getParameterMap().get(key);
        if (l != null && l.length > 0)
            return l[0];

        return _default;
    }

    protected String[] getParams(HttpServletRequest req, String key) {
        String[] l = req.getParameterMap().get(key);
        if (l != null && l.length > 0) {
            return l;
        }
        return new String[0];
    }

    protected List<Double> getDoubleParamList(HttpServletRequest req, String key) {
        String[] l = req.getParameterMap().get(key);
        if (l != null && l.length > 0) {
            ArrayList<Double> doubleList = new ArrayList<Double>(l.length);
            for (String s : l) {
                doubleList.add(Double.valueOf(s));
            }
            return doubleList;
        }
        return Collections.emptyList();
    }

    protected long getLongParam(HttpServletRequest req, String key, long _default) {
        try {
            return Long.parseLong(getParam(req, key, "" + _default));
        } catch (Exception ex) {
            return _default;
        }
    }

    protected int getIntParam(HttpServletRequest req, String key, int _default) {
        try {
            return Integer.parseInt(getParam(req, key, "" + _default));
        } catch (Exception ex) {
            return _default;
        }
    }

    protected boolean getBooleanParam(HttpServletRequest req, String key, boolean _default) {
        try {
            return Boolean.parseBoolean(getParam(req, key, "" + _default));
        } catch (Exception ex) {
            return _default;
        }
    }

    protected double getDoubleParam(HttpServletRequest req, String key, double _default) {
        try {
            return Double.parseDouble(getParam(req, key, "" + _default));
        } catch (Exception ex) {
            return _default;
        }
    }

    public void writeResponse(HttpServletResponse res, String str) {
        try {
            res.setStatus(SC_OK);
            res.getWriter().append(str);
        } catch (IOException ex) {
            logger.error("Cannot write message:" + str, ex);
        }
    }

    protected void initHints(HintsMap m, Map<String, String[]> parameterMap) {
        for (Map.Entry<String, String[]> e : parameterMap.entrySet()) {
            if (e.getValue().length == 1)
                m.put(e.getKey(), e.getValue()[0]);
        }
    }
}
