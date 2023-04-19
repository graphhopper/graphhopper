
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

export default class Leg {
  get geometry() {
    return this._geometry;
  }

  get type() {
    return this._type;
  }

  get turns() {
    return this._turns;
  }

  get distance() {
    return this._distance;
  }

  get departureTime() {
    return this._departureTime;
  }

  get arrivalTime() {
    return this._arrivalTime;
  }

  constructor(apiLeg, type) {
    this._geometry = apiLeg.geometry;
    this._type = type;
    this._departureTime = apiLeg.departure_time;
    this._arrivalTime = apiLeg.arrival_time;
    this._turns = this.initializeTurns(apiLeg);
    this._distance = this.initializeDistance(apiLeg);
  }

  initializeTurns(apiLeg) {
    throw Error("initializeTurns must be implemented by subclass");
  }

  initializeDistance(apiLeg) {
    throw Error("initializeLegDistance must be implemented by subclass");
  }
}

export const LegMode = {
  WALK: "walk",
  PT: "pt"
};
