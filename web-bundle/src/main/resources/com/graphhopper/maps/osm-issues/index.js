// Map app that shows OSM ways with missing bridge related tags (see /osm-issues) and lets you fix
// them right here: log in to OSM, edit the tags of a way, collect the changes and upload them as a
// single changeset.
const MIN_ZOOM = 11;

// everything that differs per issue type: marker color, what to tell the user and which tag it is about
const ISSUES = {
    missing_maxheight: {
        color: '#e6194b', tag: 'maxheight',
        title: 'missing maxheight below a bridge',
        hint: 'the way below the bridge needs the maxheight tag'
    },
    missing_maxweight: {
        color: '#f58231', tag: 'maxweight',
        title: 'bridge without maxweight',
        hint: 'the bridge needs the maxweight tag'
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

// config.js holds the values for this deployment, what a user sets in the panel wins over it
const deployed = typeof osmIssuesConfig === 'object' ? osmIssuesConfig : {};
const stored = (key, fallback) => localStorage.getItem(key) || fallback || '';

const settings = {
    get api() { return stored('osm_api', 'https://www.openstreetmap.org'); },
    set api(v) { localStorage.setItem('osm_api', v); },
    get isDevApi() { return this.api.includes('dev.openstreetmap'); },
    get clientId() {
        return stored('osm_client_id_' + this.api,
            this.isDevApi ? deployed.osmClientIdDev : deployed.osmClientId);
    },
    set clientId(v) { localStorage.setItem('osm_client_id_' + this.api, v); },
    get mapillaryToken() { return stored('mapillary_token', deployed.mapillaryToken); },
    set mapillaryToken(v) { localStorage.setItem('mapillary_token', v); },
    /** empty means the GraphHopper server is the one that serves this page */
    get gh() { return stored('gh_url', deployed.graphhopperUrl); },
    set gh(v) { localStorage.setItem('gh_url', v.replace(/\/$/, '')); }
};

// ?gh=http://host:8989 sets the GraphHopper server once, afterwards it is remembered
const ghParam = new URLSearchParams(location.search).get('gh');
if (ghParam) settings.gh = ghParam;

const redirectUri = location.origin + location.pathname.replace(/\/?$/, '/');
let pendingEdits = JSON.parse(localStorage.getItem('osm_pending') || '[]');
// ways we uploaded ourselves. GraphHopper keeps reporting them until it is imported again
let uploadedWays = JSON.parse(localStorage.getItem('osm_uploaded') || '[]');
let currentIssue = null, currentWay = null;

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

const issueLayer = new ol.layer.Vector({
    source: issueSource,
    style: feature => {
        const wayId = feature.get('way_id');
        const edited = pendingEdits.some(e => e.wayId === wayId);
        const done = uploadedWays.includes(wayId);
        return new ol.style.Style({
            image: new ol.style.Circle({
                radius: done ? 5 : 7,
                fill: new ol.style.Fill({
                    color: done ? '#bbb' : (ISSUES[feature.get('type')] || {}).color || '#000'
                }),
                stroke: new ol.style.Stroke({color: edited ? '#2e9e4f' : '#fff', width: edited ? 3 : 2})
            })
        });
    }
});

const map = new ol.Map({
    target: 'map',
    layers: [new ol.layer.Tile({source: new ol.source.OSM()}), wayLayer, issueLayer],
    view: new ol.View(parseHash() || {center: [0, 0], zoom: 2})
});

function parseHash() {
    const parts = location.hash.replace('#', '').split('/');
    if (parts.length === 3 && !isNaN(parts[0]))
        return {center: ol.proj.fromLonLat([+parts[2], +parts[1]]), zoom: +parts[0]};
    return null;
}

// without a position in the url hash we show the area of the imported map
if (!parseHash())
    ghFetch('/info').then(info => map.getView().fit(
        ol.proj.transformExtent(info.bbox, 'EPSG:4326', 'EPSG:3857'), {size: map.getSize(), maxZoom: 16}
    )).catch(err => $('status').textContent = err.message);

const checkboxes = [...document.querySelectorAll('#issues input[type=checkbox]')];
checkboxes.forEach(cb => cb.onchange = () => load());

// the road filter is remembered, it is a setting you pick once and keep
$('road-filter').value = stored('road_filter', 'major');
$('road-filter').onchange = () => {
    localStorage.setItem('road_filter', $('road-filter').value);
    load();
};

let controller = null;

function load() {
    if (controller) controller.abort();
    const types = checkboxes.filter(cb => cb.checked).map(cb => cb.dataset.type);
    if (!types.length) {
        // unchecking every type is a deliberate action, there the markers should go away at once
        issueSource.clear();
        $('status').textContent = 'no issue type selected';
        return;
    }
    // the old markers stay on the map until the new ones are there, so panning does not blank it
    if (map.getView().getZoom() < MIN_ZOOM) {
        $('status').textContent = 'zoom in to load issues';
        return;
    }
    const extent = ol.proj.transformExtent(map.getView().calculateExtent(map.getSize()), 'EPSG:3857', 'EPSG:4326');
    $('status').textContent = 'loading ...';
    controller = new AbortController();
    ghFetch('/osm-issues?bbox=' + extent.map(v => v.toFixed(6)).join(',')
        + '&types=' + types.join(',') + '&major_only=' + ($('road-filter').value === 'major'),
        {signal: controller.signal})
        .then(json => {
            issueSource.clear();
            issueSource.addFeatures(new ol.format.GeoJSON().readFeatures(json, {featureProjection: 'EPSG:3857'}));
            $('status').textContent = json.features.length + ' issue(s) in this view';
        })
        .catch(err => {
            // keep whatever is on the map, the error message alone tells what happened
            if (err.name !== 'AbortError') $('status').textContent = err.message;
        });
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

map.on('singleclick', evt => {
    const feature = map.forEachFeatureAtPixel(evt.pixel, f => f,
        {hitTolerance: 6, layerFilter: layer => layer === issueLayer});
    if (feature) openIssue(feature);
});

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

// We want a photo taken ~40m before the problem and looking at it, so that the bridge and its sign
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
            page: 'https://kartaview.org/details/' + p.sequence_id + '/' + p.sequence_index,
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

function loadPhoto(lat, lon, line) {
    const req = ++photoRequest;
    $('photo').textContent = 'looking for a photo ...';
    const failed = name => err => (console.error(name + ' lookup failed:', err), []);
    Promise.all([
        panoramaxPhotos(lat, lon).catch(failed('Panoramax')),
        kartaViewPhotos(lat, lon).catch(failed('KartaView')),
        mapillaryPhotos(lat, lon).catch(failed('Mapillary'))
    ]).then(lists => {
        if (req !== photoRequest) return; // another marker was clicked in the meantime
        const best = pickPhoto(lists.flat(), lat, lon, line);
        const photo = best && best.photo;
        // the map links point at the photo, so that the map opens where the picture was taken
        const mapLat = photo ? photo.lat : lat, mapLon = photo ? photo.lon : lon;
        $('edit-links').innerHTML =
            '<a href="https://www.mapillary.com/app/?lat=' + mapLat + '&lng=' + mapLon
            + '&z=19&trafficSign=all" target="_blank">Mapillary</a>'
            + '<a href="https://kartaview.org/map/@' + mapLat + ',' + mapLon
            + ',19z" target="_blank">KartaView</a>';
        if (!photo) {
            $('photo').textContent = 'no street level photo on this way looking at this spot';
            return;
        }
        const link = el('a');
        link.href = photo.page;
        link.target = '_blank';
        const img = el('img');
        img.src = photo.thumb;
        img.alt = photo.source + ' photo';
        link.append(img);
        $('photo').replaceChildren(link, photo.source + ', ' + Math.round(best.dist)
            + ' m before the spot' + (photo.date ? ', ' + photo.date : ''));
        // whoever reads the value off this photo should say so in the changeset
        if (!$('source').value) $('source').value = photo.source;
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

/** authenticated request against the OSM API, returns the response body as text */
function osmFetch(path, options) {
    return auth.fetch(settings.api + path, options).then(res => res.text().then(text => {
        if (!res.ok) throw new Error(res.status + ' ' + (text || res.statusText));
        return text;
    }));
}

/** reading a way needs no login, so the tags can be inspected before logging in */
function osmRead(path) {
    return fetch(settings.api + path).then(res => {
        if (!res.ok) throw new Error(res.status + ' ' + res.statusText);
        return res.json();
    });
}

function updateAccount() {
    const loggedIn = auth && auth.authenticated();
    $('login').hidden = loggedIn;
    $('logout').hidden = !loggedIn;
    $('upload').disabled = !loggedIn;
    $('account-state').textContent = loggedIn ? 'logged in' : 'not logged in';
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

    $('edit').hidden = false;
    $('edit-title').textContent = issue.title || p.type;
    $('edit-hint').textContent = issue.hint || '';
    $('edit-hint').className = issue.warn ? 'hint warn' : 'hint';

    // for maxheight the way below the bridge comes first, it is the one that needs the tag
    const ways = [{id: p.way_id, name: p.way_name, cls: p.road_class}];
    if (p.other_way_id) ways.push({id: p.other_way_id, name: p.other_way_name, cls: p.other_road_class});
    const picker = $('way-picker');
    picker.replaceChildren(...ways.map((way, i) => {
        const button = el('button', (way.name || '(no name)') + ' [' + way.cls + ']', i ? '' : 'selected');
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
        const coords = new Map(json.elements.filter(e => e.type === 'node').map(n => [n.id, [n.lon, n.lat]]));
        const line = way.nodes.map(id => coords.get(id));
        waySource.clear();
        waySource.addFeature(new ol.Feature(new ol.geom.LineString(line.map(c => ol.proj.fromLonLat(c)))));
        // a photo of this problem has to be taken on this way, not on the one crossing it
        loadPhoto(currentIssue.lat, currentIssue.lon, line);
        const pending = pendingEdits.find(e => e.wayId === wayId) || {changes: {}};
        currentWay = {id: wayId, tags: Object.assign({}, way.tags)};
        EDITABLE.forEach(key => {
            const input = tagInput(key);
            input.value = key in pending.changes ? pending.changes[key] : (way.tags[key] || '');
            // what OSM already has is shown but not editable, this app only adds missing tags
            input.disabled = way.tags[key] !== undefined && !(key in pending.changes);
            input.classList.toggle('changed', key in pending.changes);
        });
        renderTags();
        updateSaveButton();
        // jump right to the tag this issue is about, or say that it is done already
        const wanted = (ISSUES[currentIssue.type] || {}).tag;
        if (wanted && way.tags[wanted] !== undefined) {
            $('way-state').className = 'hint';
            $('way-state').textContent = 'OSM already has ' + wanted + '=' + way.tags[wanted]
                + ' here. GraphHopper reports it until its data is imported again.';
        } else if (wanted) {
            tagInput(wanted).focus();
        }
    }).catch(err => {
        $('tags').textContent = '';
        waySource.clear();
        $('way-state').textContent = 'could not load way ' + wayId + ' from ' + settings.api + ': ' + err.message
            + (settings.isDevApi ? ' - the dev API has its own database, the real ways do not exist there' : '');
    });
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
    $('save').disabled = !Object.keys(currentChanges()).length;
}

EDITABLE.forEach(key => tagInput(key).oninput = () => {
    if (!currentWay) return;
    tagInput(key).classList.toggle('changed', tagInput(key).value.trim() !== (currentWay.tags[key] || ''));
    updateSaveButton();
});

/**
 * Put what is typed into the upload list. This also runs when another way or marker is opened, so
 * that a typed in value is not lost just because the button was not pressed.
 */
function keepChanges() {
    const changes = currentChanges();
    if (!Object.keys(changes).length) return false;
    const p = currentIssue.properties;
    pendingEdits = pendingEdits.filter(e => e.wayId !== currentWay.id);
    pendingEdits.push({
        wayId: currentWay.id,
        name: (p.way_id === currentWay.id ? p.way_name : p.other_way_name) || '',
        changes: changes
    });
    currentWay = null; // taken over, do not add it a second time
    savePending();
    return true;
}

$('save').onclick = () => {
    keepChanges();
    closeEdit();
    $('pending').scrollIntoView({block: 'nearest'});
};

$('close-edit').onclick = closeEdit;

function closeEdit() {
    keepChanges();
    $('edit').hidden = true;
    waySource.clear();
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

// ---------------------------------------------------------------- settings panel and start

$('settings-toggle').onclick = e => {
    e.preventDefault();
    $('settings').hidden = !$('settings').hidden;
};
const DEFAULT_COMMENT = 'add missing maxheight/maxweight tags at bridges';
$('comment').value = DEFAULT_COMMENT;
$('source').value = stored('changeset_source', '');
$('gh-url').value = settings.gh;
$('gh-url').onchange = () => {
    settings.gh = $('gh-url').value.trim();
    load();
};
$('api-select').value = settings.api;
$('api-select').onchange = () => {
    settings.api = $('api-select').value;
    $('client-id').value = settings.clientId;
    initAuth();
    updateAccount();
};
$('client-id').value = settings.clientId;
$('client-id').onchange = () => {
    settings.clientId = $('client-id').value.trim();
    initAuth();
};
$('mapillary-token').value = settings.mapillaryToken;
$('mapillary-token').onchange = () => settings.mapillaryToken = $('mapillary-token').value.trim();
$('redirect-hint').textContent = 'Register an OAuth 2 application in your OSM settings with the '
    + 'redirect URI ' + redirectUri + ' and the permission "modify the map". OSM only allows https '
    + 'or http://127.0.0.1 as redirect, so open this page via 127.0.0.1 and not via localhost.';

$('login').onclick = () => {
    if (!settings.clientId) {
        $('settings').hidden = false;
        $('account-state').textContent = 'please enter an OAuth client id first';
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
load();
