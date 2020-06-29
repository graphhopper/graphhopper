export default class Point {
  constructor(latitude, longitude) {
    this.lat = latitude;
    this.long = longitude;
  }

  toString() {
    return this.lat + "," + this.long;
  }

  toArray() {
    return [this.lat, this.long];
  }

  static createFromArray(coord) {
    if (!coord.length || coord.length != 2) {
      throw Error("Point.createFromArray: coord must have two components");
    }

    return new Point(coord[0], coord[1]);
  }

  static createFromString(coord) {
    let split = coord.split(",");
    let map = split.map(value => {
      let number = Number.parseFloat(value);
      return Number.isNaN(number) ? 0 : number;
    });
    return Point.createFromArray(map);
  }

  static create(input) {
    if (typeof input === "string") {
      return Point.createFromString(input);
    } else if (input.length) {
      return Point.createFromArray(input);
    } else {
      throw Error("Point.create: Unknown input type");
    }
  }
}
