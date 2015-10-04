/*
 * This software stands under the Apache 2 License
 */
describe("utils", function () {
    it("should format time string correctly", function () {
        defaultTranslationMap = {};
        defaultTranslationMap["minabbr"] = 'min';
        defaultTranslationMap["hourabbr"] = 'h';
        defaultTranslationMap["dayabbr"] = 'd';
        expect(createTimeString(10773331)).toBe("2h 59min");

        expect(createTimeString(10773331 * 24)).toBe("2d 23h");

        expect(createTimeString(260493166)).toBe("3d");
        expect(createTimeString(3642407)).toBe("1h");
        expect(createTimeString(12000)).toBe("0min");
    });

    it("should format translation string correctly", function () {
        // toBe, toBeTruthy, toBeFalsy
        defaultTranslationMap = {};
        defaultTranslationMap["web.somekey1"] = "%s wow %s";
        expect(tr("someKey1", ["nice", "to"])).toBe("nice wow to");

        defaultTranslationMap["web.somekey2"] = "%2$s wow %1$s";
        expect(tr("someKey2", ["nice", "to"])).toBe("to wow nice");

        defaultTranslationMap["web.key"] = "it will take %1$s";
        expect(tr("key", "2min")).toBe("it will take 2min");

        defaultTranslationMap["web.key"] = "%1$s%2$s werden %3$s brauchen";
        expect(tr("key", [200, "km", "2min"])).toBe("200km werden 2min brauchen");
    });

    it("should format location entry correctly", function () {
        var res = formatLocationEntry({
            "state": "Berlin",
            "country": "Deutschland",
            "country_code": "de",
            "continent": "Europäischen Union"
        });
        expect(res).toEqual({postcode: undefined, country: "Deutschland", more: "Berlin, Europäischen Union"});

        res = formatLocationEntry({
            "suburb": "Neukölln",
            "city_district": "Neukölln",
            "hamlet": "Rixdorf",
            "state": "Berlin",
            "country": "Deutschland",
            "country_code": "de",
            "continent": "Europäischen Union"
        });
        expect(res).toEqual({postcode: undefined, city: "Rixdorf, Neukölln", country: "Deutschland", more: "Berlin, Europäischen Union"});
    });

    it("should decode the polyline", function () {
        var list = decodePath("_p~iF~ps|U", false);
        expect(list).toEqual([[-120.2, 38.5]]);

        list = decodePath("_p~iF~ps|U_ulLnnqC_mqNvxq`@", false);
        expect(list).toEqual([[-120.2, 38.5], [-120.95, 40.7], [-126.45300000000002, 43.252]]);
    });

    it("should decode the 3D polyline", function () {
        var list = decodePath("_p~iF~ps|Uo}@", true);
        expect(list).toEqual([[-120.2, 38.5, 10]]);

        list = decodePath("_p~iF~ps|Uo}@_ulLnnqC_anF_mqNvxq`@?", true);
        expect(list).toEqual([[-120.2, 38.5, 10], [-120.95, 40.7, 1234], [-126.45300000000002, 43.252, 1234]]);
    });

    it("should parse URL correctly", function () {
        var params = parseUrl("localhost:8989?test=pest&test2=true&test3=false&test4=2&test5=1.1&test5=2.7");
        expect("pest").toEqual(params.test);
        expect(true).toEqual(params.test2);
        expect(false).toEqual(params.test3);
        expect("2").toEqual(params.test4);
        expect(["1.1", "2.7"]).toEqual(params.test5);

        // force array for point
        // URLs with one point only should work: https://graphhopper.com/maps/?point=50.413331%2C11.699066
        params = parseUrl("blup?point=49.946505%2C11.571232&point=&");
        expect(params.point).toEqual(["49.946505,11.571232", ""]);

        params = parseUrl("blup?point=&point=49.946505%2C11.571232");
        expect(params.point).toEqual(["", "49.946505,11.571232"]);
    });

    it("ghrequest should init correctly from params", function () {
        var ghRequest = new GHRequest("http://test.de");
        var params = {};
        params.do_zoom = true;
        ghRequest.init(params);
        expect(ghRequest.do_zoom).toEqual(params.do_zoom);

        params.do_zoom = false;
        ghRequest.init(params);
        expect(ghRequest.do_zoom).toEqual(params.do_zoom);
    });

    it("input should accept 0 and no addresses", function () {
        var input = new GHInput("12,0");
        expect(input.toString()).toEqual("12,0");
        var input = new GHInput("bluo,0");
        expect(input.toString()).toEqual(undefined);
        expect(input.lat).toEqual(undefined);
        expect(input.lng).toEqual(undefined);
        var input = new GHInput("bluo");
        expect(input.toString()).toEqual(undefined);
        var input = new GHInput("");
        expect(input.toString()).toEqual(undefined);
    });

    it("GHInput should set to unresolved if new input string", function () {
        var input = new GHInput("12.44, 68.44");
        expect(input.isResolved()).toEqual(true);
        input.set("blup");
        expect(input.isResolved()).toEqual(false);
    });

    it("point should be parsable", function () {
        expect(new GHInput("12.44, 68.44").lat).toEqual(12.44);
        expect(new GHInput("12.44, 68.44").lng).toEqual(68.44);
        expect(new GHInput("12.44,68.44").lat).toEqual(12.44);
        expect(new GHInput("12.44,68.44").lng).toEqual(68.44);
        expect(new GHInput("london").lon).toEqual(undefined);
    });

    it("should sort vehicles and prefer car, foot, bike", function () {
        // car, foot and bike should come first. mc comes last
        var prefer = {"car": 1, "foot": 2, "bike": 3, "motorcycle": 10000};
        var keys = getSortedVehicleKeys({"motorcycle": "blup", "car": "blup", "mtb": "blup", "foot": "blup"}, prefer);
        expect(keys).toEqual(["car", "foot", "mtb", "motorcycle"]);
    });

    it("features should work", function () {
        var ghRequest = new GHRequest("http://test.de?vehicle=car");
        var params = {};
        params.elevation = true;
        ghRequest.features = {"car": {}};
        ghRequest.init(params);
        expect(ghRequest.api_params.elevation).toEqual(false);

        // overwrite
        ghRequest.features = {"car": {elevation: true}};
        ghRequest.init(params);
        expect(ghRequest.api_params.elevation).toEqual(true);

        var params = {};
        ghRequest.features = {"car": {elevation: true}};
        ghRequest.init(params);
        expect(ghRequest.api_params.elevation).toEqual(true);

        var params = {};
        params.elevation = false;
        ghRequest.features = {"car": {elevation: true}};
        ghRequest.init(params);
        expect(ghRequest.api_params.elevation).toEqual(false);

        var params = {};
        params.elevation = true;
        ghRequest.features = {"car": {elevation: false}};
        ghRequest.init(params);
        expect(ghRequest.api_params.elevation).toEqual(false);

        ghRequest = new GHRequest("http://test.de");
        var params = {point: [[4, 3], [2, 3]], test: "x", test_array: [1, 2]};
        ghRequest.init(params);

        // skip point, layer etc
        expect(ghRequest.api_params.point).toEqual(undefined);

        // include all other parameters
        expect(ghRequest.api_params.test).toEqual("x");
        expect(ghRequest.api_params.test_array).toEqual([1, 2]);
    });
});