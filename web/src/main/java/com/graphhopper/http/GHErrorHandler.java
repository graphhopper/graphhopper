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

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class GHErrorHandler extends ErrorHandler
{
    private static final long serialVersionUID = 1L;
    private final Logger logger = LoggerFactory.getLogger(GHErrorHandler.class);

    @Override
    public void handle( String str, Request req, HttpServletRequest httpReq, HttpServletResponse httpRes ) throws IOException
    {
        Throwable throwable = (Throwable) httpReq.getAttribute("javax.servlet.error.exception");
        if (throwable != null)
        {
            String message = throwable.getMessage();
            logger.error(message, throwable);
        } else
            logger.error("Internal error, throwable not known!");

        // you can't call sendError( 500, "Server Error" ) without triggering Jetty's DefaultErrorHandler
        httpRes.setStatus(SC_INTERNAL_SERVER_ERROR);
    }
}
