import YAML from 'yaml';

const rootKeys = ['speed', 'priority', 'distance_influence', 'areas'];
const rootKeysString = `['speed', 'priority', 'distance_influence', 'areas']`;

const clauses = ['if', 'else_if', 'else'];
const clausesString = `['if', 'else_if', 'else']`;
const operators = ['multiply_by', 'limit_to'];
const operatorsString = `['multiply_by', 'limit_to']`;

const statementKeys = ['if', 'else_if', 'else', 'multiply_by', 'limit_to'];
const statementKeysString = `['if', 'else_if', 'else', 'multiply_by', 'limit_to']`;

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

    // root must be a yaml object
    if (!isYamlObject(doc.contents.type)) {
        return [
            error(`root`, `must be an object. possible keys: ${rootKeysString}. given type: ${displayType(doc.contents)}`, doc.contents.range)
        ]
    }

    // root elements must be strings with certain values
    {
        const errors = validateRootKeys(doc.contents);
        if (errors.length > 0) return errors;
    }

    // root elements must have certain types
    {
        const errors = validateRootValues(doc.contents.items);
        if (errors.length > 0) return errors;
    }

    // document is valid
    return [];
}

function validateRootKeys(rootObj) {
    return validateObjectKeys('root', rootKeys, rootKeysString, rootObj, rootKeys.length);
}

function validateRootValues(rootItems) {
    const errors = [];
    for (let i = 0; i < rootItems.length; ++i) {
        const key = rootItems[i].key.value;
        const value = rootItems[i].value;
        if (value === null) {
            errors.push(error(`${key}`, `must not be null`, rootItems[i].key.range));
        } else {
            if (key === 'speed' || key === 'priority') {
                if (!isYamlList(value.type)) {
                    errors.push(error(`${key}`, `must be a list. given type: ${displayType(value)}`, value.range));
                } else {
                    errors.push.apply(errors, validateStatements(key, value));
                }
            } else if (key === 'distance_influence') {
                if (!isYamlPlain(value.type))
                    errors.push(error(`${key}`, `must be a number. given type: ${displayType(value)}`, value.range));
                else if (!isNumber(value))
                    errors.push(error(`${key}`, `must be a number. given: '${value.value}'`, value.range));
            } else if (key === 'areas') {
                if (!isYamlObject(value.type)) {
                    errors.push(error(`${key}`, `must be an object. given type: ${displayType(value)}`, value.range));
                } else {
                    errors.push.apply(errors, validateAreas(value));
                }
            } else {
                console.error(`Unexpected root key ${key}`);
            }
        }
    }
    return errors;
}

