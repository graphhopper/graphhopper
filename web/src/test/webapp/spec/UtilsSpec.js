/*
 * This software stands under the Apache 2 License
 */
describe("utils", function() {
    it("should format time string correctly", function() {
        defaultTranslationMap = {};
        defaultTranslationMap["minabbr"]='min';
        defaultTranslationMap["hourabbr"]='h';
        defaultTranslationMap["dayabbr"]='d';
        expect(createTimeString(10773331)).toBe("2h 59min");
        
        expect(createTimeString(10773331 * 24)).toBe("2d 23h");
        
        expect(createTimeString(260493166)).toBe("3d");
        expect(createTimeString(3642407)).toBe("1h");
        expect(createTimeString(12000)).toBe("0min");
    });
    
    it("should format translation string correctly", function() {
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

    it("should format location entry correctly", function() {
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
});