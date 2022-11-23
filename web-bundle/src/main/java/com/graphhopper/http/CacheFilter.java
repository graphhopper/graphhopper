package com.graphhopper.http;

import org.eclipse.jetty.http.HttpMethod;

import javax.servlet.*;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class CacheFilter extends HttpFilter {

    private static final String CACHE_HEADER = "public, max-age=" + TimeUnit.DAYS.toSeconds(365);

    @Override
    public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (HttpMethod.GET.is(request.getMethod())) {
            response.setHeader("Cache-Control", CACHE_HEADER);
        }

        chain.doFilter(request, response);
    }
}
