import {completeJson, getJsonPath} from "./complete_json"

const rootElements = [`"speed"`, `"priority"`, `"distance_influence"`, `"areas"`];
const statementElements = [`"if"`, `"else_if"`, `"else"`, `"limit_to"`, `"multiply_by"`];

describe('complete_json', () => {
    test('root elements', () => {
        test_complete(`{    }`, 3, rootElements, [3, 4]);
        test_complete(`{ `, 1, rootElements, [1, 2]);
        test_complete(`{`, 1, rootElements, [1, 2]);
        test_complete(`{"speed", `, 1, rootElements, [1, 8]);
        test_complete(`{"speed", `, 9, rootElements.filter(s => s !== `"speed"`), [9, 10]);
        test_complete(`{"speed"  `, 9, rootElements, [9, 10]);
        test_complete(`{"speed": [], "distance_influence": 100}: `, 13, rootElements.filter(s => s !== `"speed"` && s !== '"distance_influence"'), [13, 14]);
        test_complete(`{"speed": [], "distance_influence": 100}: `, 14, rootElements.filter(s => s !== `"speed"`), [14, 34]);
        test_complete(`{"spe `, 1, rootElements, [1, 6]);
        test_complete(`{"spe `, 5, rootElements, [1, 6]);
        test_complete(`{"spe `, 6, rootElements, [1, 7]);
        // ugh... tricky, we do not really want to mess up the comma, especially when we are just in the process of
        // typing "speed" -> let's see later...
        test_complete(`{"spe , "priority": []}`, 5, rootElements, [1, 9]);
    });

    test('statements', () => {
        test_complete(`{"speed": [ { "x"`, 15, statementElements, [14, 17]);
        test_complete(`{"priority": [ {}, { "x"`, 21, statementElements, [21, 24]);
        test_complete(`{"speed": [ { "a": "b", "x"`, 25, statementElements, [24, 27]);
        test_complete(`{"priority": [ {"if": "abc", "limit_to": 30}, { `, 48, statementElements, [48, 49]);
        test_complete(`{"priority": [ {"if": "abc",         `, 30, [`"limit_to"`, `"multiply_by"`], [30, 31]);
        test_complete(`{"priority": [ {"if": "abc", "xyz123"`, 30, [`"limit_to"`, `"multiply_by"`], [29, 37]);
        test_complete(`{"priority":  [ {"limit_to": 100, "xyz123"`, 35, [`"if"`, `"else_if"`, `"else"`], [34, 42]);
        // conditions
        test_complete(`{"speed": [ {"if":    }      `, 20, [`__hint__type a condition`], [18, 23]);
        test_complete(`{"speed": [ {"if": "x"`, 20, [`__hint__type a condition`], [19, 22]);
        test_complete(`{"speed":  [ {"if": "abc",   "limit_to": 30}, {"else_if": "my_condition"`, 60, [`__hint__type a condition`], [58, 72]);
        // operator values
        test_complete(`{"speed": [{"if": "abc", "limit_to":    `, 38, [`__hint__type a number`], [36, 40]);
        test_complete(`{"priority": [ {"if": "abc",   "multiply_by": "x"`, 47, [`__hint__type a number`], [46, 49]);
    });

    test(`distance_influence`, () => {
        test_complete(`{"distance_influence": "x`, 23, [`__hint__type a number`], [23, 25]);
        test_complete(`{"distance_influence": "x", "speed": []`, 23, [`__hint__type a number`], [23, 26]);
        test_complete(`{"distance_influence": 123, "speed": []`, 23, [`__hint__type a number`], [23, 26]);
    });

    test(`areas`, () => {
        test_complete(`{"areas": {  "x"`, 14, [`__hint__type an area name`], [13, 16]);
        test_complete(`{"areas": {     `, 14, [`__hint__type an area name`], [10, 16]);
        test_complete(`{"areas": {  "berlin": {},  "x"`, 29, [`__hint__type an area name`], [28, 31]);
        test_complete(`{"areas": {  "berlin": {  "x"`, 27, [`"geometry"`, `"type"`], [26, 29]);
        test_complete(`{"areas":{  "berlin": {},  "munich": {    "x"`, 42, [`"geometry"`, `"type"`], [42, 45]);
        test_complete(`{"areas": {  "berlin":  {   "type": "Feature",   `, 47, [`"geometry"`], [47, 48]);
        test_complete(`{"areas":  {  "berlin": {    "type": "x"`, 38, [`"Feature"`], [37, 40]);
        test_complete(`{\n "areas": {\n  "berlin": {\n   `, 34, [`"geometry"`, `"type"`], [34, 35]);
        test_complete(`{\n "areas": {\n  "berlin": {\n   "x"`, 34, [`"geometry"`, `"type"`], [31, 34]);
    });

    test(`areas geometry`, () => {
        test_complete(`{"areas": { "berlin": {  "geometry": {   "x"`, 42, [`"type"`, `"coordinates"`], [41, 44]);
        test_complete(`{"areas": { "berlin": {  "geometry": {      `, 42, [`"type"`, `"coordinates"`], [42, 43]);
        test_complete(`{"areas": { "berlin": {  "geometry": {   "type": "x"`, 50, [`"Polygon"`], [49, 52]);
        test_complete(`{"areas": { "berlin": {  "geometry": {   "type": "Polygon",  "x"`, 62, [`"coordinates"`], [61, 64]);
    });

});

