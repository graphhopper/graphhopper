var ghenv = require("./options.js").options;
var tfAddition = '';
if (ghenv.thunderforest.api_key)
    tfAddition = '?apikey=' + ghenv.thunderforest.api_key;

var mapilionAddition = '';
if (ghenv.mapilion.api_key)
    mapilionAddition = '?key=' + ghenv.mapilion.api_key;

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

var kurvigerLiberty = L.tileLayer('https://{s}-tiles.mapilion.com/raster/styles/kurviger-liberty/{z}/{x}/{y}{r}.png'+mapilionAddition, {
    subdomains: ['a', 'b', 'c', 'd', 'e'],
    attribution: osmAttr + ',&copy; <a href="https://kurviger.de/" target="_blank">Kurviger</a> &copy; <a href="https://mapilion.com/attribution" target="_blank">Mapilion</a> <a href="http://www.openmaptiles.org/" target="_blank">&copy; OpenMapTiles</a>'
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
    "Kurviger Liberty": kurvigerLiberty,
    "Lyrk": lyrk,
    "WanderReitKarte": wrk,
    "Sorbian Language": sorbianLang,
    "OpenStreetMap.de": osmde
};

var overlays;
module.exports.enableVectorTiles = function () {
    var omniscaleGray = L.tileLayer('https://maps.omniscale.net/v2/' +osAPIKey + '/style.grayscale/layers.world,buildings,landusages,labels/{z}/{x}/{y}.png?' + (retinaTiles ? '&hq=true' : ''), {
        layers: 'osm',
        attribution: osmAttr + ', &copy; <a href="https://maps.omniscale.com/">Omniscale</a>'
    });
    availableTileLayers["Omniscale Dev"] = omniscaleGray;

    require('leaflet.vectorgrid');
    var vtLayer = L.vectorGrid.protobuf("/mvt/{z}/{x}/{y}.mvt?details=max_speed&details=road_class&details=road_environment", {
      rendererFactory: L.canvas.tile,
      maxZoom: 20,
      minZoom: 10,
      interactive: true,
      vectorTileLayerStyles: {
        'roads': function(properties, zoom) {
            // weight == line width
            var color, opacity = 1, weight = 1, rc = properties.road_class;
            // if(properties.speed < 30) console.log(properties)
            if (rc == "motorway") {
                color = '#dd504b'; // red
                weight = 3;
            } else if (rc == "primary" || rc == "trunk") {
                color = '#e2a012'; // orange
                weight = 2;
            } else if (rc == "secondary") {
                weight = 2;
                color = '#f7c913'; // yellow
            } else {
                color = "#aaa5a7"; // grey
            }
            if (zoom > 16)
                weight += 3;
            else if (zoom > 15)
                weight += 2;
            else if (zoom > 13)
                weight += 1;

            return {
                weight: weight,
                color: color,
                opacity: opacity
            }
        },
      },
    })
    var urbanDensityLayer = L.vectorGrid.protobuf("/mvt/{z}/{x}/{y}.mvt?details=max_speed&details=road_class&details=road_environment&details=urban_density", {
        rendererFactory: L.canvas.tile,
        maxZoom: 20,
        minZoom: 10,
        interactive: true,
        vectorTileLayerStyles: {
            'roads': function (properties, zoom) {
                var ud = properties.urban_density;
                let c = getUrbanDensityColor(ud);
                return {
                    weight: 1 + c.weight,
                    color: c.color,
                    opacity: 1
                }
            },
        },
    });
    var vtLayers = [vtLayer, urbanDensityLayer];
    for (var i = 0; i < vtLayers.length; ++i) {
        vtLayers[i]
            .on('click', function (e) {
            })
            .on('mouseover', function (e) {
                console.log(e.layer.properties);
                // remove route info
                $("#info").children("div").remove();
                // remove last vector tile info
                $("#info").children("ul").remove();

                var list = "";
                $.each(e.layer.properties, function (key, value) {
                    list += "<li>" + key + ": " + value + "</li>";
                });
                $("#info").append("<ul>" + list + "</ul>");
                $("#info").show();
            }).on('mouseout', function (e) {
                // $("#info").html("");
            }
        );
    }
    overlays = {
        "Local MVT": vtLayer,
        "Show Urban Density": urbanDensityLayer
    };
}

function getUrbanDensityColor(urbanDensity) {
    var color = '#0aaff1';
    if (urbanDensity === "residential") color = '#fd084a';
    else if (urbanDensity === "city") color = '#edf259';
    return {
        weight: 1,
        color: color
    }
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
