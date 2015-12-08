var urlTools = requireFile('./tools/url.js');

describe('urlTools', function () {
    it("should parse URL correctly", function () {
        var params = urlTools.parseUrl("localhost:8989?test=pest&test2=true&test3=false&test4=2&test5=1.1&test5=2.7");
        expect("pest").toEqual(params.test);
        expect(true).toEqual(params.test2);
        expect(false).toEqual(params.test3);
        expect("2").toEqual(params.test4);
        expect(["1.1", "2.7"]).toEqual(params.test5);

        // force array for point
        // URLs with one point only should work: https://graphhopper.com/maps/?point=50.413331%2C11.699066
        params = urlTools.parseUrl("blup?point=49.946505%2C11.571232&point=&");
        expect(params.point).toEqual(["49.946505,11.571232", ""]);

        params = urlTools.parseUrl("blup?point=&point=49.946505%2C11.571232");
        expect(params.point).toEqual(["", "49.946505,11.571232"]);
    });
});
