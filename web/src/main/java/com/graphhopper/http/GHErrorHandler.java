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

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Peter Karich
 */
public class GHErrorHandler extends ErrorHandler {
    private static final long serialVersionUID = 1L;
    private final Logger logger = LoggerFactory.getLogger(GHErrorHandler.class);

    @Override
    public void handle(String str, Request req, HttpServletRequest httpReq, HttpServletResponse httpRes) throws IOException {
        Throwable throwable = (Throwable) httpReq.getAttribute("javax.servlet.error.exception");
        String url = httpReq.getRequestURI();
        if (httpReq.getQueryString() != null)
            url += "?" + httpReq.getQueryString();

        if (throwable != null) {
            logger.error("Internal error for request " + url, throwable);
        } else {
            String message = (String) httpReq.getAttribute("javax.servlet.error.message");
            if (httpRes.getStatus() / 100 == 4) {
                logger.warn("Bad request '" + message + "' " + url);
            } else if (message != null) {
                logger.error("Internal error with message " + message + " for " + url);
            } else {
                logger.error("Internal error with unknown throwable (" + str + ") for " + url);
            }
        }

        // you can't call sendError( 500, "Server Error" ) without triggering Jetty's DefaultErrorHandler
        httpRes.setStatus(httpRes.getStatus());
    }
}
