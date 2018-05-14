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
package com.graphhopper.tools;

import com.graphhopper.util.Helper;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Simple bzip2 uncompression. TODO integrate with OSMReader!
 */
public class Bzip2 {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("You need to specify the bz2 file!");
        }

        String fromFile = args[0];
        if (!fromFile.endsWith(".bz2")) {
            throw new IllegalArgumentException("You need to specify a bz2 file! But was:" + fromFile);
        }
        String toFile = Helper.pruneFileEnd(fromFile);

        FileInputStream in = new FileInputStream(fromFile);
        FileOutputStream out = new FileOutputStream(toFile);
        BZip2CompressorInputStream bzIn = new BZip2CompressorInputStream(in);
        try {
            final byte[] buffer = new byte[1024 * 8];
            int n = 0;
            while (-1 != (n = bzIn.read(buffer))) {
                out.write(buffer, 0, n);
            }
        } finally {
            out.close();
            bzIn.close();
        }
    }
}
