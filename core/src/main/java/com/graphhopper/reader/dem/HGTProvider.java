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

package com.graphhopper.reader.dem;

import com.graphhopper.util.Helper;

import java.io.*;
import java.nio.file.Files;
import java.util.zip.ZipInputStream;

public class HGTProvider extends AbstractSRTMElevationProvider {
    public HGTProvider(String dir) {
        super("", dir, "", Integer.MIN_VALUE, Integer.MAX_VALUE, 3601);
    }

    @Override
    byte[] readFile(File file) throws IOException {
        InputStream is = Files.newInputStream(file.toPath());
        ZipInputStream zis = new ZipInputStream(is);
        zis.getNextEntry();
        BufferedInputStream buff = new BufferedInputStream(zis);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[0xFFFF];
        int len;
        while ((len = buff.read(buffer)) > 0) {
            os.write(buffer, 0, len);
        }
        os.flush();
        Helper.close(buff);
        return os.toByteArray();
    }

    @Override
    String getFileName(double lat, double lon) {
        int latInt = (int) Math.floor(lat);
        int lonInt = (int) Math.floor(lon);
        return cacheDir + "/" + (lat > 0 ? "N" : "S") + getPaddedLatString(latInt) + (lon > 0 ? "E" : "W") + getPaddedLonString(lonInt) + ".hgt.zip";
    }

    @Override
    String getDownloadURL(double lat, double lon) {
        return getFileName(lat, lon);
    }
}
