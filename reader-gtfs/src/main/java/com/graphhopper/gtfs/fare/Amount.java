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

package com.graphhopper.gtfs.fare;

import java.math.BigDecimal;

public class Amount {

    private final BigDecimal amount;
    private final String currencyType;

    public Amount(BigDecimal amount, String gtfs_currency_type) {
        this.amount = amount;
        this.currencyType = gtfs_currency_type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyType() {
        return currencyType;
    }

}
