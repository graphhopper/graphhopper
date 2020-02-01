var vehicle = requireFile('./tools/vehicle.js');

describe('vehicle', function () {
    it("should sort vehicles and prefer car, foot, bike", function () {
        // car, foot and bike should come first. mc comes last
        var prefer = {"car": 1, "foot": 2, "bike": 3, "motorcycle": 10000};
        var keys = vehicle.getSortedVehicleKeys({"motorcycle": "blup", "car": "blup", "mtb": "blup", "foot": "blup"}, prefer);
        expect(keys).toEqual(["car", "foot", "mtb", "motorcycle"]);
    });
});
