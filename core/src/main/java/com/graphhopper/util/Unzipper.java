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
package com.graphhopper.util;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Peter Karich
 */
public class Unzipper {
    public void unzip(String from, boolean remove) throws IOException {
        String to = Helper.pruneFileEnd(from);
        unzip(from, to, remove);
    }

    public boolean unzip(String fromStr, String toStr, boolean remove) throws IOException {
        File from = new File(fromStr);
        if (!from.exists() || fromStr.equals(toStr))
            return false;

        unzip(new FileInputStream(from), new File(toStr), null);

        if (remove)
            Helper.removeDir(from);
        return true;
    }

    /**
     * @param progressListener updates not in percentage but the number of bytes already read.
     */
    public void unzip(InputStream fromIs, File toFolder, ProgressListener progressListener) throws IOException {
        if (!toFolder.exists())
            toFolder.mkdirs();

        long sumBytes = 0;
        ZipInputStream zis = new ZipInputStream(fromIs);
        try {
            ZipEntry ze = zis.getNextEntry();
            byte[] buffer = new byte[8 * 1024];
            while (ze != null) {
                if (ze.isDirectory()) {
                    new File(toFolder, ze.getName()).mkdir();
                } else {
                    double factor = 1;
                    if (ze.getCompressedSize() > 0 && ze.getSize() > 0)
                        factor = (double) ze.getCompressedSize() / ze.getSize();

                    File newFile = new File(toFolder, ze.getName());
                    FileOutputStream fos = new FileOutputStream(newFile);
                    try {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                            sumBytes += len * factor;
                            if (progressListener != null)
                                progressListener.update(sumBytes);
                        }
                    } finally {
                        fos.close();
                    }
                }

                ze = zis.getNextEntry();
            }
            zis.closeEntry();
        } finally {
            zis.close();
        }
    }
}
