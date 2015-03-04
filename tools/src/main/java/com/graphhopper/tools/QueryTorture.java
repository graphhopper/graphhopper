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
package com.graphhopper.tools;

import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.Downloader;
import com.graphhopper.util.Helper;
import com.graphhopper.util.StopWatch;
import com.graphhopper.util.shapes.GHPoint;
import java.io.*;
import java.net.URLEncoder;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads log files and queries the live service
 * <p/>
 * @author Peter Karich
 */
public class QueryTorture
{
    public static void main( String[] args )
    {
        new QueryTorture().start(CmdArgs.read(args));
    }
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private ExecutorService service;
    private BlockingQueue<Query> queryQueue;
    private Set<Query> noDuplicate;
    private String baseUrl;
    private AtomicInteger successfullQueries;
    private AtomicInteger httpErrorCounter;
    private AtomicInteger routingErrorCounter;
    private CountDownLatch workerStartedBarrier;
    private CountDownLatch logfileEOFBarrier;
    private int skippedTooShort;
    private int readQueries;
    private int maxQueries;
    private int timeout;
    private int statusUpdateCnt;

    public QueryTorture()
    {
    }

    public void start( CmdArgs read )
    {
        String logfile = read.get("logfile", "");
        int workers = read.getInt("workers", 1);
        baseUrl = read.get("baseurl", "");
        maxQueries = read.getInt("maxqueries", 1000);
        timeout = read.getInt("timeout", 3000);
        statusUpdateCnt = maxQueries / 10;
        if (Helper.isEmpty(baseUrl))
            throw new IllegalArgumentException("baseUrl cannot be empty!?");

        if (!baseUrl.endsWith("/"))
            baseUrl += "/";
        if (!baseUrl.endsWith("route/"))
            baseUrl += "route/";
        if (!baseUrl.endsWith("?"))
            baseUrl += "?";

        // there should be enough feed available for the workers in the queue
        queryQueue = new LinkedBlockingQueue<Query>(workers * 100);
        noDuplicate = new HashSet<Query>();
        successfullQueries = new AtomicInteger(0);
        httpErrorCounter = new AtomicInteger(0);
        routingErrorCounter = new AtomicInteger(0);
        workerStartedBarrier = new CountDownLatch(workers);
        logfileEOFBarrier = new CountDownLatch(1);
        StopWatch sw = new StopWatch().start();
        Thread mainThread = startWorkers(workers);

        // start reading the logs and interrupt mainThread if no further entry available
        startReadingLogs(logfile);
        try
        {
            mainThread.join();
        } catch (Exception ex)
        {
            logger.info("End waiting", ex);
        }

        sw.stop();
        logger.info("Queries| read: " + readQueries
                + ", no dups:" + noDuplicate.size()
                + ", successful routes:" + successfullQueries.get()
                + ", too short:" + skippedTooShort
                + ", queue.size:" + queryQueue.size()
                + ", routing errors:" + routingErrorCounter.get()
                + ", http errors:" + httpErrorCounter.get());
        logger.info("took:" + sw.getSeconds());
        logger.info("throughput queries/sec:" + successfullQueries.get() / sw.getSeconds());
        logger.info("mean query time in sec:" + sw.getSeconds() / successfullQueries.get());
    }

    Thread startWorkers( final int workers )
    {
        Thread mainThread = new Thread("mainThread")
        {
            @Override
            public void run()
            {
                Collection<Callable<Object>> workerCollection = new ArrayList<Callable<Object>>(workers);
                for (int i = 0; i < workers; i++)
                {
                    final int workerNo = i;
                    workerCollection.add(new Callable<Object>()
                    {
                        @Override
                        public Object call() throws Exception
                        {
                            workerStartedBarrier.countDown();
                            try
                            {
                                while (!isInterrupted())
                                {
                                    if (logfileEOFBarrier.getCount() == 0 && queryQueue.isEmpty())
                                        break;

                                    execute(workerNo);
                                }
                            } catch (Throwable ex)
                            {
                                logger.error(getName() + " - worker " + workerNo + " died", ex);
                            }
                            return null;
                        }
                    });
                }
                service = Executors.newFixedThreadPool(workers);
                try
                {
                    logger.info(getName() + " started with " + workers + " workers");
                    service.invokeAll(workerCollection);
                    logger.info(getName() + " FINISHED");
                } catch (RejectedExecutionException ex)
                {
                    logger.info(getName() + " cannot create threads", ex);
                } catch (InterruptedException ex)
                {
                    // logger.info(getName() + " was interrupted", ex);
                }
            }
        };
        mainThread.start();
        return mainThread;
    }

