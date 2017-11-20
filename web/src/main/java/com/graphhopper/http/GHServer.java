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

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.servlet.GuiceFilter;
import com.graphhopper.util.CmdArgs;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.DispatcherType;
import java.util.EnumSet;

/**
 * Simple server similar to integration tests setup.
 */
public class GHServer {
    private final CmdArgs args;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private Server server;
    private Injector injector;

    public GHServer(CmdArgs args) {
        this.args = args;
    }

    public static void main(String[] args) throws Exception {
        new GHServer(CmdArgs.read(args)).start();
    }

    public void start() throws Exception {
        Injector injector = Guice.createInjector(createModule());
        start(injector);
    }

    public void start(Injector injector) throws Exception {
        if (this.injector != null)
            throw new IllegalArgumentException("Server already started");

        this.injector = injector;
        ResourceHandler resHandler = new ResourceHandler();
        resHandler.setDirectoriesListed(false);
        resHandler.setWelcomeFiles(new String[]{"index.html"});
        resHandler.setRedirectWelcome(false);

        String contextPath = args.get("jetty.contextpath", "/");
        ContextHandler contextHandler = new ContextHandler();
        contextHandler.setErrorHandler(new GHErrorHandler());
        contextHandler.setContextPath(contextPath);
        contextHandler.setBaseResource(Resource.newResource(args.get("jetty.resourcebase", "./web/src/main/webapp")));
        contextHandler.setHandler(resHandler);

        server = new Server();
        // getSessionHandler and getSecurityHandler should always return null
        ServletContextHandler servHandler = new ServletContextHandler(ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);
        servHandler.setContextPath(contextPath);

        // Putting this here (and not in the guice servlet module) because it should take precedence
        // over more specific routes. And guice, strangely, is order-dependent (even though, except in the servlet
        // extension, modules are _not_ supposed to be ordered).
        servHandler.addServlet(new ServletHolder(injector.getInstance(InvalidRequestServlet.class)), "/*");

        servHandler.addFilter(new FilterHolder(new GuiceFilter()), "/*", EnumSet.allOf(DispatcherType.class));

        ServerConnector connector0 = new ServerConnector(server);
        int httpPort = args.getInt("jetty.port", 8989);
        String host = args.get("jetty.host", "");
        connector0.setPort(httpPort);

        int requestHeaderSize = args.getInt("jetty.request_header_size", -1);
        if (requestHeaderSize > 0)
            connector0.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration().setRequestHeaderSize(requestHeaderSize);

        if (!host.isEmpty())
            connector0.setHost(host);

        server.addConnector(connector0);

        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{
                contextHandler, servHandler
        });

        GzipHandler gzipHandler = new GzipHandler();
        gzipHandler.setIncludedMethods("GET", "POST");
        // Note: gzip only affects the response body like our previous 'GHGZIPHook' behaviour: http://stackoverflow.com/a/31565805/194609
        // If no mimeTypes are defined the content-type is "not 'application/gzip'", See also https://github.com/graphhopper/directions-api/issues/28 for pitfalls
        // gzipHandler.setIncludedMimeTypes();
        gzipHandler.setHandler(handlers);

        GraphHopperService graphHopper = injector.getInstance(GraphHopperService.class);
        graphHopper.start();
        createCallOnDestroyModule("AutoCloseable for GraphHopper", graphHopper);

        server.setHandler(gzipHandler);
        server.setStopAtShutdown(true);
        server.start();
        logger.info("Started server at HTTP " + host + ":" + httpPort);
    }

    protected Module createModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                binder().requireExplicitBindings();
                if (args.has("gtfs.file")) {
                    // switch to different API implementation when using Pt
                    install(new PtModule(args));
                } else {
                    install(new GraphHopperModule(args));
                }
                install(new GraphHopperServletModule(args));
            }
        };
    }

    /**
     * Close resources on exit
     */
    public final void createCallOnDestroyModule(String name, final AutoCloseable closeable) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (closeable != null)
                    closeable.close();
            } catch (Exception ex) {
                if (logger != null)
                    logger.error("Cannot close " + name + " (" + closeable + ")", ex);
            }
        }, name));
    }

    public void stop() {
        if (server == null)
            return;

        try {
            server.stop();
        } catch (Exception ex) {
            logger.error("Cannot stop jetty", ex);
        }
    }

    Injector getInjector() {
        return injector;
    }
}
