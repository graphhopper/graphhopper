import {findNodeAtOffset, parseTree} from "jsonc-parser";

/**
 * Returns auto-complete suggestions for a json string and a given character position
 */
export function completeJson(content, pos) {
    // pad the content in case the position is out of range
    while (pos >= content.length) content += ' ';
    const jsonPath = getJsonPath(content, pos);
    const signatureString = jsonPath.signature.join('-');
    if (
        /^root-object(-property|-property-key)?$/.test(signatureString)
    ) {
        let suggestions = ['"speed"', '"priority"', '"distance_influence"', '"areas"']
            .filter(s => !keyAlreadyExistsInOtherPairs(jsonPath.path[0].children, jsonPath.path[1], s));
        return {
            suggestions,
            range: jsonPath.signature[jsonPath.signature.length - 1] === 'key' ? jsonPath.tokenRange : [pos, pos + 1]
        }
    } else if (
        /^root-object-property\[distance_influence]-value$/.test(signatureString)
    ) {
        return {
            suggestions: ['__hint__type a number'],
            range: jsonPath.tokenRange
        }
    } else if (
        /^root-object-property\[(speed|priority)]-array\[[0-9]+]-object(-property|-property-key)?$/.test(signatureString)
    ) {
        const clauses = ['"if"', '"else_if"', '"else"'];
        const operators = ['"limit_to"', '"multiply_by"'];
        const hasClause = jsonPath.signature.length > 4 && keysAlreadyExistInOtherPairs(jsonPath.path[3].children, jsonPath.path[4], clauses);
        const hasOperator = jsonPath.signature.length > 4 && keysAlreadyExistInOtherPairs(jsonPath.path[3].children, jsonPath.path[4], operators);
        let suggestions = [];
        if (!hasClause)
            suggestions.push(...clauses);
        if (!hasOperator)
            suggestions.push(...operators);
        return {
            suggestions,
            range: jsonPath.signature[jsonPath.signature.length - 1] === 'key' ? jsonPath.tokenRange : [pos, pos + 1]
        }
    } else if (
        /^root-object-property\[(speed|priority)]-array\[[0-9]+]-object-property\[(if|else_if|else)]-value$/.test(signatureString)
    ) {
        return {
            suggestions: ['__hint__type a condition'],
            range: jsonPath.tokenRange
        }
    } else if (
        /^root-object-property\[(speed|priority)]-array\[[0-9]+]-object-property\[(limit_to|multiply_by)]-value$/.test(signatureString)
    ) {
        return {
            suggestions: ['__hint__type a number'],
            range: jsonPath.tokenRange
        }
    } else if (
        /^root-object-property\[areas]-object(-property-key)?$/.test(signatureString)
    ) {
        return {
            suggestions: ['__hint__type an area name'],
            range: jsonPath.tokenRange
        }
    } else if (
        /^root-object-property\[areas]-object-property\[[a-zA-Z0-9_]*]-object(-property-key)?$/.test(signatureString)
    ) {
        const suggestions = ['"geometry"', '"type"']
            .filter(s => !keyAlreadyExistsInOtherPairs(jsonPath.path[4].children, jsonPath.path[5], s));
        return {
            suggestions,
            range: jsonPath.signature[jsonPath.signature.length - 1] === 'key' ? jsonPath.tokenRange : [pos, pos + 1]
        }
    } else if (
        /^root-object-property\[areas]-object-property\[[a-zA-Z0-9_]*]-object-property\[type]-value$/.test(signatureString)
    ) {
        return {
            suggestions: ['"Feature"'],
            range: jsonPath.tokenRange
        }
    } else if (
        /^root-object-property\[areas]-object-property\[[a-zA-Z0-9_]*]-object-property\[geometry]-object(-property-key)?$/.test(signatureString)
    ) {
        const suggestions = ['"type"', '"coordinates"']
            .filter(s => !keyAlreadyExistsInOtherPairs(jsonPath.path[6].children, jsonPath.path[7], s));
        return {
            suggestions,
            range: jsonPath.signature[jsonPath.signature.length - 1] === 'key' ? jsonPath.tokenRange : [pos, pos + 1]
        }
    } else if (
        /^root-object-property\[areas]-object-property\[[a-zA-Z0-9_]*]-object-property\[geometry]-object-property\[type]-value$/.test(signatureString)
    ) {
        return {
            suggestions: ['"Polygon"'],
            range: jsonPath.tokenRange
        }
    } else {
        return {
            suggestions: [],
            range: []
        }
    }
}

/**
 * Returns the JSON path and a special string representation (the 'signature') for the given json string and position.
 * The returned object contains the path as array, its 'signature' as string array and the token range of the token at
 * pos.
 */
export function getJsonPath(json, pos) {
    if (json.trim().length === 0) {
        return {
            path: [],
            signature: ['root'],
            tokenRange: []
        }
    }
    const errors = [];
    const root = parseTree(json, errors, {
        allowEmptyContent: false,
        allowTrailingComma: false,
        disallowComments: true
    });
    if (!root)
        return {
            path: [],
            signature: [],
            tokenRange: []
        }
    const includeRightBound = true;
    const node = findNodeAtOffset(root, pos, includeRightBound);
    if (!node)
        return {
            path: [],
            signature: [],
            tokenRange: []
        }
    const nodePath = getNodePath(node);
    const signature = nodePath.map((n, i) => nodeToPathElement(n, i + 1 < nodePath.length ? nodePath[i + 1] : null))
    signature.unshift('root');
    const range = [node.offset, node.offset + node.length];
    if (node.type === 'property') {
        if (node.colonOffset && pos > node.colonOffset) {
            signature[signature.length - 1] = `property[${node.children[0].value}]`;
            signature.push('value');
            range[0] = node.colonOffset + 1;
        }
    }
    return {
        path: nodePath,
        signature: signature,
        tokenRange: range
    }
}

function getNodePath(node) {
    const path = [node];
    let curr = node;
    while (curr.parent) {
        path.push(curr.parent);
        curr = curr.parent;
    }
    path.reverse();
    return path;
}

function nodeToPathElement(node, child) {
    if (node.type === 'object') {
        return 'object';
    } else if (node.type === 'property') {
        if (child !== null && node.children[1] === child) {
            return `property[${node.children[0].value}]`
        } else {
            return 'property'
        }
    } else if (node.type === 'array') {
        if (child !== null) {
            return `array[${node.children.indexOf(child)}]`
        } else {
            return 'array';
        }
    } else if (node.type === 'string' || node.type === 'number' || node.type === 'boolean') {
        if (node.parent && node.parent.type === 'property')
            if (node.parent.children[0] === node)
                return 'key';
            else if (node.parent.children[1] === node)
                return 'value';
            else
                throw 'a literal in a property should be the first or second child';
        else
            return 'literal';
    } else {
        return `unknown[${node.type}]`;
    }
}

function keyAlreadyExistsInOtherPairs(allPairs, thisPair, key) {
    return allPairs.some(p => p !== thisPair && p.children && `"${p.children[0].value}"` === key);
}

function keysAlreadyExistInOtherPairs(allPairs, thisPair, keys) {
    return allPairs.some(p => p !== thisPair && p.children && keys.indexOf(`"${p.children[0].value}"`) >= 0);
}
