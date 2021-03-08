import YAML from 'yaml';

const rootKeys = ['speed', 'priority', 'distance_influence', 'areas'];
const clauses = ['if', 'else_if', 'else'];
const operators = ['multiply_by', 'limit_to'];
const statementKeys = ['if', 'else_if', 'else', 'multiply_by', 'limit_to'];

let _conditionRanges = [];
let _areas = [];

/**
 * Checks that a given yaml string follows this schema:
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
 * - errors: a list of error objects that contain a message, a (yaml) path (as string) and
 *           the character range associated with the error as array [startInclusive, endExclusive]
 * - yamlErrors: a list of errors returned by the yaml parser. using the same format except that the path is set
 *               to 'syntax'
 * - conditionRanges: a list of character ranges in above format that indicates the positions of
 *                    the 'conditions', i.e. the values of 'if' and 'else_if' clauses
 * - areas: the list of area names used in the document
 */
export function validate(yaml) {
    _conditionRanges = [];
    _areas = [];
    const doc = YAML.parseDocument(yaml, {
        // with this option we can access the lower-level 'concrete syntax tree (cst)'. This helps us to obtain
        // the character ranges in a few places but unfortunately there are also some cases where it does
        // not help either, e.g. for null map values.
        keepCstNodes: true,
        prettyErrors: false
    });
    // the yaml parser returns a list of errors, but we keep them separate from the errors we find when we compare
    // against our 'schema'
    const yamlErrors = doc.errors.map(e => {
        if (e.range) {
            return error('syntax', e.message, [e.range.start, e.range.end]);
        } else if (e.source && e.source.range) {
            return error('syntax', e.message, [e.source.range.start, e.source.range.end]);
        } else {
            // last resort so to say, these errors should be rare because we do not provide very useful information
            // to the user
            return error('syntax', 'error', [0, yaml.length]);
        }
    });
    const errors = validateYamlDoc(doc);
    return {
        errors,
        yamlErrors,
        conditionRanges: _conditionRanges,
        areas: _areas
    }
}

function validateYamlDoc(doc) {
    // empty docs are ok
    if (!doc.contents) return [];
    return validateRoot(doc.contents);
}

function validateRoot(root) {
    const message = `possible keys: ${displayList(rootKeys)}`;
    const keyIsValid = (key) => rootKeys.indexOf(key.value) >= 0;
    return validateObject('root', root, keyIsValid, (path, keys, range) => [], message, validateRootKeyValuePair);
}

function validateRootKeyValuePair(path, key, value) {
    if (value === null || value.value === null) {
        return [error(`${key.value}`, `must not be null`, key.range)];
    } else if (key.value === 'speed' || key.value === 'priority') {
        return validateStatements(key.value, value);
    } else if (key.value === 'distance_influence') {
        return validateDistanceInfluence(value);
    } else if (key.value === 'areas') {
        return validateAreas(value);
    } else {
        console.error(`Unexpected root key ${key.value}`);
        return [];
    }
}

function validateStatements(key, itemsObj) {
    const errors = validateList(key, itemsObj, 0, -1, validateStatement);
    if (errors.length > 0)
        return errors;

    // single statements seem to be ok, but are they written in the right order?
    const items = itemsObj.items;
    const clausesList = [];
    for (let i = 0; i < items.length; ++i) {
        for (let j = 0; j < items[i].items.length; ++j) {
            const key = items[i].items[j].key.value;
            if (clauses.indexOf(key) >= 0)
                clausesList.push(key);
        }
    }
    let prev = '';
    for (let i = 0; i < clausesList.length; ++i) {
        if ((clausesList[i] === 'else_if' || clausesList[i] === 'else') && (prev !== 'else_if' && prev !== 'if'))
            errors.push(error(`${key}[${i}]`, `'${clausesList[i]}' clause must be preceded by 'if' or 'else_if'`, items[i].range));
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
            if (value !== null && value.value !== null) {
                errors.push(error(`${path}[else]`, `must be null. given: '${value}'`, value.range));
            }
        } else {
            if (value === null || value.value === null) {
                errors.push(error(`${path}[${key.value}]`, `must be a string or boolean. given type: null`, key.range));
                // this is a very common case (we typed 'if: ' and the value is still null). unfortunately we cannot reliably
                // obtain the value range, not even from the cst(?!). So we do this workaround and only calculate
                // the range based on the key range, see this: https://github.com/eemeli/yaml/discussions/231
                _conditionRanges.push([key.range[1] + 1, key.range[1] + 2]);
            } else if (!isString(value) && !isBoolean(value)) {
                errors.push(error(`${path}[${key.value}]`, `must be a string or boolean. given type: ${displayType(value)}`, value.range));
            } else {
                _conditionRanges.push(value.range);
            }
        }
    }
    if (isOperator) {
        if (value === null) {
            errors.push(error(`${path}[${key.value}]`, `must be a number. given type: null`, key.range));
        } else if (!isNumber(value)) {
            errors.push(error(`${path}[${key.value}]`, `must be a number. given type: ${displayType(value)}`, value.range));
        }
    }
    if (isClause === isOperator) {
        console.error(`Unexpected statement: ${isClause} ${isOperator} ${key} ${value}`);
    }
    return errors;
}