    void execute( int workerNo ) throws InterruptedException
    {
        Query query = queryQueue.take();
        try
        {
            String url = baseUrl + query.createQueryString();
            String res = new Downloader("QueryTorture!").setTimeout(timeout).downloadAsString(url);
            if (res.contains("errors"))
                routingErrorCounter.incrementAndGet();
            else
                successfullQueries.incrementAndGet();

            if (successfullQueries.get() % statusUpdateCnt == 0)
            {
                logger.info("progress: " + (int) (successfullQueries.get() * 100 / maxQueries) + "%");
            }
        } catch (IOException ex)
        {
            // logger.error("Error while querying " + query.queryString, ex);
            httpErrorCounter.incrementAndGet();
        }
    }

    void startReadingLogs( final String logFile )
    {
        final DistanceCalc distCalc = new DistanceCalcEarth();
        new Thread("readLogFile")
        {
            @Override
            public void run()
            {
                try
                {
                    InputStream is;
                    if (logFile.endsWith(".gz"))
                        is = new GZIPInputStream(new FileInputStream(logFile));
                    else
                        is = new FileInputStream(logFile);

                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, Helper.UTF_CS));
                    try
                    {
                        String logLine;
                        while ((logLine = reader.readLine()) != null)
                        {
                            Query q = Query.parse(logLine);
                            if (q == null)
                                continue;

                            double dist = distCalc.calcDist(q.start.lat, q.start.lon, q.end.lat, q.end.lon);
                            if (dist < 100)
                            {
                                skippedTooShort++;
                                continue;
                            }
                            readQueries++;
                            if (noDuplicate.size() >= maxQueries)
                                break;
                            if (noDuplicate.add(q))
                                queryQueue.put(q);
                        }
                    } finally
                    {
                        reader.close();
                    }
                    // wait until all worker threads have started
                    workerStartedBarrier.await();
                    // now tell workers that we are ready with log reading
                    logfileEOFBarrier.countDown();
                    // now wait for termination
                    service.shutdown();
                } catch (Exception ex)
                {
                    logger.error("Stopped reading logs", ex);
                    // do not wait, just shut down
                    if (service != null)
                        service.shutdownNow();
                }
            }
        }.start();
    }

    static class Query
    {
        GHPoint start;
        GHPoint end;
        List<String> points = new ArrayList<String>();
        Map<String, String> params = new HashMap<String, String>();

        static Query parse( String logLine )
        {
            String START = "GraphHopperServlet - ";
            int index = logLine.indexOf(START);
            if (index < 0)
                return null;

            logLine = logLine.substring(index + START.length());
            index = logLine.indexOf(" ");
            if (index < 0)
                return null;

            Query q = new Query();
            String queryString = logLine.substring(0, index);
            String[] tmpStrings = queryString.split("\\&");
            for (String paramStr : tmpStrings)
            {
                int equalIndex = paramStr.indexOf("=");
                if (equalIndex <= 0)
                    continue;

                String key = paramStr.substring(0, equalIndex);
                String value = paramStr.substring(equalIndex + 1);
                if (!paramStr.startsWith("point="))
                {
                    q.params.put(key, value);
                    continue;
                }

                value = value.replace("%2C", ",");
                GHPoint point = GHPoint.parse(value);
                if (point == null)
                    continue;

                q.points.add(value);
                if (q.start == null)
                    q.start = point;
                else if (q.end == null)
                    q.end = point;
            }
            if (q.start != null && q.end != null)
                return q;

            return null;
        }

        public void put( String key, String value )
        {
            params.put(key, value);
        }

        public String createQueryString()
        {
            String qStr = "";
            for (String pointStr : points)
            {
                if (!qStr.isEmpty())
                    qStr += "&";

                qStr += "point=" + pointStr;
            }
            for (Entry<String, String> e : params.entrySet())
            {
                if (!qStr.isEmpty())
                    qStr += "&";

                qStr += e.getKey() + "=" + encodeURL(e.getValue());
            }

            return qStr;
        }

        static String encodeURL( String str )
        {
            try
            {
                return URLEncoder.encode(str, "UTF-8");
            } catch (Exception _ignore)
            {
                return str;
            }
        }

        @Override
        public String toString()
        {
            return createQueryString();
        }
    }
}