function validateStatements(key, itemsObj) {
    const errors = [];
    const items = itemsObj.items;
    for (let i = 0; i < items.length; ++i) {
        const item = items[i];
        if (item === null) {
            const range = itemsObj.cstNode.items[i].range;
            errors.push(error(`${key}[${i}]`, `every statement must be an object with a clause ${clausesString} and an operator ${operatorsString}. given type: null`, [range.start, range.end]));
        } else if (isYamlPair(item.type)) {
            errors.push(error(`${key}[${i}]`, `every statement must be an object with a clause ${clausesString} and an operator ${operatorsString}. given type: ${displayType(item)}`, [item.key.range[0], item.value.range[1]]));
        } else if (!isYamlObject(item.type))
            errors.push(error(`${key}[${i}]`, `every statement must be an object with a clause ${clausesString} and an operator ${operatorsString}. given type: ${displayType(item)}`, item.range));
        else
            errors.push.apply(errors, validateStatement(key, i, item));
    }

    if (errors.length > 0)
        return errors;

    // statements seem to be ok, but are they in the right order?
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

function validateStatement(statementKey, statementIndex, statementItem) {
    const statementEntries = statementItem.items;
    const errors = validateObjectKeys(`${statementKey}[${statementIndex}]`, statementKeys, statementKeysString, statementItem, 2);
    if (errors.length > 0) return errors;

    let hasClause = false;
    let hasOperator = false;
    const keys = [];
    for (let i = 0; i < statementEntries.length; ++i) {
        const entry = statementEntries[i];
        const key = entry.key.value;
        keys.push(key);
        const isClause = clauses.indexOf(key) >= 0;
        const isOperator = operators.indexOf(key) >= 0;
        if (isClause) {
            hasClause = true;
            if (key === 'else') {
                if (entry.value !== null) {
                    errors.push(error(`${statementKey}[${statementIndex}]`, `the value of 'else' must be null. given: '${entry.value}'`, entry.value.range));
                }
            } else {
                if (entry.value === null || entry.value.value === null) {
                    errors.push(error(`${statementKey}[${statementIndex}]`, `the value of '${key}' must be a string or boolean. given type: null`, entry.key.range));
                    // this is a very common case (we typed 'if: ' and the value is still null). unfortunately we cannot reliably
                    // obtain the value range, not even from the cst(?!). So we do this workaround and only calculate
                    // the range based on the key range, see this: https://github.com/eemeli/yaml/discussions/231
                    _conditionRanges.push([entry.key.range[1] + 1, entry.key.range[1] + 2]);
                } else if (!isString(entry.value) && !isBoolean(entry.value)) {
                    errors.push(error(`${statementKey}[${statementIndex}]`, `the value of '${key}' must be a string or boolean. given type: ${displayType(entry.value)}`, entry.value.range));
                } else {
                    _conditionRanges.push(entry.value.range);
                }
            }
        }
        if (isOperator) {
            hasOperator = true;
            if (entry.value === null) {
                errors.push(error(`${statementKey}[${statementIndex}]`, `the value of '${key}' must be a number. given type: null`, entry.key.range));
            } else if (!isNumber(entry.value)) {
                errors.push(error(`${statementKey}[${statementIndex}]`, `the value of '${key}' must be a number. given type: ${displayType(entry.value)}`, entry.value.range));
            }
        }
        if (isClause === isOperator) {
            console.error(`Unexpected statement: ${isClause} ${isOperator} ${key} ${clauses.indexOf(key)} ${entry}`);
        }
    }
    if (!hasClause) {
        errors.push(error(`${statementKey}[${statementIndex}]`, `every statement must have a clause ${clausesString}. given: ${keys}`, statementItem.range));
    }
    if (!hasOperator) {
        errors.push(error(`${statementKey}[${statementIndex}]`, `every statement must have an operator ${operatorsString}. given: ${keys}`, statementItem.range));
    }
    return errors;
}

function validateAreas(areas) {
    // todo: currently we only check the area names, but do not check their values! this could be a use-case for json
    // schema validation, because we could use a ready-made geo-json schema. or maybe just not validate it at all...
    const errors = [];
    const keys = new Set();
    const entries = areas.items;
    for (let i = 0; i < entries.length; ++i) {
        const key = entries[i].key;
        if (key === undefined || key === null) {
            errors.push(error(`areas`, `keys must not be null`, areas.range));
        } else if (!isString(key)) {
            errors.push(error(`areas`, `keys must be strings. given type: ${displayType(key)}`, key.range));
        } else if (key.value.length === 0) {
            errors.push(error(`areas`, `keys must not be empty. given: '${key}'`, key.range));
        } else if (!isValidAreaName(key.value)) {
            errors.push(error(`areas`, `invalid area name: '${key.value}', only a-z, digits and _ are allowed`, key.range));
        } else if (keys.has(key.value)) {
            errors.push(error(`areas`, `keys must be unique. duplicate: '${key.value}'`, key.range))
        } else {
            keys.add(key.value);
            _areas.push(key.value);
        }
    }
    return errors;
}

function isValidAreaName(string) {
    const regex = /^[a-z][0-9A-Za-z_]*$/g
    return regex.test(string);
}

function validateObjectKeys(objectKey, legalKeys, legalKeysString, obj, maxKeys) {
    const errors = [];
    const keys = new Set();
    const entries = obj.items;
    for (let i = 0; i < entries.length; ++i) {
        const key = entries[i].key;
        if (key === undefined || key === null) {
            errors.push(error(`${objectKey}`, `possible keys: ${legalKeysString}. given: ${key}`, obj.range));
        } else if (!isString(key) || key.value.length === 0 || key.value.trim().length === 0 || legalKeys.indexOf(key.value) < 0) {
            errors.push(error(`${objectKey}`, `possible keys: ${legalKeysString}. given: '${key}'`, key.range));
        } else if (keys.has(key.value)) {
            errors.push(error(`${objectKey}`, `keys must be unique. duplicate: '${key.value}'`, key.range))
        } else {
            keys.add(key.value);
        }
    }
    if (keys.size > maxKeys) {
        errors.push(error(`${objectKey}`, `too many keys. maximum: ${maxKeys}. given: ${Array.from(keys).sort()}`, obj.range));
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
