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
package com.graphhopper.util;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * Stores command line options in a map. The capitalization of the key is ignored.
 * <p>
 *
 * @author Peter Karich
 */
public class CmdArgs extends PMap {

    public CmdArgs() {
    }

    @JsonCreator
    public CmdArgs(Map<String, String> map) {
        super(map);
    }

    private static CmdArgs argsFromPropertiesFile(String configLocation) {
        CmdArgs cmdArgs = new CmdArgs();
        Map<String, String> map = new LinkedHashMap<>();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(
                new File(configLocation).getAbsoluteFile()), Helper.UTF_CS)) {
            Helper.loadProperties(map, reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        cmdArgs.merge(map);
        return cmdArgs;
    }

    private static CmdArgs argsFromSystemProperties() {
        CmdArgs cmdArgs = new CmdArgs();
        for (Entry<Object, Object> e : System.getProperties().entrySet()) {
            String k = ((String) e.getKey());
            String v = ((String) e.getValue());
            if (k.startsWith("graphhopper.")) {
                k = k.substring("graphhopper.".length());
                cmdArgs.put(k, v);
            }
        }
        return cmdArgs;
    }

    /**
     * This method creates a CmdArgs object from the specified string array (a list of key=value pairs).
     */
    public static CmdArgs read(String[] args) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String arg : args) {
            int index = arg.indexOf("=");
            if (index <= 0) {
                continue;
            }

            String key = arg.substring(0, index);
            if (key.startsWith("-")) {
                key = key.substring(1);
            }

            if (key.startsWith("-")) {
                key = key.substring(1);
            }

            String value = arg.substring(index + 1);
            String old = map.put(key.toLowerCase(), value);
            if (old != null)
                throw new IllegalArgumentException("Pair '" + key.toLowerCase() + "'='" + value + "' not possible to " +
                        "add to the CmdArgs-object as the key already exists with '" + old + "'");
        }

        return new CmdArgs(map);
    }

    public static CmdArgs readFromConfigAndMerge(CmdArgs args) {
        final CmdArgs argsFromSystemProperties = argsFromSystemProperties();
        args.merge(argsFromSystemProperties);

        String propertiesFile = args.get("config", "");
        if (!Helper.isEmpty(propertiesFile)) {
            final CmdArgs argsFromPropertiesFile = argsFromPropertiesFile(propertiesFile);
            argsFromPropertiesFile.merge(args);
            return argsFromPropertiesFile;
        }
        return args;
    }

    @Override
    public CmdArgs put(String key, Object str) {
        super.put(key, str);
        return this;
    }
}
