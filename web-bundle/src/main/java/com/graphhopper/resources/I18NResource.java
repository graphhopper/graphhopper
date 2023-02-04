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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.graphhopper.core.util.Translation;
import com.graphhopper.util.TranslationMap;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Locale;
import java.util.Map;

/**
 * @author Peter Karich
 */
@Path("i18n")
@Produces(MediaType.APPLICATION_JSON)
public class I18NResource {

    private final TranslationMap map;

    @Inject
    public I18NResource(TranslationMap map) {
        this.map = map;
    }

    public static class Response {
        public String locale;
        public Map<String, String> en;
        @JsonProperty("default")
        public Map<String, String> defaultTr;
    }

    @GET
    public Response getFromHeader(@HeaderParam("Accept-Language") String acceptLang) {
        if (acceptLang == null)
            return get("");
        return get(acceptLang.split(",")[0]);
    }

    @GET
    @Path("/{locale}")
    public Response get(@PathParam("locale") String locale) {
        Translation tr = map.get(locale);
        Response json = new Response();
        if (tr != null && !Locale.US.equals(tr.getLocale())) {
            json.defaultTr = tr.asMap();
        }
        json.locale = locale;
        json.en = map.get("en").asMap();
        return json;
    }

}
