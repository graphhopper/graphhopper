// Map app that shows OSM ways with missing bridge related tags (see /osm-issues) and lets you fix
// them right here: log in to OSM, edit the tags of a way, collect the changes and upload them as a
// single changeset.
// About "a whole city fits on the screen" - a view this size answers in a few seconds and is a
// few hundred kB. Further out the bounding box grows faster than the map is useful.
const MIN_ZOOM = 11;
const LIMIT = 6000;
const DEFAULT_COMMENT = 'add missing maxheight/maxweight tags at bridges';
// How far the crossing may be from the way as OSM has it now. The import simplifies geometries
// (import.osm.max_way_point_distance, 0.5m by default), set that to 0 to use a value this small.
const MAX_CROSSING_OFFSET = 0.2;

// everything that differs per issue type: marker color, what to tell the user and which tag it is about
const ISSUES = {
    missing_maxheight: {
        color: '#e6194b', tag: 'maxheight',
        title: 'missing maxheight below a bridge',
        // the first way is the one that needs the tag, say so on the buttons
        roles: ['needs the tag', 'the bridge above']
    },
    missing_maxweight: {
        color: '#f58231', tag: 'maxweight',
        title: 'bridge without maxweight'
    },
    missing_maxheight_tunnel: {
        color: '#911eb4', tag: 'maxheight',
        title: 'missing maxheight in a tunnel',
        hint: 'a tunnel or a roof over the road. Nearly six in ten of these turn out to carry a real '
            + 'sign, twice the rate under bridges.'
    },
    missing_bridge: {
        color: '#4363d8', tag: 'bridge', warn: true,
        title: 'missing bridge tag',
        hint: 'these ways cross without a junction. Careful: usually only a part of one way is the '
            + 'bridge, so it has to be split first - that is easier in the iD editor.'
    }
};

// the only tags this app may add or change. Tags are never deleted and the geometry of a way is
// uploaded exactly as it comes from the API.
const EDITABLE = ['maxheight', 'maxweight', 'bridge'];

const $ = id => document.getElementById(id);
const tagInput = key => $('tag-' + key);

function el(tag, text, className) {
    const e = document.createElement(tag);
    if (text) e.textContent = text;
    if (className) e.className = className;
    return e;
}

// ---------------------------------------------------------------- settings

// everything configurable lives in config.js, ?gh=... overrides the server for one visit
const deployed = typeof osmIssuesConfig === 'object' ? osmIssuesConfig : {};

const settings = {
    api: deployed.osmApi || 'https://www.openstreetmap.org',
    gh: (new URLSearchParams(location.search).get('gh') || deployed.graphhopperUrl || '').replace(/\/$/, ''),
    // an OAuth app is registered against one instance, so the dev API needs its own id
    get clientId() {
        return (this.isDevApi ? deployed.osmClientIdDev : deployed.osmClientId) || '';
    },
    mapillaryToken: deployed.mapillaryToken || '',
    get isDevApi() {
        return this.api.includes('dev.openstreetmap');
    }
};

const redirectUri = location.origin + location.pathname.replace(/\/?$/, '/');
let pendingEdits = JSON.parse(localStorage.getItem('osm_pending') || '[]');
// ways we uploaded ourselves. GraphHopper keeps reporting them until it is imported again
let uploadedWays = JSON.parse(localStorage.getItem('osm_uploaded') || '[]');
let currentIssue = null, currentWay = null, selectedFeature = null;

// ---------------------------------------------------------------- map + issues

const issueSource = new ol.source.Vector();
const waySource = new ol.source.Vector();

// the way that is currently being edited, drawn below the markers with a white casing
const wayLayer = new ol.layer.Vector({
    source: waySource,
    style: [
        new ol.style.Style({stroke: new ol.style.Stroke({color: '#fff', width: 11})}),
        new ol.style.Style({stroke: new ol.style.Stroke({color: '#2d7dd2', width: 5})})
    ]
});

const redrawMarkers = () => {
    issueSource.changed();
    osmTagSource.changed();
};

/** the mark that says "this is the one you are editing" - the middle is the only free channel here,
 *  colour and size already carry the issue type and how promising the place is */
const selectedDot = radius => new ol.style.Style({
    image: new ol.style.Circle({
        radius: Math.max(2.5, radius / 3),
        fill: new ol.style.Fill({color: '#fff'})
    })
});

const issueLayer = new ol.layer.Vector({
    source: issueSource,
    style: feature => {
        const wayId = feature.get('way_id');
        const edited = pendingEdits.some(e => e.wayId === wayId);
        const done = uploadedWays.includes(wayId);
        // colour says what kind of issue it is, size says how likely a visit finds a sign here
        // rather than another maxheight=default. Without a score (the other issue types) they are
        // all the same size.
        const p = feature.get('p_sign');
        const radius = done ? 4 : (p == null ? 7 : 4 + 7 * p * p);
        const marker = new ol.style.Style({
            image: new ol.style.Circle({
                radius: radius,
                fill: new ol.style.Fill({
                    color: done ? '#bbb' : (ISSUES[feature.get('type')] || {}).color || '#000'
                }),
                stroke: new ol.style.Stroke({color: edited ? '#2e9e4f' : '#fff', width: edited ? 3 : 2})
            })
        });
        return feature === selectedFeature ? [marker, selectedDot(radius)] : marker;
    }
});

// Mapillary's traffic sign detections, narrowed to the height signs. They answer the question the
// elevation cannot: is there a sign here at all, or would a visit only produce maxheight=default.
// Detections carry no value, so the number still has to be read off the photo.
const HEIGHT_SIGNS = new Set([
    'regulatory--height-limit--g1',
    'warning--height-restriction--g2', 'warning--height-restriction--g3',
    'warning--height-restriction--g4', 'warning--height-restriction--g5',
    'information--height-limit--g1', 'information--height-limit--g2',
    'complementary--height-limit--g1', 'complementary--height-limit--g2'
]);

// Our own sign: the red ring and the two arrows of a height restriction, with a question mark
// where the number would be. Mapillary's own icon carries a placeholder height (it draws "3 m" on
// every one of them) and the detection does not carry the real value - unlike speed limits, where
// it is part of the class name - so anything but a question mark here would be invented.
const SIGN_ICON = 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMCIgaGVpZ2h0PSIzMCIgdmlld0JveD0iMCAwIDMwIDMwIj48Y2lyY2xlIGN4PSIxNSIgY3k9IjE1IiByPSIxMiIgZmlsbD0iI2ZmZmZmZiIgc3Ryb2tlPSIjZDAwMjFiIiBzdHJva2Utd2lkdGg9IjMuNCIvPjxwYXRoIGQ9Ik0xMS4zIDYuNiBMMTguNyA2LjYgTDE1IDEwLjIgWiIgZmlsbD0iIzFhMWExYSIvPjxwYXRoIGQ9Ik0xMS4zIDIzLjQgTDE4LjcgMjMuNCBMMTUgMTkuOCBaIiBmaWxsPSIjMWExYTFhIi8+PHRleHQgeD0iMTUiIHk9IjE4LjEiIHRleHQtYW5jaG9yPSJtaWRkbGUiIGZpbGw9IiMxYTFhMWEiIGZvbnQtZmFtaWx5PSJIZWx2ZXRpY2EsQXJpYWwsc2Fucy1zZXJpZiIgZm9udC1zaXplPSI4LjYiIGZvbnQtd2VpZ2h0PSI3MDAiPj8gbTwvdGV4dD48L3N2Zz4=';

