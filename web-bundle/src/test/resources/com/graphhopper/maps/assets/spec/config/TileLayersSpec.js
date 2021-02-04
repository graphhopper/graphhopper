describe('TileLayers', function() {
    var oldL;

    beforeEach(function () {
        oldL = global.L;
        global.L = {
            tileLayer: function () {
                return 'a tile layer';
            },
            Browser: {
                retina: false
            }
        };
        global.L.tileLayer.wms = function () {
            return 'a tile layer';
        };
    });

    it('gets the correct layer', function () {
        var tileLayers = requireFile('./config/tileLayers.js');

        var layer = tileLayers.selectLayer('bla');

        expect(layer).toBe('a tile layer');
    });

    afterEach(function () {
        global.L = oldL;
    });
});
