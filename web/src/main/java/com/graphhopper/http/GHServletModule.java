/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.graphhopper.http;

import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Peter Karich
 */
public class GHServletModule extends ServletModule
{
    @Override
    protected void configureServlets()
    {
        Map<String, String> params = new HashMap<String, String>();
        params.put("mimeTypes", "text/html,"
                + "text/plain,"
                + "text/xml,"
                + "application/xhtml+xml,"
                + "text/css,"
                + "application/json,"
                + "application/javascript,"
                + "image/svg+xml");

        filter("/*").through(GHGZIPHook.class, params);
        bind(GHGZIPHook.class).in(Singleton.class);

        serve("/api/i18n*").with(I18NServlet.class);
        bind(I18NServlet.class).in(Singleton.class);

        serve("/api*").with(GraphHopperServlet.class);
        bind(GraphHopperServlet.class).in(Singleton.class);
    }
}
