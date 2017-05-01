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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Translation;
import com.graphhopper.util.TranslationMap;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;

/**
 * @author Peter Karich
 */
public class I18NServlet extends GHBaseServlet {
    @Inject
    private TranslationMap map;

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String locale = "";
        String path = req.getPathInfo();
        if (!Helper.isEmpty(path) && path.startsWith("/"))
            locale = path.substring(1);

        if (Helper.isEmpty(locale)) {
            // fall back to language specified in header e.g. via browser settings
            String acceptLang = req.getHeader("Accept-Language");
            if (!Helper.isEmpty(acceptLang))
                locale = acceptLang.split(",")[0];
        }

        Translation tr = map.get(locale);
        ObjectNode json = jsonNodeFactory.objectNode();
        if (tr != null && !Locale.US.equals(tr.getLocale()))
            json.putPOJO("default", tr.asMap());

        json.put("locale", locale);
        json.putPOJO("en", map.get("en").asMap());
        writeJson(req, res, json);
    }
}
