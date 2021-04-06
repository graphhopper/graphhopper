import {parseTree, printParseErrorCode} from "jsonc-parser";

const rootKeys = ['speed', 'priority', 'distance_influence', 'areas'];
const clauses = ['if', 'else_if', 'else'];
const operators = ['multiply_by', 'limit_to'];
const statementKeys = clauses.concat(operators);

let _conditionRanges = [];
let _areas = [];

/**
 * Checks that a given json string follows this schema:
 *
 * speed: array<{clause: string, operator: value}>, optional, not null
 * priority: array<{clause: string, operator: value}>, optional, not null
 * distance_influence: number, optional, not null
 * areas: object, optional, not null
 *
 * the speed/priority array objects must contain a cause that can be either 'if', 'else_if' or 'else' and
 * an operator that can be 'multiply_by' or 'limit_to'
 *
 * the clause value must be a string and the operator value must be a number
 * except when the the clause is 'else' in which case the value must be null
 *
 * 'else_if' and 'else' clauses must be preceded by an 'if' or 'else_if' clause
 *
 * This method returns an object containing:
 *
 * - errors: a list of error objects that contain a message, a (json) path (as string) and
 *           the character range associated with the error as array [startInclusive, endExclusive]
 * - jsonErrors: a list of errors returned by the json parser. using the same format except that the path is set
 *               to 'syntax'
 * - conditionRanges: a list of character ranges in above format that indicates the positions of
 *                    the 'conditions', i.e. the values of 'if' and 'else_if' clauses
 * - areas: the list of area names used in the document
 */
export function validateJson(json) {
    _conditionRanges = [];
    _areas = [];

    if (json.trim().length === 0)
        return {
            errors: [error('root', 'must be an object', [0, json.length])],
            jsonErrors: [],
            conditionRanges: _conditionRanges,
            areas: _areas
        }

    // we keep errors found by the json separate from the ones we find when we validate against our 'schema'
    const parserErrors = [];
    const parseRes = parseTree(json, parserErrors, {
        allowEmptyContent: false,
        allowTrailingComma: false,
        disallowComments: true
    });

    const jsonErrors = parserErrors.map(e => {
        const message = printParseErrorCode(e.error);
        return error('syntax', message, [e.offset, e.offset + Math.max(1, e.length)]);
    });

    const errors = parseRes
        ? validateJsonDoc(parseRes)
        : [];

    return {
        errors,
        jsonErrors,
        conditionRanges: _conditionRanges,
        areas: _areas
    }
}

function validateJsonDoc(doc) {
    return validateRoot(doc);
}

function validateRoot(root) {
    const message = `possible keys: ${displayList(rootKeys)}`;
    const keyIsValid = (key) => rootKeys.indexOf(key.value) >= 0;
    return validateObject('root', root, keyIsValid, (path, keys, range) => [], message, validateRootKeyValuePair);
}

function validateRootKeyValuePair(path, key, value) {
    if (isJsonNull(value)) {
        return [error(`${key.value}`, `must not be null`, getRange(value))];
    } else if (key.value === 'speed' || key.value === 'priority') {
        return validateStatements(key.value, value);
    } else if (key.value === 'distance_influence') {
        return validateDistanceInfluence(value);
    } else if (key.value === 'areas') {
        return validateAreas(value);
    } else {
        throw `Unexpected root key ${key.value}`;
    }
}

function validateStatements(key, itemsObj) {
    const errors = validateList(key, itemsObj, 0, -1, validateStatement);
    if (errors.length > 0)
        return errors;

    // single statements seem to be ok, but are they written in the right order?
    const items = itemsObj.children;
    const clausesList = [];
    for (let i = 0; i < items.length; ++i) {
        for (let j = 0; j < items[i].children.length; ++j) {
            const key = items[i].children[j].children[0].value;
            if (clauses.indexOf(key) >= 0)
                clausesList.push(key);
        }
    }
    let prev = '';
    for (let i = 0; i < clausesList.length; ++i) {
        if ((clausesList[i] === 'else_if' || clausesList[i] === 'else') && (prev !== 'else_if' && prev !== 'if'))
            errors.push(error(`${key}[${i}]`, `'${clausesList[i]}' clause must be preceded by 'if' or 'else_if'`, getRange(items[i])));
        prev = clausesList[i];
    }
    return errors;
}

function validateStatement(path, statementItem, statementIndex) {
    const message = `possible keys: ${displayList(statementKeys)}`;
    const keyIsValid = (key) => statementKeys.indexOf(key.value) >= 0;
    return validateObject(path, statementItem,
        keyIsValid, validateStatementKeyConstraints, message, validateStatementValue);
}

function validateStatementKeyConstraints(path, keys, range) {
    const errors = [];
    if (keys.length > 2)
        errors.push(error(path, `too many keys. maximum: 2. given: ${keys.sort()}`, range));
    const hasClause = keys.some(key => clauses.indexOf(key) >= 0);
    const hasOperator = keys.some(key => operators.indexOf(key) >= 0);
    if (!hasClause)
        errors.push(error(path, `every statement must have a clause ${displayList(clauses)}. given: ${keys}`, range));
    if (!hasOperator)
        errors.push(error(path, `every statement must have an operator ${displayList(operators)}. given: ${keys}`, range));
    return errors;
}