function validateDistanceInfluence(value) {
    if (!isYamlPlain(value.type))
        return [error(`distance_influence`, `must be a number. given type: ${displayType(value)}`, value.range)];
    else if (!isNumber(value))
        return [error(`distance_influence`, `must be a number. given: '${value.value}'`, value.range)];
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
    if (area === null || area.value === null)
        return [error(`${path}[${areaName.value}]`, `must not be null`, areaName.range)];

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
    if (value === null || value.value === null) {
        return [error(`${path}[${key.value}]`, `must not be null`, key.range)];
    } else if (key.value === 'type') {
        if (value.value !== 'Feature')
            return [error(`${path}[${key.value}]`, `must be 'Feature'. given: '${value.value}'`, value.range)];
        else
            return [];
    } else if (key.value === 'properties') {
        return validateObject(`${path}[${key.value}]`, value, () => true, () => [], '', () => []);
    } else if (key.value === 'id') {
        if (!isString(value)) {
            return [error(`${path}[${key.value}]`, `must be a string. given type: ${displayType(value)}`, value.range)];
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
    if (value === null || value.value === null) {
        return [error(`${path}[${key.value}]`, `must not be null`, key.range)];
    } else if (key.value === 'type') {
        if (value.value !== 'Polygon')
            return [error(`${path}[${key.value}]`, `must be 'Polygon'. given: '${value.value}'`, value.range)];
        else
            return [];
    } else if (key.value === 'coordinates') {
        return validateCoordinates(`${path}[${key.value}]`, key, value);
    } else {
        console.error(`unexpected area field ${key.value}`);
        return [];
    }
}

function validateCoordinates(path, key, value) {
    return validateList(path, value, 1, -1, validateLinearRing);
}

function validateList(path, node, minLength, maxLength, validateListItem) {
    if (!isYamlList(node.type))
        return [error(path, `must be a list. given type: ${displayType(node)}`, node.range)];

    const items = node.items;
    if (items.length < minLength)
        return [error(path, `minimum length: ${minLength}, given: ${items.length}`, node.range)];
    if (maxLength >= 0 && items.length > maxLength)
        return [error(path, `maximum length: ${maxLength}, given: ${items.length}`, node.range)];

    const errors = [];
    for (let i = 0; i < items.length; ++i) {
        const item = items[i];
        if (item === null) {
            const range = node.cstNode.items[i].range;
            errors.push(error(`${path}[${i}]`, `must not be null`, [range.start, range.end]));
        } else if (isYamlPair(item.type)) {
            // this is a bit ugly. we do not reliably get useful range information, e.g. for nested pairs
            // so we fallback to the range of the entire node. maybe related: https://github.com/eemeli/yaml/discussions/231
            errors.push(error(`${path}`, `must not contain pairs`, node.range));
            break;
        } else {
            errors.push.apply(errors, validateListItem(`${path}[${i}]`, item, i));
        }
    }
    return errors;
}

function validateLinearRing(path, ring, index) {
    const errors = validateList(path, ring, 4, -1, validatePoint);
    if (errors.length > 0)
        return errors;
    const firstPoint = ring.items[0];
    const lastPoint = ring.items[ring.items.length - 1];
    if (!pointsEqual(firstPoint, lastPoint))
        return [error(`${path}`, `the last point must be equal to the first`, lastPoint.range)];
    else
        return [];
}

function validatePoint(path, point, index) {
    return validateList(path, point, 2, 2, validateCoordinate);
}

function validateCoordinate(path, coordinate, index) {
    const errors = [];
    if (!isNumber(coordinate)) {
        errors.push(error(path, `must be a number`, coordinate.range));
    } else {
        if (index === 0 && (coordinate.value < -180 || coordinate.value > 180))
            errors.push(error(`${path}`, `longitude must be in [-180, +180]`, coordinate.range));
        if (index === 1 && (coordinate.value < -90 || coordinate.value > 90))
            errors.push(error(`${path}`, `latitude must be in [-90, +90]`, coordinate.range));
    }
    return errors;
}

function pointsEqual(p, q) {
    return p.items[0].value === q.items[0].value && p.items[1].value === q.items[1].value;
}

function isValidAreaName(string) {
    const regex = /^[a-z][0-9A-Za-z_]*$/g
    return regex.test(string);
}

function validateObject(path, obj, keyIsValid, validateKeys, message, validateKeyValuePair) {
    if (obj.value === null)
        return [error(path, `must not be null`, obj.range)];
    if (!isYamlObject(obj.type))
        return [error(path, `must be an object. given type: ${displayType(obj)}`, obj.range)];

    const keyValidation = validateObjectKeys(path, keyIsValid, message, obj);
    if (keyValidation.errors.length > 0) return keyValidation.errors;

    const errors = [];
    errors.push.apply(errors, validateKeys(path, keyValidation.keys, obj.range));
    if (errors.length > 0) return errors;

    errors.push.apply(errors, validateObjectItems(path, obj.items, validateKeyValuePair));
    return errors;
}

function validateObjectKeys(objectKey, keyIsValid, message, obj) {
    const errors = [];
    const keys = new Set();
    const entries = obj.items;
    for (let i = 0; i < entries.length; ++i) {
        const key = entries[i].key;
        if (key === undefined || key === null) {
            errors.push(error(objectKey, `keys must not be null`, obj.range));
        } else if (!isString(key)) {
            errors.push(error(objectKey, `keys must be strings. given type: ${displayType(key)}`, key.range));
        } else if (key.value.length === 0 || key.value.trim().length === 0) {
            errors.push(error(objectKey, `keys must be non-empty and must not only consist of whitespace. given: '${key}'`, key.range));
        } else if (!keyIsValid(key)) {
            errors.push(error(objectKey, `${message}. given: '${key}'`, key.range));
        } else if (keys.has(key.value)) {
            errors.push(error(objectKey, `keys must be unique. duplicate: '${key.value}'`, key.range))
        } else {
            keys.add(key.value);
        }
    }
    return {
        errors,
        keys: Array.from(keys)
    };
}

function validateObjectItems(path, items, validateKeyValuePair) {
    const errors = [];
    for (let i = 0; i < items.length; ++i) {
        const key = items[i].key;
        const value = items[i].value;
        errors.push.apply(errors, validateKeyValuePair(path, key, value));
    }
    return errors;
}

function error(path, message, range) {
    return {path, message, range};
}

function isYamlObject(yamlParserType) {
    return yamlParserType === 'MAP' || yamlParserType === 'FLOW_MAP';
}

function isYamlList(yamlParserType) {
    return yamlParserType === 'SEQ' || yamlParserType === 'FLOW_SEQ';
}

function isYamlString(yamlParserType) {
    return yamlParserType === 'PLAIN' || yamlParserType === 'QUOTE_SINGLE' || yamlParserType === 'QUOTE_DOUBLE';
    // todo: support BLOCK_LITERAL etc.?
}

function isYamlPlain(yamlParserType) {
    return yamlParserType === 'PLAIN';
}

function isYamlPair(yamlParserType) {
    return yamlParserType === 'PAIR';
}

function displayType(node) {
    if (isYamlObject(node.type)) {
        return tr['object'];
    } else if (isYamlList(node.type)) {
        return tr['list'];
    } else if (isString(node)) {
        return tr['string'];
    } else if (isNumber(node)) {
        return tr['number'];
    } else if (isBoolean(node)) {
        return tr['boolean'];
    } else if (isPair(node)) {
        return tr['pair'];
    } else {
        console.error(`Unknown yaml parser type ${node} ${node.type}`);
    }
}

function displayList(list) {
    const str = list.map(l => `'${l}'`).join(', ');
    return `[${str}]`;
}

function isString(node) {
    return isYamlString(node.type) && typeof node.value === 'string';
}

function isNumber(node) {
    return isYamlPlain(node.type) && typeof node.value === "number" && !isNaN(node.value);
}

function isBoolean(node) {
    return isYamlPlain(node.type) && typeof node.value === 'boolean';
}

function isPair(node) {
    return isYamlPair(node.type);
}

// translations (english only atm)
// todo: extract error messages as well
const tr = {
    'object': 'object',
    'list': 'list',
    'string': 'string',
    'number': 'number',
    'boolean': 'boolean',
    'pair': 'pair'
}