function test_complete(content, pos, suggestions, range) {
        const result = completeJson(content, pos);
    try {
        expect(result.suggestions).toStrictEqual(suggestions);
        expect(result.range).toStrictEqual(range);
    } catch (e) {
        Error.captureStackTrace(e, test_complete);
        throw e;
    }
}

describe("get_json_path", () => {
    test('root elements', () => {
        test_path(`"x"`, 0, [`root`, `literal`], [0, 3]);
        test_path(`"x"`, 1, [`root`, `literal`], [0, 3]);
        test_path(`"x"`, 2, [`root`, `literal`], [0, 3]);
        test_path(`"x"`, 3, [`root`, `literal`], [0, 3]);
        test_path(`{  "x" `, 3, [`root`, `object`, `property`, `key`], [3, 6]);
        test_path(`{"speed": [], "x"`, 14, [`root`, `object`, `property`, `key`], [14, 17]);
        test_path(`{"speed": [], "x""distance_influence": 10}`, 15, [`root`, `object`, `property`, `key`], [14, 17]);
        test_path(`{\n"speed": [], "x"`, 17, [`root`, `object`, `property`, `key`], [15, 18]);
    });

    test('properties details', () => {
        // objects span the range from { to }
        test_path(`{ }`, 0, ['root', 'object'], [0, 3]);
        test_path(`{ }`, 1, ['root', 'object'], [0, 3]);
        test_path(`{ }`, 2, ['root', 'object'], [0, 3]);
        test_path(`{ }`, 3, ['root', 'object'], [0, 3]);
        test_path(`{ } `, 3, ['root', 'object'], [0, 3]);
        // everything inside an object is represented by 'properties' that can contain a key and value
        test_path(`{ "abc" }`, 1, ['root', 'object'], [0, 9]);
        test_path(`{ "abc" }`, 2, ['root', 'object', 'property', 'key'], [2, 7]);
        // this is a bit strange: the property starts where the 'key' starts, but goes to the end of the block
        // (but does not start at the block)
        test_path(`{ "abc"   }`, 8, ['root', 'object', 'property'], [2, 11]);
        test_path(`{   "abc"   }`, 1, ['root', 'object'], [0, 13]);
        // ... adding just the colon does not create a property
        test_path(`{   :   }`, 2, ['root', 'object'], [0, 9]);
        test_path(`{   :   }`, 6, ['root', 'object'], [0, 9]);
        // ... neither does a comma
        test_path(`{   ,   }`, 2, ['root', 'object'], [0, 9]);
        test_path(`{   ,   }`, 6, ['root', 'object'], [0, 9]);
        test_path(`{ "abc"   :   }`, 3, ['root', 'object', 'property', 'key'], [2, 7]);
        test_path(`{ "abc"   :   }`, 8, ['root', 'object', 'property'], [2, 15]);
        // everything right of the colon we consider to be the properties' 'value'
        test_path(`{ "abc"   :   }`, 12, ['root', 'object', 'property[abc]', 'value'], [11, 15]);
        test_path(`{ "abc" : "def"  ,   }`, 13, ['root', 'object', 'property[abc]', 'value'], [10, 15]);
        test_path(`{ "abc" : "def"  ,   }`, 16, ['root', 'object'], [0, 22]);
        test_path(`{ "abc" : "def"  ,   }`, 18, ['root', 'object'], [0, 22]);
        test_path(`{ "abc" : "def"  , "ghi"  }`, 22, ['root', 'object', 'property', 'key'], [19, 24]);
    });

    test('statements', () => {
        test_path(`{"speed": [ "x"`, 12, [`root`, `object`, `property[speed]`, `array[0]`, `literal`], [12, 15]);
        test_path(`{"speed": [ { "x"`, 15, [`root`, `object`, `property[speed]`, `array[0]`, `object`, `property`, `key`], [14, 17]);
        test_path(`{"priority": [ {}, { "x"`, 22, [`root`, `object`, `property[priority]`, `array[1]`, `object`, `property`, `key`], [21, 24]);
        test_path(`{"speed": [ { "a": "b", "x"`, 25, [`root`, `object`, `property[speed]`, `array[0]`, `object`, `property`, `key`], [24, 27]);
        test_path(`{"priority": [ {"if": "abc",  "limit_to": 30}, "x"`, 48, [`root`, `object`, `property[priority]`, `array[1]`, `literal`], [47, 50]);
        test_path(`{"priority":[ {"if": "abc", "xyz"`, 30, [`root`, `object`, `property[priority]`, `array[0]`, `object`, `property`, `key`], [28, 33]);
        // conditions
        test_path(`{"speed": [ {"if": "x"`, 20, [`root`, `object`, `property[speed]`, `array[0]`, `object`, `property[if]`, `value`], [19, 22]);
        test_path(`{"speed": [{"if": "abc", "limit_to": 30}, {"else_if": "my_condition"`, 65, [`root`, `object`, `property[speed]`, `array[1]`, `object`, `property[else_if]`, `value`], [54, 68]);
        // operator values
        test_path(`{"speed": [{"if": "abc", "limit_to": "x"`, 37, [`root`, `object`, `property[speed]`, `array[0]`, `object`, `property[limit_to]`, `value`], [37, 40]);
        test_path(`{"priority": [{ "if": "abc", "multiply_by": "x"`, 46, [`root`, `object`, `property[priority]`, `array[0]`, `object`, `property[multiply_by]`, `value`], [44, 47]);
    });

    test(`distance_influence`, () => {
        test_path(`{"distance_influence": "x"`, 24, [`root`, `object`, `property[distance_influence]`, `value`], [23, 26]);
    });

    test(`areas`, () => {
        test_path(`{"areas":  "x"`, 12, [`root`, `object`, `property[areas]`, `value`], [11, 14]);
        test_path(`{"areas": { "berlin":    "x"`, 25, [`root`, `object`, `property[areas]`, `object`, `property[berlin]`, `value`], [25, 28]);
        test_path(`{"areas":  {"berlin": {   "type": "Feature"    "x"`, 48, [`root`, `object`, `property[areas]`, `object`, `property[berlin]`, `object`, `property`, `key`], [47, 50]);
        test_path(`{"areas":{  "berlin": {   "type": "Feature"    "geometry": []},  "x"`, 65, [`root`, `object`, `property[areas]`, `object`, `property`, `key`], [65, 68]);
        test_path(`{\n "areas": {\n  "berlin": {\n   `, 31, [`root`, `object`, `property[areas]`, `object`, `property[berlin]`, `object`], [26, 31]);
    });
});

function test_path(content, pos, expectedSignature, expectedTokenRange) {
        const jsonPath = getJsonPath(content, pos);
    try {
        expect(jsonPath.signature).toStrictEqual(expectedSignature);
        expect(jsonPath.tokenRange).toStrictEqual(expectedTokenRange);
    } catch (e) {
        Error.captureStackTrace(e, test_path);
        throw e;
    }
}