/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;

/**
 * @author Peter Karich
 */
public class GHBaseServlet extends HttpServlet
{
    protected static Logger logger = LoggerFactory.getLogger(GHBaseServlet.class);
    @Inject
    @Named("jsonpAllowed")
    private boolean jsonpAllowed;

    protected void writeJson( HttpServletRequest req, HttpServletResponse res, JSONObject json ) throws JSONException, IOException
    {
        String type = getParam(req, "type", "json");
        res.setCharacterEncoding("UTF-8");
        boolean debug = getBooleanParam(req, "debug", false) || getBooleanParam(req, "pretty", false);
        if ("jsonp".equals(type))
        {
            res.setContentType("application/javascript");
            if (!jsonpAllowed)
            {
                writeError(res, SC_BAD_REQUEST, "Server is not configured to allow jsonp!");
                return;
            }

            String callbackName = getParam(req, "callback", null);
            if (callbackName == null)
            {
                writeError(res, SC_BAD_REQUEST, "No callback provided, necessary if type=jsonp");
                return;
            }

            if (debug)
                writeResponse(res, callbackName + "(" + json.toString(2) + ")");
            else
                writeResponse(res, callbackName + "(" + json.toString() + ")");

        } else
        {
            res.setContentType("application/json");
            if (debug)
                writeResponse(res, json.toString(2));
            else
                writeResponse(res, json.toString());
        }
    }

    protected void writeError( HttpServletResponse res, int code, String message )
    {
        JSONObject json = new JSONObject();
        json.put("message", message);
        writeJsonError(res, code, json);
    }

    protected void writeJsonError( HttpServletResponse res, int code, JSONObject json )
    {
        try
        {
            // no type parameter check here as jsonp does not work if an error
            // also no debug parameter yet
            res.setContentType("application/json");
            res.setCharacterEncoding("UTF-8");
            res.setStatus(code);
            res.getWriter().append(json.toString(2));
        } catch (IOException ex)
        {
            logger.error("Cannot write error " + ex.getMessage());
        }
    }

    protected String getParam( HttpServletRequest req, String string, String _default )
    {
        String[] l = req.getParameterMap().get(string);
        if (l != null && l.length > 0)
            return l[0];

        return _default;
    }

    protected String[] getParams( HttpServletRequest req, String string )
    {
        String[] l = req.getParameterMap().get(string);
        if (l != null && l.length > 0)
        {
            return l;
        }
        return new String[0];
    }

    protected long getLongParam( HttpServletRequest req, String string, long _default )
    {
        try
        {
            return Long.parseLong(getParam(req, string, "" + _default));
        } catch (Exception ex)
        {
            return _default;
        }
    }

    protected boolean getBooleanParam( HttpServletRequest req, String string, boolean _default )
    {
        try
        {
            return Boolean.parseBoolean(getParam(req, string, "" + _default));
        } catch (Exception ex)
        {
            return _default;
        }
    }

    protected double getDoubleParam( HttpServletRequest req, String string, double _default )
    {
        try
        {
            return Double.parseDouble(getParam(req, string, "" + _default));
        } catch (Exception ex)
        {
            return _default;
        }
    }

    public void writeResponse( HttpServletResponse res, String str )
    {
        try
        {
            res.setStatus(SC_OK);
            res.getWriter().append(str);
        } catch (IOException ex)
        {
            logger.error("Cannot write message:" + str, ex);
        }
    }
}