function validateStatementValue(path, key, value) {
    const errors = [];
    const isClause = clauses.indexOf(key.value) >= 0;
    const isOperator = operators.indexOf(key.value) >= 0;
    if (isClause) {
        if (key.value === 'else') {
            if (!(isJsonNull(value) || (isJsonString(value) && value.value === ''))) {
                // todo: now we are using json can we just allow "" and reject null?
                errors.push(error(`${path}[else]`, `must be null or empty. given: '${value.value}'`, getRange(value)));
            }
        } else {
            if (!isJsonString(value) && !isJsonBoolean(value)) {
                errors.push(error(`${path}[${key.value}]`, `must be a string or boolean. given type: ${displayType(value)}`, getRange(value)));
            } else {
                _conditionRanges.push(getRange(value));
            }
        }
    }
    if (isOperator) {
        if (!isJsonNumber(value)) {
            errors.push(error(`${path}[${key.value}]`, `must be a number. given type: ${displayType(value)}`, getRange(value)));
        }
    }
    if (isClause === isOperator) {
        throw `Unexpected statement: ${isClause} ${isOperator} ${key} ${value}`;
    }
    return errors;
}

function validateDistanceInfluence(value) {
    if (!isJsonNumber(value))
        return [error(`distance_influence`, `must be a number. given type: ${displayType(value)}`, getRange(value))];
    else
        return [];
}

function validateAreas(areas) {
    const validateKeys = (path, keys, range) => {
        _areas = keys;
        return [];
    }
    return validateObject(`areas`, areas,
        isValidAreaName, validateKeys, `names may only contain a-z, digits and _`, validateArea);
}

function validateArea(path, areaName, area) {
    const areaKeys = ['type', 'geometry', 'id', 'properties'];
    const message = `possible keys: ${displayList(areaKeys)}`;
    const keyIsValid = (key) => areaKeys.indexOf(key.value) >= 0;
    return validateObject(`${path}[${areaName.value}]`, area,
        keyIsValid, validateRequiredAreaKeys, message, validateAreaField);
}

function validateRequiredAreaKeys(path, keys, range) {
    const requiredAreaKeys = ['type', 'geometry'];
    const errors = [];
    for (let i = 0; i < requiredAreaKeys.length; i++) {
        if (keys.indexOf(requiredAreaKeys[i]) < 0)
            errors.push(error(path, `missing '${requiredAreaKeys[i]}'. given: ${displayList(keys)}`, range));
    }
    return errors;
}

function validateAreaField(path, key, value) {
    if (key.value === 'type') {
        if (!isJsonString(value)) {
            return [error(`${path}[${key.value}]`, `must be "Feature". given type: ${displayType(value)}`, getRange(value))];
        } else if (value.value !== 'Feature')
            return [error(`${path}[${key.value}]`, `must be "Feature". given: "${value.value}"`, getRange(value))];
        else
            return [];
    } else if (key.value === 'properties') {
        return validateObject(`${path}[${key.value}]`, value, () => true, () => [], '', () => []);
    } else if (key.value === 'id') {
        if (!isJsonString(value)) {
            return [error(`${path}[${key.value}]`, `must be a string. given type: ${displayType(value)}`, getRange(value))];
        }
    } else if (key.value === 'geometry') {
        const geometryKeys = ['type', 'coordinates'];
        const message = `possible keys: ${displayList(geometryKeys)}`;
        const keyIsValid = (key) => geometryKeys.indexOf(key.value) >= 0;
        return validateObject(`${path}[${key.value}]`, value,
            keyIsValid, validateRequiredGeometryKeys, message, validateGeometryField);
    } else {
        console.error(`unexpected area field ${key.value}`);
        return [];
    }
}

function validateRequiredGeometryKeys(path, keys, range) {
    const requiredKeys = ['type', 'coordinates'];
    const errors = [];
    for (let i = 0; i < requiredKeys.length; i++) {
        if (keys.indexOf(requiredKeys[i]) < 0)
            errors.push(error(path, `missing '${requiredKeys[i]}'. given: ${displayList(keys)}`, range));
    }
    return errors;
}

function validateGeometryField(path, key, value) {
    if (key.value === 'type') {
        if (value.value !== 'Polygon')
            return [error(`${path}[${key.value}]`, `must be "Polygon". given: '${value.value}'`, getRange(value))];
        else
            return [];
    } else if (key.value === 'coordinates') {
        return validateCoordinates(`${path}[${key.value}]`, key, value);
    } else {
        throw `unexpected geometry field ${key.value}`;
    }
}

function validateCoordinates(path, key, value) {
    return validateList(path, value, 1, -1, validateLinearRing);
}

