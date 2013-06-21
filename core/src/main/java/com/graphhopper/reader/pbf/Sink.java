package com.graphhopper.reader.pbf;

import com.graphhopper.reader.OSMElement;

/**
 * Created with IntelliJ IDEA. User: Nop Date: 29.05.13 Time: 23:58 To change this template use File
 * | Settings | File Templates.
 */
public interface Sink
{
    void process( OSMElement item );

    void complete();
}