const signStyle = new ol.style.Style({
    image: new ol.style.Icon({src: SIGN_ICON, scale: 1})
});

// Mapillary serves these at zoom 14 only, so the tile grid has that single level: closer in
// OpenLayers scales one tile up, further out it fetches four times as many per zoom step. A
// 1600x900 view needs about 22 tiles at zoom 14 and 88 at 13, against a budget of 50000 a day -
// which is why the layer stops there and does not go to 12, where it would be 350.
const SIGN_MIN_ZOOM = 13;
const signLayer = new ol.layer.VectorTile({
    visible: false,
    declutter: true,
    minZoom: SIGN_MIN_ZOOM - 0.01,
    source: new ol.source.VectorTile({
        format: new ol.format.MVT(),
        minZoom: 14,
        maxZoom: 14,
        attributions: 'signs &copy; <a href="https://www.mapillary.com" target="_blank">Mapillary</a>',
        url: 'https://tiles.mapillary.com/maps/vtp/mly_map_feature_traffic_sign/2/{z}/{x}/{y}'
    }),
    style: feature => HEIGHT_SIGNS.has(feature.get('value')) ? signStyle : null
});

// What OpenStreetMap already says, as an overlay: the same crossings, but the ones that already
// carry maxheight, drawn with the value so it can be compared against a photo at a glance.
const osmTagSource = new ol.source.Vector();
const osmTagLayer = new ol.layer.Vector({
    visible: false,
    source: osmTagSource,
    style: feature => {
        const marker = new ol.style.Style({
            image: new ol.style.Circle({
                radius: 8,
                fill: new ol.style.Fill({color: '#2e9e4f'}),
                stroke: new ol.style.Stroke({color: '#fff', width: 2})
            }),
            text: new ol.style.Text({
                text: String(feature.get('maxheight') || ''),
                offsetY: -16,
                font: '600 12px system-ui, sans-serif',
                fill: new ol.style.Fill({color: '#1b5e2a'}),
                stroke: new ol.style.Stroke({color: '#fff', width: 3})
            })
        });
        return feature === selectedFeature ? [marker, selectedDot(8)] : marker;
    }
});

const map = new ol.Map({
    target: 'map',
    // order matters: the highlighted way is a line under the markers, not across them, and the
    // issues stay on top of everything
    layers: [new ol.layer.Tile({source: new ol.source.OSM()}), signLayer, wayLayer, osmTagLayer, issueLayer],
    view: new ol.View(parseHash() || {center: [0, 0], zoom: 2})
});

function parseHash() {
    const parts = location.hash.replace('#', '').split('/');
    if (parts.length === 3 && !isNaN(parts[0]))
        return {center: ol.proj.fromLonLat([+parts[2], +parts[1]]), zoom: +parts[0]};
    return null;
}

// Two more controls under the zoom buttons. They have to go through addControl: OpenLayers puts
// pointer-events:none on the container its controls live in and only sets it back to auto on the
// elements it manages itself, so an appended div would be visible but dead.
function addMapTools() {
    const icon = paths => '<svg viewBox="0 0 24 24" width="21" height="21" fill="none" '
        + 'stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">'
        + paths + '</svg>';
    const tools = el('div', '', 'ol-control ol-unselectable map-tools');
    tools.innerHTML = '<button id="goto-toggle" type="button" title="go to coordinates">'
        + icon('<circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5 21 21"/>') + '</button>'
        + '<input id="goto" type="text" placeholder="lat, lon" aria-label="go to coordinates" '
        + 'autocomplete="off" spellcheck="false">'
        + '<button id="locate" type="button" title="go to my location">'
        + icon('<circle cx="12" cy="12" r="6"/>'
               + '<circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none"/>'
               + '<path d="M12 1.5v3.5M12 19v3.5M1.5 12h3.5M19 12h3.5"/>') + '</button>';
    map.addControl(new ol.control.Control({element: tools}));
    // the field only takes up the width once it is needed
    const open = on => {
        tools.classList.toggle('open', on);
        if (on) $('goto').focus(); else $('goto').classList.remove('bad');
    };
    $('goto-toggle').onclick = () => open(true);
    $('goto').addEventListener('keydown', e => {
        if (e.key === 'Enter' && goToCoordinates(e.target)) open(false);
        if (e.key === 'Escape') open(false);
    });
    $('goto').addEventListener('blur', () => {
        if (!$('goto').value.trim()) open(false);
    });
    $('locate').onclick = goToMyLocation;
}

