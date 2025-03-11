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

import {WaypointType} from "../../data/Waypoint.js";

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

class LeafletComponent extends React.Component {
    constructor(props) {
        super(props);
    }

    componentDidMount() {
        this.map = new mapboxgl.Map({
            container: this.leafletRoot,
            style: {
                'version': 8,
                'sources': {
                    'raster-tiles-source': {
                        'type': 'raster',
                        'tiles': ['https://tile.thunderforest.com/transport/{z}/{x}/{y}.png?apikey=88820e96998441019bb5876b601c6084']
                    },
                    'gh-mvt': {
                        'type': 'vector',
                        'tiles': ['http://' + window.location.host + '/pt-mvt/{z}/{x}/{y}.mvt']
                    },
                    'selected': {
                        'type': 'geojson',
                        data: {
                            type: "FeatureCollection",
                            features: []
                        }
                    },
                    'unselected': {
                        'type': 'geojson',
                        data: {
                            type: "FeatureCollection",
                            features: []
                        }
                    }
                },
                'layers': [
                    {
                        'id': 'raster-tiles',
                        'type': 'raster',
                        'source': 'raster-tiles-source'
                    },
                    {
                        'id': 'gh',
                        'type': 'circle',
                        'source': 'gh-mvt',
                        'source-layer': 'stops',
                        paint: {
                            'circle-color': 'black',
                            'circle-radius': {
                                'base': 1.75,
                                'stops': [
                                    [12, 2],
                                    [22, 180]
                                ]
                            }
                        }
                    },
                    {
                        'id': 'unselected',
                        'type': 'line',
                        'source': 'unselected',
                        paint: {
                            'line-width': 4,
                            'line-color': "#858687"
                        }
                    },
                    {
                        'id': 'selected',
                        'type': 'line',
                        'source': 'selected',
                        paint: {
                            'line-width': 4,
                            'line-color': [
                                'match',
                                ['get', 'type'],
                                'pt',
                                '#015eaf',
                                '#87edff',
                            ]
                        }
                    },
                    {
                        'id': 'selected-waypoint-markers',
                        'type': 'circle',
                        'source': 'selected',
                        'filter': ['==', '$type', 'Point'],
                        paint: {
                            'circle-radius': {
                                'base': 1.75,
                                'stops': [
                                    [12, 2],
                                    [22, 180]
                                ]
                            },
                            'circle-color': 'white'
                        }
                    }
                ]
            }
        });
        this._markers = [];
        let bbox = this.props.info.bbox;
        this.map.fitBounds([
            [bbox[0], bbox[1]],
            [bbox[2], bbox[3]]
        ], {
            animate: false,
            padding: 50
        });
        this.map.on('click', e => this.props.onSubmit((prevState) => {
            if (prevState.from === null) return {from: e.lngLat};
            else if (prevState.to === null) return {to: e.lngLat};
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

            let marker1 = new mapboxgl.Marker({
                draggable: true
            }).setLngLat(from);
            let wurst = this;
            marker1.on("dragend", e => wurst.props.onSubmit({
                from: marker1.getLngLat()
            }));

            let marker2 = new mapboxgl.Marker({
                draggable: true
            }).setLngLat(to);
            marker2.on("dragend", e => wurst.props.onSubmit({
                to: marker2.getLngLat()
            }));

            marker1.addTo(this.map);
            this._markers.push(marker1);
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

    setNewPaths(paths, selectedRouteIndex) {
        if (this.map.getSource('selected') && this.map.getSource('unselected')) {
            this.map.getSource('selected').setData({
                type: "FeatureCollection",
                features: this._createFeatures(paths[selectedRouteIndex])
            });
            this.map.getSource('unselected').setData({
                type: "FeatureCollection",
                features: paths.flatMap(path => this._createFeatures(path))
            });
        }
    }

    clearPaths() {
        if (this.map.getSource('selected') && this.map.getSource('unselected')) {
            this.map.getSource('selected').setData({
                type: "FeatureCollection",
                features: []
            });
            this.map.getSource('unselected').setData({
                type: "FeatureCollection",
                features: []
            });
        }
    }

    _createFeatures(path) {
        const features = [];
        path.legs.forEach(leg => {
            features.push({
                type: "Feature",
                properties: {
                    type: leg.type
                },
                geometry: leg.geometry
            });
        });
        path.waypoints.forEach(waypoint => {
            if (waypoint.type === WaypointType.INBEETWEEN) {
                features.push({
                    type: "Feature",
                    properties: {
                        type: "waypoint"
                    },
                    geometry: waypoint.geometry
                });
            }
        });
        return features;
    }
}
