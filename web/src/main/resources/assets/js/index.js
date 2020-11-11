// Import vendors
import 'angular'
import 'leaflet'
import 'leaflet-defaulticon-compatibility/dist/leaflet-defaulticon-compatibility';
import 'leaflet-routing-machine'
import 'lrm-graphhopper'
import 'ng-file-upload/dist/ng-file-upload-all.min'

import 'leaflet/dist/leaflet.css'
import 'leaflet-defaulticon-compatibility/dist/leaflet-defaulticon-compatibility.webpack.css'

import 'leaflet-routing-machine/dist/leaflet-routing-machine.css'
// Import styles
import './reset.sass';
import './index.sass';
// Import javascript
import './component/map.component.js'


import html from './index.html';

angular.module('FarmyVrpApp', []).component('farmyIndex', {
    bindings: {},
    controller: ($scope, $http) => {
        const COLORS = ['#808080', '#000000', '#FF0000', '#800000',
            '#FFFF00', '#808000', '#00FF00', '#008000',
            '#00FFFF', '#008080', '#0000FF', '#000080',
            '#FF00FF', '#800080', '#7D0552', '#F535AA',
            '#F87217', '#493D26', '#254117', '#1569C7'];

        let SELECTED_COLORS = [];

        $scope.optimizedRoutes = {};
        $scope.rs = {};

        $scope.loadOptimizedRoutes = function () {
            loadOptimizeRoutes();
        };
        $scope.rsDrawRoute = function () {
            L.Routing.control({
                waypoints: [
                    L.latLng(47.4133906,8.5170937),
                    L.latLng(47.4123083,8.5408663)
                ],
                routeWhileDragging: true,
                router: $scope.router
            }).addTo($scope.mymap);
        };

        function constructor() {
            buildMap();
        }

        function loadOptimizeRoutes() {
            console.debug('[DEV][MAP] Getting Routes');
            let lsRoutes = localStorage.getItem('dev.ls.routes');
            if(lsRoutes) {
                drawRoutes(JSON.parse(lsRoutes));
                console.debug('[DEV][MAP] From LocalStorage');
            } else {
                $http.get('http://localhost:8989/optimize-route').then((response)=>{
                    drawRoutes(response.data);
                    localStorage.setItem('dev.ls.routes', JSON.stringify(response.data));
                    console.debug('[DEV][MAP] Got Routes', response);
                });
            }

        }

        function buildRouter() {
            // Graph hopper open source
            $scope.router = L.Routing.graphHopper(undefined, {
                serviceUrl: 'http://localhost:8989/route'
            });
        }

        function buildMap() {
            let chCenterPoint = [47.369132,8.558135];
            $scope.mymap = L.map('content-map').setView(chCenterPoint, 15);
            buildTileLayer();
            buildRouter();
        }

        function buildTileLayer() {
            // Only switzerland
            L.tileLayer('https://tile.osm.ch/switzerland/{z}/{x}/{y}.png', {
                maxZoom: 18,
                attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
                bounds: [[45, 5], [48, 11]]
            }).addTo($scope.mymap);

            // All
            // L.tileLayer('http://{s}.tile.osm.org/{z}/{x}/{y}{r}.png', {
            //     attribution: '© OpenStreetMap contributors'
            // }).addTo($scope.mymap);

            // L.tileLayer('https://maps.wikimedia.org/osm-intl/${z}/${x}/${y}.png', {
            //     attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery © <a href="http://cloudmade.com">CloudMade</a>',
            //     maxZoom: 18
            // }).addTo($scope.mymap);
        }

        function drawRoutes(data) {
            Object.keys(data).forEach((vehicleId) => {
                $scope.optimizedRoutes[vehicleId] = {
                    color: selectColor(),
                    waypoints: data[vehicleId]
                };
                drawRoute(vehicleId, buildWaypoints(data[vehicleId]), data);
            });
            console.debug('[DEV][MAP] Optimized Routes', $scope.optimizedRoutes);
        }

        function buildWaypoints(waypointsData) {
            let wp = [];
            waypointsData.forEach(waypoint => {
               wp.push(L.latLng(waypoint[0], waypoint[1]));
            });
            return wp;
        }

        function drawRoute(vehicle, waypoints) {
            let vehicleRoute = $scope.optimizedRoutes[vehicle];
            if (vehicleRoute) {
                L.Routing.control({
                    waypoints: waypoints,
                    routeWhileDragging: true,
                    routeLine: (route) => {
                        return L.Routing.line(route, {
                            styles: [
                                {color: 'black', opacity: 0, weight: 0},
                                {color: 'blue', opacity: 0, weight: 0},
                                {color: vehicleRoute.color, opacity: .8, weight: 2}
                            ]
                        });
                    },
                    createMarker: (waypointIndex, waypoint, numberOfWaypoints) => {
                        return L.marker(waypoint.latLng).bindPopup(vehicleRoute.waypoints[waypointIndex][3]);
                    },
                    router: $scope.router,
                    plan: undefined
                }).addTo($scope.mymap);
            }
        }

        function selectColor() {
            let selectedCol = COLORS[Math.floor(Math.random() * COLORS.length)];
            if (SELECTED_COLORS.indexOf(selectedCol) === -1) {
                SELECTED_COLORS.push(selectedCol);
                return selectedCol;
            }
            return selectColor();
        }

        console.debug('[CI][Farmy][Index]');
        constructor();
    },
    template: html
});

// example request
// {"orders"=>
//   [{"id"=>0,
//     "order_id"=>9273471,
//     "address"=>"Rotbuchstrasse 71, Zürich, 8045, Zürich",
//     "number"=>"R416227486",
//     "distance"=>0.0,
//     "planned_time"=>"10:00",
//     "courier_key"=>"hub@farmy.ch",
//     "shipping_method_id"=>14,
//     "errors"=>[],
//     "courier_id_was"=>nil,
//     "courier_id"=>5896,
//     "original_shipping_method_id"=>14,
//     "comments"=>["Not proper final method"]},
//    {"id"=>1,
//     "order_id"=>9273474,
//     "address"=>"Pfingstweidstrasse 70, Zürich, 8001, Zürich",
//     "number"=>"R432246023",
//     "distance"=>5.0,
//     "planned_time"=>"10:15",
//     "courier_key"=>"hub@farmy.ch",
//     "shipping_method_id"=>14,
//     "errors"=>[],
//     "courier_id_was"=>nil,
//     "courier_id"=>5896,
//     "original_shipping_method_id"=>14,
//     "comments"=>["Not proper final method"]},