/** accepts "52.52, 13.40", "52.52 13.40" and a zoom/lat/lon hash, ours as well as openstreetmap.org's */
function parseCoordinates(text) {
    const hash = text.match(/#(?:map=)?([\d.]+)\/(-?[\d.]+)\/(-?[\d.]+)/);
    if (hash) return {lat: +hash[2], lon: +hash[3], zoom: +hash[1]};
    const pair = text.match(/(-?\d+(?:\.\d+)?)\s*[,; ]\s*(-?\d+(?:\.\d+)?)/);
    if (!pair) return null;
    const lat = +pair[1], lon = +pair[2];
    if (Math.abs(lat) > 90 || Math.abs(lon) > 180) return null;
    return {lat: lat, lon: lon};
}

function goToCoordinates(input) {
    const c = parseCoordinates(input.value);
    input.classList.toggle('bad', !c && input.value.trim() !== '');
    if (!c) return false;
    input.blur();
    map.getView().animate({center: ol.proj.fromLonLat([c.lon, c.lat]), zoom: c.zoom || 17});
    return true;
}

function goToMyLocation() {
    if (!navigator.geolocation) {
        alert('this browser has no location support');
        return;
    }
    setStatus('locating ...');
    navigator.geolocation.getCurrentPosition(
        pos => map.getView().animate({
            center: ol.proj.fromLonLat([pos.coords.longitude, pos.coords.latitude]), zoom: 17
        }),
        err => alert('could not get your location: ' + err.message),
        {enableHighAccuracy: true, timeout: 10000});
}

addMapTools();

// without a position in the url hash we show the area of the imported map
if (!parseHash())
    ghFetch('/info').then(info => map.getView().fit(
        ol.proj.transformExtent(info.bbox, 'EPSG:4326', 'EPSG:3857'), {size: map.getSize(), maxZoom: 16}
    )).catch(err => setStatus(err.message));

// ---------------------------------------------------------------- loading

/** is the second [minLon, minLat, maxLon, maxLat] inside the first one? */
const contains = (a, b) => a[0] <= b[0] && a[1] <= b[1] && a[2] >= b[2] && a[3] >= b[3];

/**
 * Fills a source from /osm-issues, one request at a time: a newer one aborts the older. It also
 * remembers what the source holds, so zooming in or panning inside that area loads nothing.
 */
function layerLoader(source) {
    let controller = null, holds = null;
    const loader = {busy: false};
    const setBusy = on => {
        loader.busy = on;
        updateNotes();
    };
    loader.abort = () => {
        if (controller) controller.abort();
        controller = null;
        if (loader.busy) setBusy(false);
    };
    loader.forget = () => holds = null;
    /** starts a request unless the source covers the extent already, returns whether it did */
    loader.request = (extent, params, done = () => {}, failed = () => {}) => {
        loader.abort();
        const query = new URLSearchParams(params).toString();
        if (holds && holds.query === query && contains(holds.extent, extent)) return false;
        controller = new AbortController();
        setBusy(true);
        ghFetch('/osm-issues?bbox=' + extent.map(v => v.toFixed(6)).join(',') + '&' + query + '&limit=' + LIMIT,
            {signal: controller.signal})
            .then(json => {
                source.clear();
                source.addFeatures(new ol.format.GeoJSON().readFeatures(json, {featureProjection: 'EPSG:3857'}));
                // a truncated answer does not cover the area, so do not reuse it when zooming in
                holds = json.features.length < LIMIT ? {query: query, extent: extent} : null;
                setBusy(false);
                done(json.features.length);
            })
            .catch(err => {
                // aborted means replaced by a newer request, which is busy now
                if (err.name === 'AbortError') return;
                setBusy(false);
                failed(err);
            });
        return true;
    };
    return loader;
}

const issues = layerLoader(issueSource), osmTags = layerLoader(osmTagSource);

function load() {
    issues.abort();
    const view = map.getView();
    const extent = ol.proj.transformExtent(view.calculateExtent(map.getSize()), 'EPSG:3857', 'EPSG:4326');
    const zoomedIn = view.getZoom() >= MIN_ZOOM;
    // what OSM has already is not a work list, so the filters of the issues do not apply to it
    if (zoomedIn && osmTagLayer.getVisible())
        osmTags.request(extent, {types: 'missing_maxheight', roads: 'all', tagged: true});

    const types = typeBoxes.filter(cb => cb.checked).map(cb => cb.dataset.type);
    if (!types.length || !roadGroups().length) {
        // unchecking every box is a deliberate action, there the markers should go away at once
        issueSource.clear();
        issues.forget();
        setStatus(roadGroups().length ? 'no issue type selected' : 'no road class selected');
        return;
    }
    // the old markers stay on the map until the new ones are there, so panning does not blank it.
    // The heading says "zoom in" already, only the folded heading needs it spelled out.
    if (!zoomedIn) return setStatus('', 'zoom in');
    const started = issues.request(extent, {types: types.join(','), roads: roadGroups().join(',')},
        n => setStatus(issueCount(n) + ' in this view'),
        // keep whatever is on the map, the error message alone tells what happened
        err => setStatus(err.message));
    if (started) setStatus('', 'loading ...');
    else setStatus(issueCount(issueSource.getFeatures().length) + ' loaded for this area');
}

let moveTimer = null;
map.on('moveend', () => {
    const view = map.getView(), center = ol.proj.toLonLat(view.getCenter());
    history.replaceState(null, '',
        '#' + Math.round(view.getZoom()) + '/' + center[1].toFixed(5) + '/' + center[0].toFixed(5));
    // wait for the map to come to rest, otherwise every pan step starts and aborts a request
    clearTimeout(moveTimer);
    moveTimer = setTimeout(load, 300);
});

// ---------------------------------------------------------------- sidebar

/** The status is about the issues and sits at the end of their group. Folded, or with the sheet
 *  collapsed on a phone, that group is not visible - there the heading carries it instead. */
function setStatus(text, folded = text) {
    $('status').textContent = text;
    $('summary-status').textContent = folded;
}

const issueCount = n => n + (n === 1 ? ' issue' : ' issues');

/** The note behind a layer name: further out nothing is loaded, which would otherwise look as if
 *  there was nothing to show, and a large view can take a few seconds. */
function updateNotes() {
    const zoom = map.getView().getZoom();
    const note = (on, minZoom, loader) => !on ? ''
        : zoom < minZoom ? '(zoom in)' : loader && loader.busy ? '(loading ...)' : '';
    $('issues-note').textContent = note(true, MIN_ZOOM, issues);
    $('osm-note').textContent = note($('layer-osm').checked, MIN_ZOOM, osmTags);
    $('signs-note').textContent = note($('layer-signs').checked, SIGN_MIN_ZOOM);
}
map.getView().on('change:resolution', updateNotes);

const typeBoxes = [...document.querySelectorAll('#issues input[data-type]')];
typeBoxes.forEach(cb => cb.onchange = load);

// the road groups are remembered, they are a setting you pick once and keep
// every box carries the road class names it stands for, so the grouping is only defined here
const roadBoxes = [...document.querySelectorAll('.road-group')];
const roadGroups = () => roadBoxes.filter(cb => cb.checked).map(cb => cb.value);
const storedBoxes = localStorage.getItem('road_classes');
if (storedBoxes !== null) roadBoxes.forEach(cb => cb.checked = storedBoxes.split(';').includes(cb.value));
roadBoxes.forEach(cb => cb.onchange = () => {
    localStorage.setItem('road_classes', roadGroups().join(';'));
    load();
});

/** a layer that is on unless the user turned it off - they are the point of the map */
function layerSwitch(box, layer, key, changed = () => {}) {
    box.checked = !box.disabled && localStorage.getItem(key) !== 'no';
    layer.setVisible(box.checked);
    box.onchange = () => {
        localStorage.setItem(key, box.checked ? 'yes' : 'no');
        layer.setVisible(box.checked);
        updateNotes();
        changed();
    };
}

// The two layers of what is known already. They answer different questions - what OSM has, and
// what a camera has actually seen.
layerSwitch($('layer-osm'), osmTagLayer, 'layer_osm', () => {
    // a hidden layer is not kept up to date, so it starts over
    osmTagSource.clear();
    osmTags.forget();
    load();
});
$('key-sign').style.backgroundImage = 'url(' + SIGN_ICON + ')';
if (settings.mapillaryToken) {
    signLayer.getSource().setUrl('https://tiles.mapillary.com/maps/vtp/mly_map_feature_traffic_sign'
        + '/2/{z}/{x}/{y}?access_token=' + encodeURIComponent(settings.mapillaryToken));
} else {
    $('layer-signs').disabled = true;
    $('layer-signs').parentNode.title = 'needs a mapillaryToken in config.js';
}
layerSwitch($('layer-signs'), signLayer, 'layer_signs');
updateNotes();

// the filter section stays folded the way the user left it
$('filters').open = localStorage.getItem('filters_open') !== 'no';
$('filters').ontoggle = () => localStorage.setItem('filters_open', $('filters').open ? 'yes' : 'no');

// On a phone the sidebar is a bottom sheet: collapsed by default so the map is usable, expanded
// when there is something to do in it. On a wide screen the class does nothing.
const sheet = $('sidebar'), sheetToggle = $('sheet-toggle');
// the same breakpoint as the bottom sheet in index.css
const narrow = window.matchMedia('(max-width: 900px)');
const setSheet = open => {
    sheet.classList.toggle('collapsed', !open);
    sheetToggle.setAttribute('aria-expanded', open ? 'true' : 'false');
};
setSheet(false);
sheetToggle.onclick = () => setSheet(sheet.classList.contains('collapsed'));
// collapsed, the heading is only there for the status, so a tap opens the sheet and not the fold
$('filters').querySelector('summary').addEventListener('click', e => {
    if (!sheet.classList.contains('collapsed')) return;
    e.preventDefault();
    setSheet(true);
});
// an on-screen keyboard covers the lower half of the sheet, so bring the focused field up
sheet.addEventListener('focusin', e => {
    if (e.target.matches('input, textarea'))
        setTimeout(() => e.target.scrollIntoView({block: 'center', behavior: 'smooth'}), 250);
});

// Right click offers the three street level services at the spot under the cursor - useful for
// looking at a place the endpoint did not report, or for checking the surroundings of one it did.
const photoMenu = $('photo-menu');

map.getViewport().addEventListener('contextmenu', evt => {
    evt.preventDefault();
    const [lon, lat] = ol.proj.toLonLat(map.getEventCoordinate(evt)).map(v => v.toFixed(6));
    const zoom = Math.round(map.getView().getZoom());
    photoMenu.innerHTML = '<div class="coord">' + lat + ', ' + lon + '</div>'
        + '<a href="' + settings.api + '/#map=' + zoom + '/' + lat + '/' + lon
        + '" target="_blank">OpenStreetMap</a>' + photoLinks(lat, lon);
    photoMenu.style.left = Math.min(evt.clientX, innerWidth - 170) + 'px';
    photoMenu.style.top = Math.min(evt.clientY, innerHeight - 130) + 'px';
    photoMenu.hidden = false;
});

const hidePhotoMenu = () => photoMenu.hidden = true;

// anything opened over the map closes again when you touch the map, in the order you would expect
document.addEventListener('pointerdown', e => {
    if (!photoMenu.contains(e.target)) hidePhotoMenu();
});
document.addEventListener('keydown', e => {
    if (e.key !== 'Escape') return;
    if (!photoMenu.hidden) hidePhotoMenu();
    else if (!$('edit').hidden) closeEdit();
});
map.on('movestart', hidePhotoMenu);

map.on('singleclick', evt => {
    const hit = (layer, tolerance, accept = () => true) => map.forEachFeatureAtPixel(evt.pixel,
        f => accept(f) ? f : null, {hitTolerance: tolerance, layerFilter: l => l === layer});
    // the green ones carry the same properties, so they open the same panel - useful when a value
    // in OSM turns out to be wrong on the photo
    const feature = hit(issueLayer, 6) || hit(osmTagLayer, 6);
    if (feature) return openIssue(feature);
    const sign = hit(signLayer, 8, f => HEIGHT_SIGNS.has(f.get('value')));
    if (sign) return openSign(sign);
    // clicking the map itself is how you put the panel away - no button needed for that
    if (!$('edit').hidden) closeEdit();
});

/**
 * A detection has no picture of its own, only an id, so ask the API which images show it and open
 * the first one. Without that round trip we could only center the Mapillary map on the coordinate.
 */
function openSign(feature) {
    // not getId(): that is the running number inside the tile, the Mapillary id is a property
    const id = feature.get('id');
    if (!id) return;
    const win = window.open('', '_blank');
    const [lon, lat] = ol.proj.toLonLat(feature.getGeometry().getFirstCoordinate());
    const onMap = 'https://www.mapillary.com/app/?focus=map&lat=' + lat + '&lng=' + lon + '&z=19';
    fetch('https://graph.mapillary.com/' + id + '?access_token='
        + encodeURIComponent(settings.mapillaryToken) + '&fields=images')
        .then(res => res.json())
        .then(json => {
            const img = json?.images?.data?.[0];
            win.location = img ? 'https://www.mapillary.com/app/?pKey=' + img.id + '&focus=photo' : onMap;
        })
        .catch(() => win.location = onMap);
}

/** GET from the GraphHopper server, with an error message that says what came back instead */
function ghFetch(path, options) {
    const url = settings.gh + path;
    return fetch(url, options).then(res => res.text().then(text => {
        let json = null;
        try {
            json = JSON.parse(text);
        } catch (e) { /* not json, handled below */ }
        // anything but JSON means we did not reach GraphHopper at all, e.g. the 404 page of a
        // static web server when the page is served from a different port than GraphHopper
        const hint = json === null && !settings.gh
            ? ' - this did not come from GraphHopper, set its URL in the settings' : '';
        if (!res.ok)
            throw new Error((json && json.message) || res.status + ' ' + res.statusText + ' from ' + url + hint);
        if (json === null) throw new Error('no JSON from ' + url + ' (content-type '
            + (res.headers.get('content-type') || 'unknown') + '): '
            + (text || '(empty response)').replace(/\s+/g, ' ').trim().slice(0, 120) + hint);
        return json;
    })).catch(err => {
        // aborting the previous request while panning is normal, do not report it
        if (err.name !== 'AbortError') console.error('GraphHopper request failed:', url, err);
        throw err;
    });
}

// ---------------------------------------------------------------- photos

function bearing(lat1, lon1, lat2, lon2) {
    const r = Math.PI / 180, dLon = (lon2 - lon1) * r;
    const y = Math.sin(dLon) * Math.cos(lat2 * r);
    const x = Math.cos(lat1 * r) * Math.sin(lat2 * r) - Math.sin(lat1 * r) * Math.cos(lat2 * r) * Math.cos(dLon);
    return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
}

function distance(lat1, lon1, lat2, lon2) {
    return Math.hypot((lat1 - lat2) * 111320, (lon1 - lon2) * 111320 * Math.cos(lat1 * Math.PI / 180));
}

/** shortest distance in meters from a point to a polyline of [lon, lat] pairs */
function distanceToLine(lat, lon, line) {
    const mx = 111320 * Math.cos(lat * Math.PI / 180), my = 111320;
    const px = lon * mx, py = lat * my;
    let min = Infinity;
    for (let i = 1; i < line.length; i++) {
        const ax = line[i - 1][0] * mx, ay = line[i - 1][1] * my;
        const dx = line[i][0] * mx - ax, dy = line[i][1] * my - ay;
        const len2 = dx * dx + dy * dy;
        const t = len2 ? Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2)) : 0;
        min = Math.min(min, Math.hypot(px - (ax + t * dx), py - (ay + t * dy)));
    }
    return min;
}

