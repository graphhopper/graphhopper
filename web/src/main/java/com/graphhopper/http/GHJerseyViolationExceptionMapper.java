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

import com.graphhopper.http.api.JsonErrorEntity;
import com.graphhopper.util.Helper;
import io.dropwizard.jersey.validation.ConstraintMessage;
import io.dropwizard.jersey.validation.JerseyViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.stream.Collectors;

@Provider
public class GHJerseyViolationExceptionMapper implements ExceptionMapper<JerseyViolationException> {
    private static final Logger logger = LoggerFactory.getLogger(GHJerseyViolationExceptionMapper.class);

    @Override
    public Response toResponse(final JerseyViolationException e) {
        logger.info("jersey violation exception: " + (Helper.isEmpty(e.getMessage()) ? "unknown reason" : e.getMessage()));
        return Response
                .status(ConstraintMessage.determineStatus(e.getConstraintViolations(), e.getInvocable()))
                .type(MediaType.APPLICATION_JSON)
                .entity(new JsonErrorEntity(e.getConstraintViolations().stream().map(v -> new IllegalArgumentException(v.getMessage())).collect(Collectors.toList())))
                .build();
    }
}
