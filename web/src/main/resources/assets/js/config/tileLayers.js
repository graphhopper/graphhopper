var ghenv = require("./options.js").options;
var tfAddition = '';
if (ghenv.thunderforest.api_key)
    tfAddition = '?apikey=' + ghenv.thunderforest.api_key;

var osAPIKey = 'mapsgraph-bf48cc0b';
if (ghenv.omniscale.api_key)
    osAPIKey = ghenv.omniscale.api_key;

var osmAttr = '&copy; <a href="http://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap</a> contributors';

// Automatically enable high-DPI tiles if provider and browser support it.
var retinaTiles = L.Browser.retina;

var lyrk = L.tileLayer('https://tiles.lyrk.org/' + (retinaTiles ? 'lr' : 'ls') + '/{z}/{x}/{y}?apikey=6e8cfef737a140e2a58c8122aaa26077', {
    attribution: osmAttr + ', <a href="https://geodienste.lyrk.de/">Lyrk</a>'
});

var omniscale = L.tileLayer('https://maps.omniscale.net/v2/' +osAPIKey + '/style.default/{z}/{x}/{y}.png' + (retinaTiles ? '?hq=true' : ''), {
    layers: 'osm',
    attribution: osmAttr + ', &copy; <a href="https://maps.omniscale.com/">Omniscale</a>'
});

var openMapSurfer = L.tileLayer('http://korona.geog.uni-heidelberg.de/tiles/roads/x={x}&y={y}&z={z}', {
    attribution: osmAttr + ', <a href="http://korona.geog.uni-heidelberg.de/contact.html">GIScience Heidelberg</a>'
});

// Not an option as too fast over limit.
// var mapbox= L.tileLayer('https://{s}.tiles.mapbox.com/v4/peterk.map-vkt0kusv/{z}/{x}/{y}' + (retinaTiles ? '@2x' : '') + '.png?access_token=pk.eyJ1IjoicGV0ZXJrIiwiYSI6IkdFc2FJd2MifQ.YUd7dS_gOpT3xrQnB8_K-w', {
//     attribution: osmAttr + ', <a href="https://www.mapbox.com/about/maps/">&copy; MapBox</a>'
// });

var sorbianLang = L.tileLayer('http://a.tile.openstreetmap.de/tiles/osmhrb/{z}/{x}/{y}.png', {
    attribution: osmAttr + ', <a href="http://www.alberding.eu/">&copy; Alberding GmbH, CC-BY-SA</a>'
});

var thunderTransport = L.tileLayer('https://{s}.tile.thunderforest.com/transport/{z}/{x}/{y}.png' + tfAddition, {
    attribution: osmAttr + ', <a href="https://www.thunderforest.com/maps/transport/" target="_blank">Thunderforest Transport</a>'
});

var thunderCycle = L.tileLayer('https://{s}.tile.thunderforest.com/cycle/{z}/{x}/{y}.png' + tfAddition, {
    attribution: osmAttr + ', <a href="https://www.thunderforest.com/maps/opencyclemap/" target="_blank">Thunderforest Cycle</a>'
});

var thunderOutdoors = L.tileLayer('https://{s}.tile.thunderforest.com/outdoors/{z}/{x}/{y}.png' + tfAddition, {
    attribution: osmAttr + ', <a href="https://www.thunderforest.com/maps/outdoors/" target="_blank">Thunderforest Outdoors</a>'
});

var thunderNeighbourhood = L.tileLayer('https://{s}.tile.thunderforest.com/neighbourhood/{z}/{x}/{y}.png' + tfAddition, {
    attribution: osmAttr + ', <a href="https://thunderforest.com/maps/neighbourhood/" target="_blank">Thunderforest Neighbourhood</a>'
});

var wrk = L.tileLayer('http://{s}.wanderreitkarte.de/topo/{z}/{x}/{y}.png', {
    attribution: osmAttr + ', <a href="http://wanderreitkarte.de" target="_blank">WanderReitKarte</a>',
    subdomains: ['topo4', 'topo', 'topo2', 'topo3']
});

var osm = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: osmAttr
});

var osmde = L.tileLayer('http://{s}.tile.openstreetmap.de/tiles/osmde/{z}/{x}/{y}.png', {
    attribution: osmAttr
});

var mapLink = '<a href="http://www.esri.com/">Esri</a>';
var wholink = 'i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community';
var esriAerial = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
    attribution: '&copy; ' + mapLink + ', ' + wholink,
    maxZoom: 18
});

var availableTileLayers = {
    "Omniscale": omniscale,
    "OpenStreetMap": osm,
    "Esri Aerial": esriAerial,
    "TF Transport": thunderTransport,
    "TF Cycle": thunderCycle,
    "TF Outdoors": thunderOutdoors,
    "TF Neighbourhood": thunderNeighbourhood,
    "Lyrk": lyrk,
    "WanderReitKarte": wrk,
    "OpenMapSurfer": openMapSurfer,
    "Sorbian Language": sorbianLang,
    "OpenStreetMap.de": osmde
};

var overlays;
if(ghenv.environment === 'development') {
    var omniscaleGray = L.tileLayer('https://maps.omniscale.net/v2/' +osAPIKey + '/style.grayscale/layers.world,buildings,landusages,labels/{z}/{x}/{y}.png?' + (retinaTiles ? '&hq=true' : ''), {
        layers: 'osm',
        attribution: osmAttr + ', &copy; <a href="https://maps.omniscale.com/">Omniscale</a>'
    });
    availableTileLayers["Omniscale Dev"] = omniscaleGray;

    require('leaflet.vectorgrid');
    overlays = {};
    overlays["Local MVT"] = L.vectorGrid.protobuf("http://127.0.0.1:8989/mvt/{z}/{x}/{y}.mvt?details=max_speed&details=road_class&details=road_environment", {
      rendererFactory: L.canvas.tile,
      maxZoom: 20,
      minZoom: 10,
      vectorTileLayerStyles: {
        'roads': function(properties, zoom) {
            var color, opacity = 1, weight = 1, radius = 2, rc = properties.road_class;
            // if(properties.speed < 30) console.log(properties)
            if(rc == "motorway") {
                color = '#dd504b'; // red
                weight = 3;
                radius = 6;
            } else if(rc == "primary" || rc == "trunk") {
                color = '#e2a012'; // orange
                weight = 2;
                radius = 6;
            } else if(rc == "secondary") {
                weight = 2;
                color = '#f7c913'; // yellow
            } else {
                if(zoom <= 11) {
                  // color = "white";
                  color = "#aaa5a7"
                  opacity = 0.2;
                } else {
                  color = "#aaa5a7"; /* gray */
                }
            }
            return {
                weight: weight,
                color: color,
                opacity: opacity,
                radius: radius
            }
        },
      },
      interactive: true // use true to make sure that this VectorGrid fires mouse/pointer events
    });
}

module.exports.activeLayerName = "Omniscale";
module.exports.defaultLayer = omniscale;

module.exports.getAvailableTileLayers = function () {
    return availableTileLayers;
};

module.exports.getOverlays = function () {
    return overlays;
};

module.exports.selectLayer = function (layerName) {
    var defaultLayer = availableTileLayers[layerName];
    if (!defaultLayer)
        defaultLayer = module.exports.defaultLayer;

    return defaultLayer;
};
