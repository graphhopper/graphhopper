var formatTools = requireFile('./tools/format.js');

describe('formatTools', function () {
    it("should format location entry correctly", function () {
        var res = formatTools.formatLocationEntry({
            "state": "Berlin",
            "country": "Deutschland",
            "country_code": "de",
            "continent": "Europäischen Union"
        });
        expect(res).toEqual({country: "Deutschland", more: "Berlin, Europäischen Union"});

        res = formatTools.formatLocationEntry({
            "suburb": "Neukölln",
            "city_district": "Neukölln",
            "hamlet": "Rixdorf",
            "state": "Berlin",
            "country": "Deutschland",
            "country_code": "de",
            "continent": "Europäischen Union"
        });
        expect(res).toEqual({city: "Rixdorf, Neukölln", country: "Deutschland", more: "Berlin, Europäischen Union"});
    });
});
