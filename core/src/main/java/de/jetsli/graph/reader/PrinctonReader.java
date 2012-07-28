/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.graph.reader;

import de.jetsli.graph.storage.Graph;
import de.jetsli.graph.util.Helper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Data taken from here
 * http://algs4.cs.princeton.edu/44sp/
 * 
 * @author Peter Karich, info@jetsli.de
 */
public class PrinctonReader {

    private Graph g;
    private InputStream is;

    public PrinctonReader(Graph graph) {
        g = graph;
    }

    public PrinctonReader setStream(InputStream is) {
        this.is = is;
        return this;
    }

    public void read() {        
        BufferedReader reader = new BufferedReader(new InputStreamReader(is), 8 * (1 << 10));
        int lineNo = 0;
        try {
            lineNo++;
            int nodes = Integer.parseInt(reader.readLine());
            lineNo++;
            int edges = Integer.parseInt(reader.readLine());
            for (int i = 0; i < edges; i++) {
                lineNo++;
                String line = reader.readLine();
                String args[] = line.split(" ");
                int from = -1;
                int to = -1;
                double dist = -1;
                int counter = 0;
                for (int j = 0; j < args.length; j++) {
                    if (args[j].isEmpty())
                        continue;

                    if (counter == 0)
                        from = Integer.parseInt(args[j]);
                    else if (counter == 1)
                        to = Integer.parseInt(args[j]);
                    else
                        dist = Double.parseDouble(args[j]);

                    counter++;
                    continue;
                }
                if (counter != 3)
                    throw new RuntimeException("incorrect read!? from:" + from + ", to:" + to + ", dist:" + dist);

                g.edge(from, to, dist, CarFlags.FORWARD);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Problem in line " + lineNo, ex);
        } finally {
            Helper.close(reader);
        }
    }
}
