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

package com.graphhopper.application.resources;

import io.dropwizard.core.Application;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

@ExtendWith(DropwizardExtensionsSupport.class)
public class Dropwizard3Test {

    @Path("/")
    public static class MyResource {
        @POST
        public Response doPost() {
            return Response.ok().build();
        }
    }

    public static class MyApp extends Application<Configuration> {

        @Override
        public void run(Configuration configuration, Environment environment) throws Exception {
            environment.jersey().register(new MyResource());
        }
    }

    private static final DropwizardAppExtension<Configuration> app = new DropwizardAppExtension<>(MyApp.class, new Configuration());

    @Test
    public void myTest() {
        for (int i = 0; i < 10; i++) {
            post();
            System.out.println("post " + i + " complete");
        }
    }

    private static void post() {
        Response response = app.client().target("http://localhost:8080/not_found").request().post(Entity.json("{}"));
        System.out.println("success: " + response.getStatus());
    }
}
