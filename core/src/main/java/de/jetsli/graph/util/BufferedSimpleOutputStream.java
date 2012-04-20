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
package de.jetsli.graph.util;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class BufferedSimpleOutputStream extends BufferedOutputStream {

    public BufferedSimpleOutputStream(OutputStream out, int size) {
        super(out, size);
    }

    public BufferedSimpleOutputStream(OutputStream out) {
        super(out);        
    }

    public final void writeFloat(float v) throws IOException {
        writeInt(Float.floatToIntBits(v));
    }

    public final void writeInt(int v) throws IOException {
        write((v >>> 24) & 0xFF);
        write((v >>> 16) & 0xFF);
        write((v >>> 8) & 0xFF);
        write((v >>> 0) & 0xFF);
    }
}