// We want a photo taken ~40m before the issue and looking at it, so that the bridge and its sign
// are in the picture instead of being right above the camera. It also has to be taken on the way we
// are editing, otherwise we end up with a picture from the bridge itself.
function pickPhoto(photos, lat, lon, line) {
    let best = null;
    for (const p of photos) {
        if (isNaN(p.heading)) continue;
        const dist = distance(lat, lon, p.lat, p.lon);
        if (dist < 20 || dist > 70) continue;
        if (line && distanceToLine(p.lat, p.lon, line) > 20) continue;
        const off = Math.abs((bearing(p.lat, p.lon, lat, lon) - p.heading + 540) % 360 - 180);
        if (off > 40) continue;
        const score = off + Math.abs(dist - 40);
        if (!best || score < best.score) best = {score: score, dist: dist, photo: p};
    }
    return best;
}

function kartaViewPhotos(lat, lon) {
    return fetch('https://api.openstreetcam.org/1.0/list/nearby-photos/', {
        method: 'POST',
        body: new URLSearchParams({lat: lat, lng: lon, radius: '90'})
    }).then(res => res.json()).then(json => (json.currentPageItems || []).map(p => {
        const host = p.lth_name.slice(0, p.lth_name.indexOf('/'));
        return {
            lat: +p.lat, lon: +p.lng, heading: parseFloat(p.heading),
            date: (p.shot_date || p.date_added || '').substring(0, 10),
            thumb: 'https://' + host + '.openstreetcam.org/' + p.lth_name.slice(host.length + 1),
            // its detail view loads the whole sequence and fails on the long ones, so we open the
            // map at the position of this picture instead
            page: 'https://kartaview.org/map/@' + p.lat + ',' + p.lng + ',19z',
            source: 'KartaView'
        };
    }));
}

