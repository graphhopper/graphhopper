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

package com.conveyal.gtfs.model;

import com.google.common.collect.Lists;

import java.io.Serializable;
import java.util.List;

/**
 * This table does not exist in GTFS. It is a join of fare_attributes and fare_rules on fare_id.
 * There should only be one fare_attribute per fare_id, but there can be many fare_rules per fare_id.
 */
public class Fare implements Serializable {
    public static final long serialVersionUID = 1L;

    public String         fare_id;
    public FareAttribute  fare_attribute;
    public List<FareRule> fare_rules = Lists.newArrayList();

    public Fare(String fare_id) {
        this.fare_id = fare_id;
    }

}
