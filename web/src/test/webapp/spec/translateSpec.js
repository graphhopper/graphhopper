var translate = requireFile('./translate.js');

describe('translation', function () {
    it("should format time string correctly", function () {
        var defaultTranslationMap = {};
        defaultTranslationMap["minabbr"] = 'min';
        defaultTranslationMap["hourabbr"] = 'h';
        defaultTranslationMap["dayabbr"] = 'd';

        translate.init({ default: defaultTranslationMap });

        expect(translate.createTimeString(10773331)).toBe("2h 59min");

        expect(translate.createTimeString(10773331 * 24)).toBe("2d 23h");

        expect(translate.createTimeString(260493166)).toBe("3d");
        expect(translate.createTimeString(3642407)).toBe("1h");
        expect(translate.createTimeString(12000)).toBe("0min");
    });

    it("should format translation string correctly", function () {
        // toBe, toBeTruthy, toBeFalsy
        var defaultTranslationMap = {};
        defaultTranslationMap["web.somekey1"] = "%s wow %s";
        translate.init({ default: defaultTranslationMap });
        expect(translate.tr("someKey1", ["nice", "to"])).toBe("nice wow to");

        defaultTranslationMap["web.somekey2"] = "%2$s wow %1$s";
        translate.init({ default: defaultTranslationMap });
        expect(translate.tr("someKey2", ["nice", "to"])).toBe("to wow nice");

        defaultTranslationMap["web.key"] = "it will take %1$s";
        translate.init({ default: defaultTranslationMap });
        expect(translate.tr("key", "2min")).toBe("it will take 2min");

        defaultTranslationMap["web.key"] = "%1$s%2$s werden %3$s brauchen";
        translate.init({ default: defaultTranslationMap });
        expect(translate.tr("key", [200, "km", "2min"])).toBe("200km werden 2min brauchen");
    });
});