// panoramax is run by the OSM community and needs no token at all
function panoramaxPhotos(lat, lon) {
    const dLat = 90 / 111320, dLon = dLat / Math.cos(lat * Math.PI / 180);
    const bbox = [lon - dLon, lat - dLat, lon + dLon, lat + dLat].map(v => v.toFixed(6)).join(',');
    return fetch('https://api.panoramax.xyz/api/search?limit=50&bbox=' + bbox)
        .then(res => res.json()).then(json => (json.features || []).map(f => ({
            lat: f.geometry.coordinates[1], lon: f.geometry.coordinates[0],
            heading: f.properties['view:azimuth'],
            date: (f.properties.datetime || '').substring(0, 10),
            thumb: (f.assets.sd || f.assets.thumb).href,
            page: 'https://panoramax.openstreetmap.fr/#focus=pic&pic=' + f.id,
            source: 'Panoramax'
        })));
}

// mapillary cannot open the nearest image by coordinate, it needs the image id from its API
function mapillaryPhotos(lat, lon) {
    if (!settings.mapillaryToken) return Promise.resolve([]);
    const params = new URLSearchParams({
        access_token: settings.mapillaryToken, lat: lat, lng: lon, radius: '90', limit: '50',
        fields: 'id,compass_angle,computed_geometry,captured_at,thumb_1024_url'
    });
    return fetch('https://graph.mapillary.com/images?' + params).then(res => res.json()).then(json =>
        (json.data || []).filter(p => p.computed_geometry).map(p => ({
            lat: p.computed_geometry.coordinates[1], lon: p.computed_geometry.coordinates[0],
            heading: p.compass_angle,
            date: p.captured_at ? new Date(p.captured_at).toISOString().substring(0, 10) : '',
            thumb: p.thumb_1024_url,
            page: 'https://www.mapillary.com/app/?pKey=' + p.id + '&focus=photo',
            source: 'Mapillary'
        })));
}

let photoRequest = 0;

/** the three street level services at a spot, as links */
function photoLinks(lat, lon, panoramaxUrl) {
    return '<a href="https://www.mapillary.com/app/?lat=' + lat + '&lng=' + lon
        + '&z=19&trafficSign=all" target="_blank">Mapillary</a>'
        + '<a href="https://kartaview.org/map/@' + lat + ',' + lon + ',19z" target="_blank">KartaView</a>'
        + '<a href="' + (panoramaxUrl || 'https://panoramax.openstreetmap.fr/#map=19/' + lat + '/' + lon)
        + '" target="_blank">Panoramax</a>';
}

/**
 * The map links of the edit panel, shown right away for the issue itself and updated once a photo
 * is found, because then the position of that photo is the more useful one.
 */
function showLinks(lat, lon, photo) {
    // for panoramax the picture itself is the better link, its map needs a few clicks first
    $('edit-links').innerHTML = photoLinks(lat, lon, photo && photo.source === 'Panoramax' && photo.page);
}

function loadPhoto(lat, lon, line) {
    const req = ++photoRequest;
    $('photo').textContent = 'looking for a photo ...';
    showLinks(lat, lon, null);
    const failed = name => err => (console.error(name + ' lookup failed:', err), []);
    Promise.all([
        panoramaxPhotos(lat, lon).catch(failed('Panoramax')),
        kartaViewPhotos(lat, lon).catch(failed('KartaView')),
        mapillaryPhotos(lat, lon).catch(failed('Mapillary'))
    ]).then(lists => {
        if (req !== photoRequest) return; // another marker was clicked in the meantime
        const best = pickPhoto(lists.flat(), lat, lon, line);
        if (!best) {
            $('photo').textContent = 'no street level photo on this way looking at this spot';
            return;
        }
        const photo = best.photo;
        // the map links now point at the photo, so that the map opens where it was taken
        showLinks(photo.lat, photo.lon, photo);
        const link = el('a');
        link.href = photo.page;
        link.target = '_blank';
        const img = el('img');
        img.src = photo.thumb;
        img.alt = photo.source + ' photo';
        link.append(img);
        $('photo').replaceChildren(link, photo.source + ', ' + Math.round(best.dist)
            + ' m before the spot' + (photo.date ? ', ' + photo.date : ''));
    });
}

// ---------------------------------------------------------------- OSM login

// the OAuth 2 dance is done by the osm-auth library, the same one the iD editor uses
let auth = null;

function initAuth() {
    auth = osmAuth({
        url: settings.api, apiUrl: settings.api, client_id: settings.clientId,
        redirect_uri: redirectUri, scope: 'read_prefs write_api', singlepage: true, auto: false
    });
}

