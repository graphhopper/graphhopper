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

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps a public deployment from being flattened by whatever crawls it. Two limits, because they
 * guard against different things: a token bucket per client so nobody can loop over the map, and a
 * cap on how many requests run at once, since one request can hold the geometry of millions of
 * edges while it works.
 * <p>
 * Deliberately in-process and approximate - it is a doorstop, not a quota system.
 */
public class RateLimitFilter implements Filter {

    private static final int BURST = 20;
    private static final double PER_SECOND = 2;
    private static final int CONCURRENT = 4;
    /** stop the map of clients from growing without bound when many addresses show up once */
    private static final int MAX_CLIENTS = 20_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Semaphore inFlight = new Semaphore(CONCURRENT);
    private final AtomicLong lastSweep = new AtomicLong(System.currentTimeMillis());

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) req;
        // only the expensive endpoint, the map and its assets are cheap and cached anyway
        if (!http.getRequestURI().startsWith("/osm-issues")) {
            chain.doFilter(req, res);
            return;
        }
        if (!bucketFor(clientOf(http)).take()) {
            reject((HttpServletResponse) res, "too many requests, slow down");
            return;
        }
        if (!inFlight.tryAcquire()) {
            reject((HttpServletResponse) res, "server busy, try again in a moment");
            return;
        }
        try {
            chain.doFilter(req, res);
        } finally {
            inFlight.release();
        }
    }

    private static void reject(HttpServletResponse res, String message) throws IOException {
        res.setStatus(429);
        res.setHeader("Retry-After", "5");
        res.setContentType("application/json");
        res.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    /** behind a reverse proxy the socket address is the proxy, the first forwarded hop is the client */
    private static String clientOf(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isEmpty())
            return req.getRemoteAddr();
        int comma = forwarded.indexOf(',');
        return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
    }

    private Bucket bucketFor(String client) {
        long now = System.currentTimeMillis();
        long last = lastSweep.get();
        if (now - last > 600_000 && buckets.size() > MAX_CLIENTS && lastSweep.compareAndSet(last, now))
            buckets.entrySet().removeIf(e -> now - e.getValue().touched > 600_000);
        return buckets.computeIfAbsent(client, c -> new Bucket());
    }

    private static class Bucket {
        private double tokens = BURST;
        private long touched = System.currentTimeMillis();

        synchronized boolean take() {
            long now = System.currentTimeMillis();
            tokens = Math.min(BURST, tokens + (now - touched) / 1000.0 * PER_SECOND);
            touched = now;
            if (tokens < 1)
                return false;
            tokens--;
            return true;
        }
    }
}
