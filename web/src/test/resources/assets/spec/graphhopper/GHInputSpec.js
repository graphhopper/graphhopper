var GHInput = requireFile('./graphhopper/GHInput.js');

describe("GHInput", function () {

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
});
