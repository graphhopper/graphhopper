/*
 * This software stands under the Apache 2 License
 */
describe("utils", function() {    

    it("should format string correctly", function() {
        // toBe, toBeTruthy, toBeFalsy
        defaultTranslationMap = {};
        defaultTranslationMap["someKey1"] = "%s wow %s";
        expect(tr("someKey1", ["nice", "to"])).toBe("nice wow to");
        
        defaultTranslationMap["someKey2"] = "%2$s wow %1$s";
        expect(tr("someKey2", ["nice", "to"])).toBe("to wow nice");
        
        defaultTranslationMap["key"] = "it will take %1$s";
        expect(tr("key", "2min")).toBe("it will take 2min");
    });
});