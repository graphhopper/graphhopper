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

import { WaypointType } from "../../data/Waypoint.js";
import Point from "../../data/Point.js";
export default (({
                   info,
                   routes,
                   from,
                   to,
                   onSubmit = {
                     onSubmit
                   }
                 }) => {
  return React.createElement(LeafletComponent, {
    info: info,
    routes: routes,
    from: from,
    to: to,
    onSubmit: onSubmit
  });
});

const Leaflet = window.L;

class LeafletComponent extends React.Component {
  constructor(props) {
    super(props);
  }

  componentDidMount() {
    this.map = Leaflet.map(this.leafletRoot);
    this.map.zoomControl.setPosition("topright");
    this._markers = [];
    Leaflet.tileLayer("https://{s}.tile.thunderforest.com/transport/{z}/{x}/{y}.png?apikey=2ff19cdf28f249e2ba8e14bc6c083b39", {
      attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>',
      maxZoom: 18
    }).addTo(this.map);
    this.unselectedLayer = this._createGeoJsonLayer(f => this._getUnselectedStyle(f), (f, latLng) => null);
    this.selectedLayer = this._createGeoJsonLayer(f => this._getSelectedStyle(f), (f, latLng) => this._getCircleMarker({
      latLng: latLng,
      undefined,
      iconUrl: "view/map/circle.png",
      iconSize: 10
    }));
    let bbox = this.props.info.bbox;
    this.map.fitBounds(Leaflet.latLngBounds(Leaflet.latLng(bbox[1], bbox[0]), Leaflet.latLng(bbox[3], bbox[2])));
    this.map.on('click', e => this.props.onSubmit((prevState) => {
      if (prevState.from === null) return {from: Point.createFromArray([e.latlng.lat, e.latlng.lng])};
      else if (prevState.to === null) return {to: Point.createFromArray([e.latlng.lat, e.latlng.lng])};
    }));
    this.setMarkers(this.props.from, this.props.to);
  }

  componentDidUpdate() {
    if (this.props.routes.isFetching || !this.props.routes.isLastQuerySuccess) {
      this.clearPaths();
    } else if (this.props.routes.paths && this.props.routes.paths.length > 0) {
      this.setNewPaths(this.props.routes.paths, this.props.routes.selectedRouteIndex);
    }
    this.setMarkers(this.props.from, this.props.to);
  }

  setMarkers(from, to) {
    if (from != null && to != null) {
      this._markers.forEach(marker => {
        marker.off();
        marker.remove();
      });

      this._markers = [];

      let marker1 = this._getCircleMarker({
        latLng: from.toArray(),
        iconUrl: "view/map/circle.png",
        iconSize: 20,
        onDragEnd: latLng => this.props.onSubmit({
          from: Point.createFromArray([latLng.lat, latLng.lng])
        })
      });

      marker1.addTo(this.map);

      this._markers.push(marker1);

      let marker2 = this._getCircleMarker({
        latLng: to.toArray(),
        iconSize: 20,
        onDragEnd: latLng => this.props.onSubmit({
          to: Point.createFromArray([latLng.lat, latLng.lng])
        })
      });

      marker2.addTo(this.map);

      this._markers.push(marker2);
    }
  }

  render() {
    return React.createElement("div", {
      className: "fill"
    }, React.createElement("div", {
      style: {
        height: "100%"
      },
      ref: div => {
        this.leafletRoot = div;
      }
    }));
  }

  _createGeoJsonLayer(style, marker) {
    return Leaflet.geoJSON(undefined, {
      style: style,
      pointToLayer: marker
    }).addTo(this.map);
  }

  _getCircleMarker({
                     latLng,
                     onDragEnd,
                     iconUrl,
                     iconSize
                   }) {
    let marker = Leaflet.marker(latLng, {
      draggable: false
    });

    if (onDragEnd) {
      marker.options.draggable = true;
      marker.on("dragend", e => onDragEnd(e.target._latlng));
    }

    if (iconUrl) {
      let icon = Leaflet.icon({
        iconUrl: iconUrl,
        iconAnchor: [iconSize / 2, iconSize / 2],
        iconSize: [iconSize, iconSize]
      });
      marker.setIcon(icon);
    }

    return marker;
  }

  setNewPaths(paths, selectedRouteIndex) {
    this.clearPaths();
    let featureCollections = paths.map(path => this._createFeatureCollection(path));
    featureCollections.forEach((collection, i) => {
      if (i === selectedRouteIndex) {
        this.selectedLayer.addData(collection);
      } else {
        this.unselectedLayer.addData(collection);
      }
    });
    this.selectedLayer.bringToFront();
  }

  clearPaths() {
    this.unselectedLayer.clearLayers();
    this.selectedLayer.clearLayers();
  }

  _createFeatureCollection(path) {
    const features = [];
    path.legs.forEach(leg => {
      let feature = this._createFeatureFromGeometry(leg.type, leg.geometry);

      features.push(feature);
    });
    path.waypoints.forEach(waypoint => {
      if (waypoint.type === WaypointType.INBEETWEEN) {
        let feature = this._createFeatureFromGeometry("waypoint", waypoint.geometry);

        features.push(feature);
      }
    });
    return {
      type: "FeatureCollection",
      features: features
    };
  }

  _createFeatureFromGeometry(type, geometry) {
    return {
      type: "Feature",
      properties: {
        type: type
      },
      geometry: geometry
    };
  }

  _getSelectedStyle(feature) {
    let style = this._getUnselectedStyle(feature);

    switch (feature.properties.type) {
      case "walk":
      case "pt":
        style.color = "#015eaf";
        break;

      default:
        style.color = "#87edff";
    }

    style.fillColor = style.color;
    return style;
  }

  _getUnselectedStyle(feature) {
    return {
      weight: 4,
      color: "#858687"
    };
  }

}