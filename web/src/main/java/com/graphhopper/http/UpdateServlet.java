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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.reader.datexupdates.LatLongMetaData;
import com.graphhopper.util.GraphEdgeUpdate;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GHPoint;

/**
 * Servlet to use GraphHopper in a remote application (mobile or browser). Attention: If type is
 * json it returns the points in GeoJson format (longitude,latitude) unlike the format "lat,lon"
 * used otherwise.
 * <p/>
 * @author Peter Karich
 */
public class UpdateServlet extends GHBaseServlet
{
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	@Inject
    private GraphHopper hopper;

    @Override
    public void doGet( HttpServletRequest req, HttpServletResponse res ) throws ServletException, IOException
    {
        try
        {
            updatePath(req, res);
        } catch (IllegalArgumentException ex)
        {
            writeError(res, SC_BAD_REQUEST, ex.getMessage());
        } catch (Exception ex)
        {
            logger.error("Error while executing request: " + req.getQueryString(), ex);
            writeError(res, SC_INTERNAL_SERVER_ERROR, "Problem occured:" + ex.getMessage());
        }
    }

    void updatePath( HttpServletRequest req, HttpServletResponse res ) throws Exception
    {
        List<GHPoint> infoPoints = getPoints(req);

        String vehicleStr = getParam(req, "vehicle", "CAR").toUpperCase();
        String speed = getParam(req, "speed", "");
        
        StopWatch sw = new StopWatch().start();
        GHResponse ghRsp;
        if (!hopper.getEncodingManager().supports(vehicleStr))
        {
            ghRsp = new GHResponse().addError(new IllegalArgumentException("Vehicle not supported: " + vehicleStr));
        } else if (0==speed.length()){
        	ghRsp = new GHResponse().addError(new IllegalArgumentException("Speed value not specified: "));
        } else
        {
        	for (GHPoint ghPoint : infoPoints) {
				LatLongMetaData update = new LatLongMetaData(speed, ghPoint.lat, ghPoint.lon);
				GraphEdgeUpdate.updateEdge(hopper, update);
			}
        	ghRsp = new GHResponse().setFound(true);
        }

        float took = sw.stop().getSeconds();
        String infoStr = req.getRemoteAddr() + " " + req.getLocale() + " " + req.getHeader("User-Agent");
        PointList points = ghRsp.getPoints();
        String logStr = req.getQueryString() + " " + infoStr + " " + infoPoints
                + ", time:" + Math.round(ghRsp.getMillis() / 60000f)
                + "min, points:" + points.getSize() + ", took:" + took
                + ", debug - " + ghRsp.getDebugInfo();

        if (ghRsp.hasErrors())
            logger.error(logStr + ", errors:" + ghRsp.getErrors());
        else
            logger.info(logStr);
    }

    String errorsToXML( List<Throwable> list ) throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();
        Element gpxElement = doc.createElement("gpx");
        gpxElement.setAttribute("creator", "GraphHopper");
        gpxElement.setAttribute("version", "1.1");
        doc.appendChild(gpxElement);

        Element mdElement = doc.createElement("metadata");
        gpxElement.appendChild(mdElement);

        Element errorsElement = doc.createElement("extensions");
        mdElement.appendChild(errorsElement);

        for (Throwable t : list)
        {
            Element error = doc.createElement("error");
            errorsElement.appendChild(error);
            error.setAttribute("message", t.getMessage());
            error.setAttribute("details", t.getClass().getName());
        }
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    private List<GHPoint> getPoints( HttpServletRequest req ) throws IOException
    {
        String[] pointsAsStr = getParams(req, "point");
        final List<GHPoint> infoPoints = new ArrayList<GHPoint>(pointsAsStr.length);
        for (String str : pointsAsStr)
        {
            String[] fromStrs = str.split(",");
            if (fromStrs.length == 2)
            {
                GHPoint place = GHPoint.parse(str);
                if (place != null)
                    infoPoints.add(place);
            }
        }

        return infoPoints;
    }
}
