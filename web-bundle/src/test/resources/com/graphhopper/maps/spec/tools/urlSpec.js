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

    it("should create array out of point", function () {
        var params = urlTools.parseUrl("point=1&point=2");
        expect(["1", "2"]).toEqual(params.point);

        params = urlTools.parseUrl("x=1&x=2");
        expect(["1", "2"]).toEqual(params.x);
    });

    it("should force array for point", function () {
        var params = urlTools.mergeParamIntoObject({}, "point", "1");
        expect(["1"]).toEqual(params.point);

        params = urlTools.mergeParamIntoObject(params, "point", "2");
        expect(["1", "2"]).toEqual(params.point);
    })

    it("should create object from dotted URL parameter", function () {
        var someObject = urlTools.mergeParamIntoObject({}, "one.two", "12");
        expect("12").toEqual(someObject.one.two);

        someObject = urlTools.mergeParamIntoObject({"one": {"xy": "34"}}, "one.two", "12");
        expect("12").toEqual(someObject.one.two);
        expect("34").toEqual(someObject.one.xy);

        someObject = urlTools.mergeParamIntoObject({}, "one.two.three", "123");
        expect("123").toEqual(someObject.one.two.three);

        someObject = urlTools.mergeParamIntoObject({}, "__proto__.polluted", "true");
        expect(undefined).toEqual({}.polluted);
        someObject = urlTools.mergeParamIntoObject({}, "constructor.prototype.polluted", "true");
        expect(undefined).toEqual({}.polluted);

        var params = urlTools.parseUrl("localhost:8989?pt.test=now&pt.go.test=single&pt.go.further=far&pt.go.further=now");
        expect("now").toEqual(params.pt.test);
        expect("single").toEqual(params.pt.go.test);
        expect(["far", "now"]).toEqual(params.pt.go.further);

        // does not work at the moment: the second parameter is skipped
        // params = urlTools.parseUrl("localhost:8989?pt.mix=now&pt.mix.test=now2");
        // expect(["now", {"test" : "now2"}]).toEqual(params.pt.mix);
    });
});
