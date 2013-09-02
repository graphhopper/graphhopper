/*
 * This software stands under the Apache 2 License
 */
describe("utils", function() {    

    it("should format string correctly", function() {
        // toBe, toBeTruthy, toBeFalsy
        defaultTranslationMap = {};
        defaultTranslationMap["web.someKey1"] = "%s wow %s";
        expect(tr("someKey1", ["nice", "to"])).toBe("nice wow to");
        
        defaultTranslationMap["web.someKey2"] = "%2$s wow %1$s";
        expect(tr("someKey2", ["nice", "to"])).toBe("to wow nice");
        
        defaultTranslationMap["web.key"] = "it will take %1$s";
        expect(tr("key", "2min")).toBe("it will take 2min");
        
        defaultTranslationMap["web.key"] = "%1$s%2$s werden %3$s brauchen";
        expect(tr("key", [200, "km", "2min"])).toBe("200km werden 2min brauchen");        
    });
});