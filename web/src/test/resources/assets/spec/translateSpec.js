var translate = requireFile('./translate.js');

describe('translation', function () {
    it("should format distance string correctly", function () {
        var defaultTranslationMap = {};
        defaultTranslationMap["m_abbr"] = 'm';
        defaultTranslationMap["km_abbr"] = 'km';
        defaultTranslationMap["ft_abbr"] = 'ft';
        defaultTranslationMap["mi_abbr"] = 'mi';

        translate.init({default: defaultTranslationMap});

        expect(translate.createDistanceString(877.34)).toBe("877m");
        expect(translate.createDistanceString(1877.34)).toBe("1.88km");
        expect(translate.createDistanceString(100877.34)).toBe("101km");
        expect(translate.createDistanceString(100877.34, false)).toBe("101km");

        expect(translate.createDistanceString(151.96, true)).toBe("499ft")
        expect(translate.createDistanceString(152.31, true)).toBe("0.09mi")
        expect(translate.createDistanceString(4988.97, true)).toBe("3.1mi")
        expect(translate.createDistanceString(4998.97, true)).toBe("3.11mi")
        expect(translate.createDistanceString(162543.74, true)).toBe("101mi")
    });

    it("should format elevation string correctly", function () {
        var defaultTranslationMap = {};
        defaultTranslationMap["m_abbr"] = 'm';
        defaultTranslationMap["ft_abbr"] = 'ft';

        translate.init({default: defaultTranslationMap});

        expect(translate.createEleInfoString(156.3, 324.6, true)).toBe("<br/> &#8599;513ft &#8600;1065ft");
        expect(translate.createEleInfoString(156.3, 324.6, false)).toBe("<br/> &#8599;156m &#8600;325m");
        expect(translate.createEleInfoString(156.3, 324.6)).toBe("<br/> &#8599;156m &#8600;325m");
    });

    it("should format time string correctly", function () {
        var defaultTranslationMap = {};
        defaultTranslationMap["min_abbr"] = 'min';
        defaultTranslationMap["hour_abbr"] = 'h';
        defaultTranslationMap["day_abbr"] = 'd';

        translate.init({default: defaultTranslationMap});

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
        translate.init({default: defaultTranslationMap});
        expect(translate.tr("somekey1", ["nice", "to"])).toBe("nice wow to");

        defaultTranslationMap["web.somekey2"] = "%2$s wow %1$s";
        translate.init({default: defaultTranslationMap});
        expect(translate.tr("somekey2", ["nice", "to"])).toBe("to wow nice");

        defaultTranslationMap["web.key"] = "it will take %1$s";
        translate.init({default: defaultTranslationMap});
        expect(translate.tr("key", "2min")).toBe("it will take 2min");

        defaultTranslationMap["web.key"] = "%1$s%2$s werden %3$s brauchen";
        translate.init({default: defaultTranslationMap});
        expect(translate.tr("key", [200, "km", "2min"])).toBe("200km werden 2min brauchen");
    });
});
