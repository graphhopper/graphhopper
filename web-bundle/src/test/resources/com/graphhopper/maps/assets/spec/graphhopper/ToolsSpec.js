var graphhopperTools = requireFile('./graphhopper/tools.js');

describe('graphhopper tools', function () {
    it("should decode the polyline", function () {
        var list = graphhopperTools.decodePath("_p~iF~ps|U", false);
        expect(list).toEqual([[-120.2, 38.5]]);

        list = graphhopperTools.decodePath("_p~iF~ps|U_ulLnnqC_mqNvxq`@", false);
        expect(list).toEqual([[-120.2, 38.5], [-120.95, 40.7], [-126.45300000000002, 43.252]]);
    });

    it("should decode the 3D polyline", function () {
        var list = graphhopperTools.decodePath("_p~iF~ps|Uo}@", true);
        expect(list).toEqual([[-120.2, 38.5, 10]]);

        list = graphhopperTools.decodePath("_p~iF~ps|Uo}@_ulLnnqC_anF_mqNvxq`@?", true);
        expect(list).toEqual([[-120.2, 38.5, 10], [-120.95, 40.7, 1234], [-126.45300000000002, 43.252, 1234]]);
    });
});

