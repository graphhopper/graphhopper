/*
 *  Copyright 2012 Peter Karich 
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
package com.graphhopper.storage;

/**
 * @author Peter Karich
 */
public interface Directory {

    String getLocation();

    DataAccess createDataAccess(String name);
    // TODO clear means clearing the in-memory map and deleting underlying files
    // Problem with clear was: MMapDataAccess creates file in constructor and on createNew
    // it clears but then the file won't be accessible. Also clearing would mean to avoid
    // closing of underlying DataAccess object which isn't good.
//    void clear();
}