function validateList(path, node, minLength, maxLength, validateListItem) {
    if (!isJsonArray(node))
        return [error(path, `must be an array. given type: ${displayType(node)}`, getRange(node))];

    const items = node.children;
    if (items.length < minLength)
        return [error(path, `minimum length: ${minLength}, given: ${items.length}`, getRange(node))];
    if (maxLength >= 0 && items.length > maxLength)
        return [error(path, `maximum length: ${maxLength}, given: ${items.length}`, getRange(node))];

    const errors = [];
    for (let i = 0; i < items.length; ++i) {
        const item = items[i];
        errors.push.apply(errors, validateListItem(`${path}[${i}]`, item, i));
    }
    return errors;
}

function validateLinearRing(path, ring, index) {
    const errors = validateList(path, ring, 4, -1, validatePoint);
    if (errors.length > 0)
        return errors;
    const firstPoint = ring.children[0];
    const lastPoint = ring.children[ring.children.length - 1];
    if (!pointsEqual(firstPoint, lastPoint))
        return [error(`${path}`, `the last point must be equal to the first`, getRange(lastPoint))];
    else
        return [];
}

function validatePoint(path, point, index) {
    return validateList(path, point, 2, 2, validateCoordinate);
}

function validateCoordinate(path, coordinate, index) {
    const errors = [];
    if (!isJsonNumber(coordinate)) {
        errors.push(error(path, `must be a number`, getRange(coordinate)));
    } else {
        if (index === 0 && (coordinate.value < -180 || coordinate.value > 180))
            errors.push(error(`${path}`, `longitude must be in [-180, +180]`, getRange(coordinate)));
        if (index === 1 && (coordinate.value < -90 || coordinate.value > 90))
            errors.push(error(`${path}`, `latitude must be in [-90, +90]`, getRange(coordinate)));
    }
    return errors;
}

function pointsEqual(p, q) {
    return p.children[0].value === q.children[0].value && p.children[1].value === q.children[1].value;
}

function isValidAreaName(areaName) {
    const regex = /^[a-z][0-9A-Za-z_]*$/g
    return regex.test(areaName.value);
}

function validateObject(path, obj, keyIsValid, validateKeys, message, validateKeyValuePair) {
    if (!isJsonObject(obj))
        return [error(path, `must be an object. given type: ${displayType(obj)}`, getRange(obj))];

    const keyValidation = validateObjectKeys(path, keyIsValid, message, obj);
    if (keyValidation.errors.length > 0) return keyValidation.errors;

    const errors = [];
    errors.push.apply(errors, validateKeys(path, keyValidation.keys, getRange(obj)));
    if (errors.length > 0) return errors;

    errors.push.apply(errors, validateObjectProperties(path, obj.children, validateKeyValuePair));
    return errors;
}

function validateObjectKeys(objectKey, keyIsValid, message, obj) {
    const errors = [];
    const keys = new Set();
    const children = obj.children;
    for (let i = 0; i < children.length; ++i) {
        if (!isJsonProperty(children[i]))
            throw `Expected a key-value pair, but got: ${children[i]}`;
        const key = children[i].children[0];
        if (key.value.length === 0 || key.value.trim().length === 0) {
            errors.push(error(objectKey, `keys must be non-empty and must not only consist of whitespace. given: '${key.value}'`, getRange(key)));
        } else if (!keyIsValid(key)) {
            errors.push(error(objectKey, `${message}. given: '${key.value}'`, getRange(key)));
        } else if (keys.has(key.value)) {
            errors.push(error(objectKey, `keys must be unique. duplicate: '${key.value}'`, getRange(key)))
        } else {
            keys.add(key.value);
        }
    }
    return {
        errors,
        keys: Array.from(keys)
    };
}

function validateObjectProperties(path, properties, validateKeyValuePair) {
    const errors = [];
    for (let i = 0; i < properties.length; ++i) {
        if (!isJsonProperty(properties[i]))
            throw `Expected a key-value pair, but got: ${properties[i]}`;
        const key = properties[i].children[0];
        const value = properties[i].children[1];
        if (!value) {
            // we do not want to start root paths with 'root' and at the same time we want the
            // missing value check here so we do not have to repeat it in every validateKeyValuePair
            // function
            const pathWithoutRoot = path === 'root'
                ? key.value
                : `${path}[${key.value}]`
            errors.push(error(pathWithoutRoot, `missing value`, getRange(key)));
        } else
            errors.push.apply(errors, validateKeyValuePair(path, key, value));
    }
    return errors;
}

function error(path, message, range) {
    return {path, message, range};
}

function isJsonObject(node) {
    return node.type === 'object';
}

function isJsonArray(node) {
    return node.type === 'array';
}

function isJsonProperty(node) {
    return node.type === 'property';
}

function isJsonNull(node) {
    return node.type === 'null';
}

function isJsonNumber(node) {
    return node.type === 'number';
}

function isJsonString(node) {
    return node.type === 'string';
}

function isJsonBoolean(node) {
    return node.type === 'boolean';
}

function getRange(node) {
    return [node.offset, node.offset + node.length];
}

function displayType(node) {
    return node.type;
}

function displayList(list) {
    const str = list.map(l => `'${l}'`).join(', ');
    return `[${str}]`;
}