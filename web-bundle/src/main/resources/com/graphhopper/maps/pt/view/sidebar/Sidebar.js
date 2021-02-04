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

import SearchInput from "./SearchInput.js";
import TripDisplay from "./TripDisplay.js";
export default (({
                   routes,
                   search,
                   onSearchChange,
                   onSelectIndex
                 }) => {
  function getSidebarContent(routes) {
    if (routes.isFetching) {
      return React.createElement(Spinner, null);
    } else if (routes.isLastQuerySuccess && routes.paths) {
      return React.createElement(TripDisplay, {
        trips: routes,
        onSelectIndex: onSelectIndex
      });
    } else return React.createElement(RequestError, null);
  }

  return React.createElement("div", {
    className: "sidebar"
  }, React.createElement(Logo, null), React.createElement(SearchInput, {
    search: search,
    onSearchChange: onSearchChange
  }), getSidebarContent(routes));
});

const Logo = () => {
  return React.createElement("div", {
    className: "logo"
  }, React.createElement("img", {
    src: "view/img/logo.png"
  }));
};

const Spinner = () => {
  return React.createElement("div", {
    className: "spinnerContainer"
  }, React.createElement("div", {
    className: "spinner"
  }, React.createElement("div", {
    className: "rect1"
  }), React.createElement("div", {
    className: "rect2"
  }), React.createElement("div", {
    className: "rect3"
  }), React.createElement("div", {
    className: "rect4"
  }), React.createElement("div", {
    className: "rect5"
  })));
};

const RequestError = () => {
  return React.createElement("div", {
    className: "spinnerContainer"
  }, React.createElement("span", null, "No route for selected Parameters."));
};