// Every call to the OpenStreetMap API goes through this queue, at most two per second. Clicking
// through markers quickly is normal use, and a marker opens a way lookup each time - without a
// queue that is a burst against somebody else's server for every twitch of the mouse.
const OSM_MIN_GAP = 500;
let osmNextSlot = 0;

function osmSlot() {
    const at = Math.max(Date.now(), osmNextSlot);
    osmNextSlot = at + OSM_MIN_GAP;
    return new Promise(r => setTimeout(r, at - Date.now()));
}

/** authenticated request against the OSM API, returns the response body as text */
function osmFetch(path, options) {
    return osmSlot()
        .then(() => auth.fetch(settings.api + path, options))
        .then(res => res.text().then(text => {
            if (!res.ok) throw new Error(res.status + ' ' + (text || res.statusText));
            return text;
        }));
}

/** reading a way needs no login, so the tags can be inspected before logging in */
function osmRead(path) {
    return osmSlot()
        .then(() => fetch(settings.api + path))
        .then(res => {
            if (!res.ok) throw new Error(res.status + ' ' + res.statusText);
            return res.json();
        });
}

function updateAccount() {
    const loggedIn = auth && auth.authenticated();
    $('login').hidden = loggedIn;
    $('logout').hidden = !loggedIn;
    $('upload').disabled = !loggedIn;
    // without a login the tag fields only invite work that cannot be uploaded, so they stay away
    $('edit-fields').hidden = !loggedIn;
    $('save').hidden = !loggedIn;
    $('account-why').hidden = loggedIn;
    $('account-state').hidden = !loggedIn;
    $('account-state').textContent = loggedIn ? 'logged in' : '';
    if (loggedIn)
        osmFetch('/api/0.6/user/details.json')
            .then(text => $('account-state').textContent = 'logged in as ' + JSON.parse(text).user.display_name)
            .catch(err => $('account-state').textContent = err.message);
}

// ---------------------------------------------------------------- editing

function openIssue(feature) {
    keepChanges();
    const p = feature.getProperties();
    const [lon, lat] = ol.proj.toLonLat(feature.getGeometry().getCoordinates());
    const issue = ISSUES[p.type] || {};
    currentIssue = {type: p.type, lat: lat, lon: lon, properties: p};

    selectedFeature = feature;
    redrawMarkers();
    // tapping a marker while typing must not leave the keyboard up
    if (narrow.matches && document.activeElement && document.activeElement.blur)
        document.activeElement.blur();
    $('edit').hidden = false;
    setSheet(true);
    // a place that already carries a value is not a missing tag, so do not call it one
    $('edit-title').textContent = p.maxheight
        ? 'maxheight is ' + p.maxheight : (issue.title || p.type);
    $('edit-hint').textContent = issue.hint || '';
    $('edit-hint').className = issue.warn ? 'hint warn' : 'hint';
    // what the ranking thinks of this place, so the size of the marker is explainable. The number
    // sits in the sentence it belongs to, with a chip of its own so it can be scanned for.
    const NB = {
        has_number: 'Another way under this bridge already carries a number.',
        only_default: 'Every other way under this bridge says there is nothing to sign.',
        untagged: 'The other ways under this bridge are untagged too.',
        none: 'No other road passes under this bridge.'
    };
    const pct = p.maxheight || p.p_sign == null ? null : Math.round(p.p_sign * 100);
    $('edit-odds').innerHTML = pct == null ? '' :
        '<b class="' + (p.p_sign >= 0.7 ? 'good' : p.p_sign >= 0.4 ? 'maybe' : 'weak') + '">'
        + pct + '%</b> chance of finding a sign here.'
        + (NB[p.neighbours] ? ' ' + NB[p.neighbours] : '');

    // for maxheight the way below the bridge comes first, it is the one that needs the tag
    const ways = [{id: p.way_id, name: p.way_name, cls: p.road_class}];
    if (p.other_way_id) ways.push({id: p.other_way_id, name: p.other_way_name, cls: p.other_road_class});
    const picker = $('way-picker');
    const roles = issue.roles || [];
    picker.replaceChildren(...ways.map((way, i) => {
        const label = (way.name || '(no name)') + ' [' + way.cls + ']'
            + (roles[i] ? ' - ' + roles[i] : '');
        const button = el('button', label, i ? '' : 'selected');
        button.onclick = () => {
            [...picker.children].forEach(c => c.classList.remove('selected'));
            button.classList.add('selected');
            loadWay(way.id);
        };
        return button;
    }));
    loadWay(ways[0].id);
}

function loadWay(wayId) {
    keepChanges();
    currentWay = null;
    $('way-state').className = 'hint warn';
    $('way-state').textContent = '';
    $('tags').textContent = 'loading way ' + wayId + ' ...';
    EDITABLE.forEach(key => {
        tagInput(key).value = '';
        tagInput(key).disabled = true;
    });
    updateSaveButton();
    // small link to the way itself, to look at it in OSM or fix something this app cannot do
    $('way-links').innerHTML = '<a href="' + settings.api + '/way/' + wayId + '" target="_blank">way '
        + wayId + '</a> &middot; <a href="' + settings.api + '/edit?editor=id&way=' + wayId
        + '#map=19/' + currentIssue.lat.toFixed(5) + '/' + currentIssue.lon.toFixed(5)
        + '" target="_blank">open in iD</a>';

    // "full" gives us the nodes with their coordinates as well, so we can draw the way
    osmRead('/api/0.6/way/' + wayId + '/full.json').then(json => {
        const way = json.elements.find(e => e.type === 'way');
        const line = geometryOf(json);
        waySource.clear();
        waySource.addFeature(new ol.Feature(new ol.geom.LineString(line.map(c => ol.proj.fromLonLat(c)))));
        // a photo of this issue has to be taken on this way, not on the one crossing it
        loadPhoto(currentIssue.lat, currentIssue.lon, line);
        const pending = pendingEdits.find(e => e.wayId === wayId) || {changes: {}};
        currentWay = {id: wayId, tags: Object.assign({}, way.tags)};
        EDITABLE.forEach(key => {
            const input = tagInput(key);
            input.value = key in pending.changes ? pending.changes[key] : (way.tags[key] || '');
            // What OSM already has is locked rather than disabled: a disabled field swallows the
            // click, and a wrong value - a maxheight=default that is not default at all - has to be
            // correctable. Clicking asks first, see below.
            input.readOnly = way.tags[key] !== undefined && !(key in pending.changes);
            input.disabled = false;
            input.classList.toggle('changed', key in pending.changes);
        });
        renderTags();

        // the crossing has to sit on the way, otherwise OSM has moved on since the import
        const offset = distanceToLine(currentIssue.lat, currentIssue.lon, line);
        if (offset > MAX_CROSSING_OFFSET) {
            $('way-state').className = 'hint warn';
            $('way-state').textContent = 'this spot is ' + offset.toFixed(1) + ' m away from the way '
                + 'as OSM has it now, so the imported data is out of date. Not editable here.';
            EDITABLE.forEach(key => tagInput(key).disabled = true);
            updateSaveButton();
            return;
        }
        updateSaveButton();
        // jump right to the tag this issue is about, or say that it is done already
        const wanted = (ISSUES[currentIssue.type] || {}).tag;
        if (wanted && way.tags[wanted] !== undefined) {
            $('way-state').className = 'hint note';
            $('way-state').textContent = 'OSM already has ' + wanted + '=' + way.tags[wanted]
                + ' here. GraphHopper reports it until its data is imported again. Click the field '
                + 'if that value is wrong.';
        } else if (wanted && !narrow.matches) {
            // not on a phone: there the keyboard would cover the panel we just opened
            tagInput(wanted).focus();
        }
    }).catch(err => {
        $('tags').textContent = '';
        waySource.clear();
        $('way-state').textContent = 'could not load way ' + wayId + ' from ' + settings.api + ': ' + err.message
            + (settings.isDevApi ? ' - the dev API has its own database, the real ways do not exist there' : '');
    });
}

