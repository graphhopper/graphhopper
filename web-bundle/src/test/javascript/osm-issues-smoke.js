// Loads the /maps/osm-issues app with a stubbed DOM and a stubbed OpenLayers, so that missing
// element ids, typos and undefined references show up without a browser. It runs the file top to
// bottom the way the browser does, it does not test behaviour.
//
//   node web-bundle/src/test/javascript/osm-issues-smoke.js
//
const fs = require('fs');
const path = require('path');
const DIR = path.resolve(__dirname, '../../main/resources/com/graphhopper/maps/osm-issues');
const html = fs.readFileSync(DIR + '/index.html', 'utf8');

// ids from the page plus the ones index.js builds at runtime, e.g. the map controls
const js = fs.readFileSync(DIR + '/index.js', 'utf8');
const ids = new Set([...(html + js).matchAll(/id="([^"]+)"/g)].map(m => m[1]));
const missing = new Set();

function el(id) {
    const e = {
        id, checked: false, disabled: false, hidden: false, value: '', textContent: '',
        innerHTML: '', open: false, dataset: {}, style: {}, classList: {add(){}, remove(){}, toggle(){}},
        appendChild() {}, removeChild() {}, remove() {}, focus() {}, blur() {}, setAttribute() {},
        get parentNode() { return el(this.id + ':parent'); },
        replaceChildren() {}, prepend() {}, append() {}, scrollIntoView() {}, click() {},
        getAttribute: () => null, addEventListener() {}, querySelector: () => el('x'),
        querySelectorAll: () => [], insertAdjacentHTML() {}, closest: () => null,
    };
    return e;
}

const checkboxes = [
    Object.assign(el('cb1'), {dataset: {type: 'missing_maxheight'}, checked: true}),
    Object.assign(el('cb2'), {dataset: {type: 'missing_maxweight'}}),
    Object.assign(el('cb3'), {dataset: {type: 'missing_bridge'}}),
];
const roadBoxes = [Object.assign(el('r1'), {value: 'secondary,tertiary,residential', checked: true})];

global.document = {
    getElementById(id) {
        if (!ids.has(id)) missing.add(id);
        return el(id);
    },
    querySelectorAll(sel) {
        if (sel.includes('[data-type]')) return checkboxes;
        if (sel.includes('road-group')) return roadBoxes;
        return [];
    },
    querySelector: () => el('x'),
    createElement: () => el('new'),
    addEventListener() {}, body: el('body'), head: el('head'),
};
global.window = {
    addEventListener() {}, location: {}, history: {replaceState() {}},
    matchMedia: () => ({matches: false, addEventListener() {}}),
};
global.localStorage = {
    store: {}, getItem(k) { return this.store[k] ?? null; }, setItem(k, v) { this.store[k] = v; },
    removeItem(k) { delete this.store[k]; },
};
global.location = {origin: 'http://x', pathname: '/maps/osm-issues/', search: '', hash: '', href: 'http://x/'};
global.history = {replaceState() {}};
global.fetch = () => new Promise(() => {});
global.AbortController = class { constructor() { this.signal = {}; } abort() {} };
const fakeAuth = () => ({
    authenticated: () => false, fetch: () => new Promise(() => {}),
    authenticate() {}, logout() {}, options() {}, bringPopupWindowToFront() {},
});
fakeAuth.osmAuth = fakeAuth;
global.osmAuth = fakeAuth;
global.alert = () => {};
global.confirm = () => false;
global.navigator = {clipboard: {writeText() {}}};

// A Proxy that answers to anything, so every ol.* constructor and method works. It has to be
// coercible too: the app does things like map.getView().getZoom() < MIN_ZOOM.
const trap = {
    get(t, p) {
        if (p === 'then') return undefined;                 // must not look like a promise
        if (p === Symbol.toPrimitive) return () => 14;      // a zoom level that passes MIN_ZOOM
        if (p === Symbol.iterator) return function* () {};
        if (p === 'length') return 0;
        if (p === 'valueOf') return () => 14;
        if (p === 'toString') return () => '14';
        return anything;
    },
    apply: () => anything,
    construct: () => anything,
};
const anything = new Proxy(function () {}, trap);
global.ol = anything;

// the browser runs both files in one global scope, require() would not share osmIssuesConfig
const vm = require('vm');
vm.runInThisContext(fs.readFileSync(DIR + '/config.js', 'utf8') + '\n'
    + fs.readFileSync(DIR + '/index.js', 'utf8'), {filename: 'osm-issues'});

if (missing.size) {
    console.log('FAIL - index.js asks for element ids that are never defined:');
    for (const m of missing) console.log('   #' + m);
    process.exit(1);
}
console.log('OK - loaded, all ' + ids.size + ' element ids referenced are defined');
