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

import com.graphhopper.search.Geocoding;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.graphhopper.GraphHopper;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.TranslationMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public class DefaultModule extends AbstractModule
{
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final CmdArgs args;

    public DefaultModule( CmdArgs args )
    {
        this.args = args;
    }

    @Override
    protected void configure()
    {
        try
        {
            GraphHopper hopper = new GraphHopper().forServer().init(args);
            hopper.importOrLoad();
            logger.info("loaded graph at:" + hopper.getGraphHopperLocation()
                    + ", source:" + hopper.getOSMFile()
                    + ", acceptWay:" + hopper.getEncodingManager()
                    + ", class:" + hopper.getGraph().getClass().getSimpleName());

            bind(GraphHopper.class).toInstance(hopper);

            String algo = args.get("routing.defaultAlgorithm", "dijkstrabi");
            bind(String.class).annotatedWith(Names.named("defaultAlgorithm")).toInstance(algo);

            long timeout = args.getLong("web.timeout", 3000);
            bind(Long.class).annotatedWith(Names.named("timeout")).toInstance(timeout);
            bind(Geocoding.class).toInstance(new NominatimGeocoder().
                    setTimeout((int) timeout).
                    setBounds(hopper.getGraph().getBounds()));

            bind(TranslationMap.class).toInstance(new TranslationMap().doImport());
        } catch (Exception ex)
        {
            throw new IllegalStateException("Couldn't load graph", ex);
        }
    }
}
