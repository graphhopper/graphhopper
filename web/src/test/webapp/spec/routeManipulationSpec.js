// because leaflet-src.js requires window, document etc. we need to provide these variables...
GLOBAL.window = {};
GLOBAL.document = {
    documentElement: {
        style: {}
    },
    getElementsByTagName: function() { return []; },
    createElement: function() { return {}; }
};
GLOBAL.navigator = {
    userAgent: 'nodejs'
};

var L = require('leaflet');
var routeManipulation = requireFile('./routeManipulation.js');

/*some test data with url parameters:
 ?point=52.550,13.352&point=52.547,13.358&point=52.541,13.368&point=52.542,13.376&point=52.542,13.383
  &point=52.545,13.391&point=52.542,13.408&point=52.539,13.424&point=52.533,13.441&point=52.527,13.447
  &point=52.533,13.476 */
var a  = L.latLng(52.5500, 13.3520);
var x1 = L.latLng(52.5470, 13.3580);
var x2 = L.latLng(52.5410, 13.3680);
var x3 = L.latLng(52.5420, 13.3760);
var b  = L.latLng(52.5420, 13.3830);
var x4 = L.latLng(52.5450, 13.3910);
var x5 = L.latLng(52.5420, 13.4080);
var c  = L.latLng(52.5390, 13.4240);
var x6 = L.latLng(52.5330, 13.4410);
var x7 = L.latLng(52.5270, 13.4470);
var d  = L.latLng(52.5330, 13.4760);

var clickLocations = [
    { latlng: L.latLng(52.567,13.342), expectedIndex: 1},
    { latlng: L.latLng(52.540,13.378), expectedIndex: 1},
    { latlng: L.latLng(52.542,13.396), expectedIndex: 2},
    { latlng: L.latLng(52.526,13.463), expectedIndex: 3},
    { latlng: L.latLng(52.526,13.519), expectedIndex: 3}
];

describe('getIntermediatePointIndex', function () {
    it('should work', function () {
        var routeSegments =  [{
            coordinates: [a, x1, x2, x3, b, x4, x5, c, x6, x7, d],
            wayPoints: [a, b, c, d]
        }];
        
        for(var i=0; i < clickLocations.length; ++i) {
            expect(routeManipulation.getIntermediatePointIndex(routeSegments, clickLocations[i].latlng))
                    .toEqual(clickLocations[i].expectedIndex);
        }
    });
});

describe('getIntermediatePointIndex', function () {
    it('should work when using alternative routes', function () {
        var wayPoints = [a, b, c, d];
        var routeSegments =  [{
            coordinates: [a, x1, x2, x3, b],
            wayPoints: wayPoints
        }, {
            coordinates: [b, x4, x5, c],
            wayPoints: wayPoints
        }, {
            coordinates: [c, x6, x7, d],
            wayPoints: wayPoints
        }];
    
        for(var i=0; i < clickLocations.length; ++i) {
            expect(routeManipulation.getIntermediatePointIndex(routeSegments, clickLocations[i].latlng))
                    .toEqual(clickLocations[i].expectedIndex);
        }
    });
});