/** the [lon, lat] pairs of the way in a "full" answer */
function geometryOf(json) {
    const way = json.elements.find(e => e.type === 'way');
    const coords = new Map(json.elements.filter(e => e.type === 'node').map(n => [n.id, [n.lon, n.lat]]));
    return way.nodes.map(id => coords.get(id));
}

/** the current tags of the way, read only - just to see what is already mapped */
function renderTags() {
    const keys = currentWay ? Object.keys(currentWay.tags).sort() : [];
    $('tags').replaceChildren(...keys.map(key => {
        const row = el('div', '', 'tag');
        row.append(el('span', key, 'key'), el('span', currentWay.tags[key]));
        return row;
    }));
    if (!keys.length) $('tags').textContent = 'this way has no tags';
}

// Values OSM accepts without a number - they say "nothing to sign here", which is a statement too
const NO_NUMBER = ['default', 'none', 'below_default', 'unsigned', 'no_sign', 'no_indications'];

/**
 * Checks a value the way GraphHopper will later read it, so a typo is caught here and not in a
 * routing result. Feet and inches are fine - that is what British signs say - but the dash between
 * them is not, and "4,8" or "13-9" would silently come out as 4 or as nothing at all.
 */
function checkTag(key, raw) {
    const v = raw.trim();
    if (!v) return null;
    if (key === 'bridge') {
        const known = ['yes', 'viaduct', 'aqueduct', 'boardwalk', 'cantilever', 'covered',
            'movable', 'trestle', 'low_water_crossing', 'no'];
        if (known.includes(v)) return null;
        // OSM values are lowercase words with underscores, anything else is a typo not a rare value
        if (!/^[a-z][a-z_]*$/.test(v))
            return {level: 'error', text: '"' + v + '" is not an OSM value, try yes'};
        return {level: 'warn', text: '"' + v + '" is an unusual bridge value'};
    }
    if (NO_NUMBER.includes(v)) return {level: 'ok', text: v + ': no number, but a statement'};
    if (v.includes(',')) return {level: 'error', text: 'use a dot for decimals, not a comma'};
    if (/^\d+\s*[-\u2013]\s*\d+$/.test(v))
        return {level: 'error', text: 'a sign reading 13-9 is written 13\'9" here'};

    let metres = null;
    let m = v.match(/^(\d+(?:\.\d+)?)\s*(m|t)?$/);
    if (m) metres = +m[1];
    else if ((m = v.match(/^(\d+)'(?:(\d+(?:\.\d+)?)")?$/)))
        metres = +m[1] * 0.3048 + (+(m[2] || 0)) * 0.0254;
    else if ((m = v.match(/^(\d+)\s*ft(?:\s*(\d+)\s*in)?$/)))
        metres = +m[1] * 0.3048 + (+(m[2] || 0)) * 0.0254;
    if (metres === null) return {level: 'error', text: 'GraphHopper cannot read "' + v + '"'};

    const unit = key === 'maxweight' ? 't' : 'm';
    // feet and inches go to OSM exactly as typed, this only says what they mean
    const shown = unit === 'm' && !/^\d+(\.\d+)?\s*m?$/.test(v)
        ? ' = ' + metres.toFixed(2) + ' m, stored as typed' : '';
    const [lo, hi] = key === 'maxweight' ? [0.5, 100] : [1.5, 10];
    if (metres < lo || metres > hi)
        return {level: 'warn', text: metres.toFixed(2) + ' ' + unit + ' - is that right?'};
    return shown ? {level: 'ok', text: shown.slice(3)} : null;
}

/** what the user typed, without empty fields and without values that are already in OSM */
function currentChanges() {
    const changes = {};
    if (currentWay)
        EDITABLE.forEach(key => {
            const value = tagInput(key).value.trim();
            if (value && value !== currentWay.tags[key]) changes[key] = value;
        });
    return changes;
}

function updateSaveButton() {
    const checks = EDITABLE
        .map(key => [key, checkTag(key, tagInput(key).value)])
        .filter(([, c]) => c);
    $('tag-check').innerHTML = checks
        .map(([key, c]) => '<span class="' + c.level + '">' + key + ': ' + c.text + '</span>')
        .join('');
    const broken = checks.some(([, c]) => c.level === 'error');
    $('save').disabled = broken || !Object.keys(currentChanges()).length;
}

EDITABLE.forEach(key => {
    const input = tagInput(key);
    // a value OSM has already is locked, see loadWay
    input.onmousedown = input.ontouchstart = e => {
        if (!input.readOnly) return;
        e.preventDefault();
        if (!confirm('OpenStreetMap already says ' + key + '=' + input.value + ' here. Change that value?'))
            return;
        input.readOnly = false;
        input.focus();
        input.select();
    };
    input.oninput = () => {
        if (!currentWay) return;
        input.classList.toggle('changed', input.value.trim() !== (currentWay.tags[key] || ''));
        updateSaveButton();
    };
});

/**
 * Put what is typed into the upload list. This also runs when another way or marker is opened, so
 * that a typed in value is not lost just because the button was not pressed.
 */
function keepChanges() {
    const changes = currentChanges();
    if (!Object.keys(changes).length) return;
    const p = currentIssue.properties;
    pendingEdits = pendingEdits.filter(e => e.wayId !== currentWay.id);
    pendingEdits.push({
        wayId: currentWay.id,
        name: (p.way_id === currentWay.id ? p.way_name : p.other_way_name) || '',
        changes: changes
    });
    currentWay = null; // taken over, do not add it a second time
    savePending();
}

$('save').onclick = () => {
    keepChanges();
    closeEdit();
    $('pending').scrollIntoView({block: 'nearest'});
};

function closeEdit() {
    keepChanges();
    selectedFeature = null;
    redrawMarkers();
    $('edit').hidden = true;
    waySource.clear();
    // nothing left to do in the sheet, give the map back
    if ($('pending').hidden) setSheet(false);
}

// ---------------------------------------------------------------- upload

function savePending() {
    localStorage.setItem('osm_pending', JSON.stringify(pendingEdits));
    renderPending();
    issueSource.changed();
}

function describe(edit) {
    return Object.keys(edit.changes).map(key => key + '=' + edit.changes[key]).join(', ');
}

function renderPending() {
    // keep the panel while it reports the last upload, otherwise the changeset link would vanish
    $('pending').hidden = !pendingEdits.length && !$('upload-state').innerHTML;
    $('pending-count').textContent = pendingEdits.length;
    $('pending-list').replaceChildren(...pendingEdits.map(edit => {
        const item = el('div', '', 'pending-item');
        const title = el('div', '', 'pending-title');
        title.append(el('span', (edit.name || '(no name)') + ' - way ' + edit.wayId));
        const remove = el('a', 'remove from list');
        remove.onclick = () => {
            pendingEdits = pendingEdits.filter(e => e !== edit);
            savePending();
        };
        title.append(remove);
        item.append(title, el('code', describe(edit)));
        return item;
    }));
}

function xmlEscape(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/**
 * The way as it comes from the API, with only the three allowed tags changed. The node references
 * are written back unchanged, so the geometry stays exactly as it is.
 */
function wayXml(way, changes, changesetId) {
    const tags = Object.assign({}, way.tags);
    Object.keys(changes).forEach(key => {
        if (!EDITABLE.includes(key)) throw new Error('refusing to change the tag ' + key);
        if (!changes[key]) throw new Error('refusing to write an empty value for ' + key);
        tags[key] = changes[key];
    });
    if (Object.keys(tags).length < Object.keys(way.tags).length)
        throw new Error('refusing to upload way ' + way.id + ', it would lose tags');

    return '<way id="' + way.id + '" version="' + way.version + '" changeset="' + changesetId + '">'
        + way.nodes.map(ref => '<nd ref="' + ref + '"/>').join('')
        + Object.keys(tags).map(key =>
            '<tag k="' + xmlEscape(key) + '" v="' + xmlEscape(tags[key]) + '"/>').join('')
        + '</way>';
}

$('upload').onclick = async () => {
    if (!pendingEdits.length) return;
    const comment = $('comment').value.trim(), source = $('source').value.trim();
    if (!comment || !source) {
        $('upload-state').textContent = 'please fill in both the changeset comment and the source';
        (comment ? $('source') : $('comment')).focus();
        return;
    }
    if (!confirm('Upload ' + pendingEdits.length + ' way(s) to ' + settings.api
        + (settings.isDevApi ? '' : ' (this changes the real OSM data!)') + '?\n\n'
        + pendingEdits.map(e => 'way ' + e.wayId + ': ' + describe(e)).join('\n')))
        return;

    const state = $('upload-state');
    state.innerHTML = '';
    $('upload').disabled = true;
    let changesetId = null;
    try {
        // the ways may have been edited by somebody else in the meantime, so we apply our changes
        // to the current version instead of the one we saw while editing
        state.textContent = 'loading current way versions ...';
        const ways = [];
        for (const edit of pendingEdits)
            ways.push({way: (await osmRead('/api/0.6/way/' + edit.wayId + '.json')).elements[0], changes: edit.changes});

        state.textContent = 'creating changeset ...';
        changesetId = await osmFetch('/api/0.6/changeset/create', {
            method: 'PUT',
            headers: {'Content-Type': 'text/xml'},
            body: '<osm><changeset><tag k="created_by" v="GraphHopper osm-issues"/>'
                + '<tag k="comment" v="' + xmlEscape(comment) + '"/>'
                + (source ? '<tag k="source" v="' + xmlEscape(source) + '"/>' : '')
                + '</changeset></osm>'
        });

        state.textContent = 'uploading ...';
        await osmFetch('/api/0.6/changeset/' + changesetId + '/upload', {
            method: 'POST',
            headers: {'Content-Type': 'text/xml'},
            body: '<osmChange version="0.6" generator="GraphHopper osm-issues"><modify>'
                + ways.map(w => wayXml(w.way, w.changes, changesetId)).join('') + '</modify></osmChange>'
        });
        await osmFetch('/api/0.6/changeset/' + changesetId + '/close', {method: 'PUT'});

        pendingEdits = [];
        uploadedWays = [...new Set(uploadedWays.concat(ways.map(w => w.way.id)))];
        localStorage.setItem('osm_uploaded', JSON.stringify(uploadedWays));
        $('comment').value = DEFAULT_COMMENT;
        localStorage.setItem('changeset_source', source);
        state.innerHTML = 'uploaded ' + ways.length + ' way(s) as <a href="' + settings.api
            + '/changeset/' + changesetId + '" target="_blank">changeset ' + changesetId + '</a>';
        savePending();
        issues.forget();
        load();
    } catch (err) {
        state.textContent = 'upload failed: ' + err.message;
        if (changesetId) // an open changeset would block the next upload
            osmFetch('/api/0.6/changeset/' + changesetId + '/close', {method: 'PUT'}).catch(() => {});
    } finally {
        $('upload').disabled = !auth.authenticated();
    }
};

$('discard').onclick = () => {
    if (confirm('Discard all ' + pendingEdits.length + ' change(s) that were not uploaded?')) {
        pendingEdits = [];
        savePending();
    }
};

// ---------------------------------------------------------------- start

$('login').onclick = () => {
    if (!settings.clientId) {
        $('account-state').textContent = 'no ' + (settings.isDevApi ? 'osmClientIdDev' : 'osmClientId')
            + ' in config.js. Register an OAuth 2 '
            + 'application at ' + settings.api + '/oauth2/applications with the redirect URI '
            + redirectUri + ' and the permission "modify the map", not confidential.';
        return;
    }
    auth.authenticate(err => {
        if (err) $('account-state').textContent = 'login failed: ' + (err.message || err);
        updateAccount();
    });
};
$('logout').onclick = () => {
    auth.logout();
    updateAccount();
};

$('comment').value = DEFAULT_COMMENT;
$('source').value = localStorage.getItem('changeset_source') || '';

initAuth();
// coming back from the OSM login page, osm-auth exchanges the code in the url for a token
if (/[?&](code|error)=/.test(location.search))
    auth.authenticate(err => {
        history.replaceState(null, '', redirectUri + location.hash);
        if (err) $('account-state').textContent = 'login failed: ' + (err.message || err);
        updateAccount();
    });
else
    updateAccount();
renderPending();
// no load() here: the map fires moveend once it has rendered for the first time, which loads
