// Map app that shows OSM ways with missing bridge related tags (see /osm-issues) and lets you fix
// the tags right here: log in to OSM, edit the tags of a way, collect the changes and upload them
// as a single changeset.
const MIN_ZOOM = 11;

const COLORS = {
    missing_maxheight: '#e6194b',
    missing_maxweight: '#f58231',
    missing_bridge: '#4363d8'
};

const TITLES = {
    missing_maxheight: 'missing maxheight below a bridge',
    missing_maxweight: 'bridge without maxweight',
    missing_bridge: 'missing bridge tag'
};

// which tag the issue is about, so we can offer it right away
const TAGS = {
    missing_maxheight: 'maxheight',
    missing_maxweight: 'maxweight',
    missing_bridge: 'bridge'
};

// config.js holds the values for this deployment, what a user sets in the panel wins over it
const deployed = typeof osmIssuesConfig === 'object' ? osmIssuesConfig : {};

const settings = {
    get api() { return localStorage.getItem('osm_api') || 'https://www.openstreetmap.org'; },
    set api(v) { localStorage.setItem('osm_api', v); },
    get isDevApi() { return this.api.indexOf('dev.openstreetmap') >= 0; },
    get clientId() {
        return localStorage.getItem('osm_client_id_' + this.api)
            || (this.isDevApi ? deployed.osmClientIdDev : deployed.osmClientId) || '';
    },
    set clientId(v) { localStorage.setItem('osm_client_id_' + this.api, v); },
    get mapillaryToken() { return localStorage.getItem('mapillary_token') || deployed.mapillaryToken || ''; },
    set mapillaryToken(v) { localStorage.setItem('mapillary_token', v); },
    /** empty means the GraphHopper server is the one that serves this page */
    get gh() { return localStorage.getItem('gh_url') || deployed.graphhopperUrl || ''; },
    set gh(v) { localStorage.setItem('gh_url', v.replace(/\/$/, '')); }
};

// ?gh=http://host:8989 sets the GraphHopper server once, afterwards it is remembered
if (new URLSearchParams(location.search).get('gh'))
    settings.gh = new URLSearchParams(location.search).get('gh');

function preview(text) {
    return (text || '(empty response)').replace(/\s+/g, ' ').trim().slice(0, 120);
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
            + (res.headers.get('content-type') || 'unknown') + '): ' + preview(text) + hint);
        return json;
    })).catch(err => {
        // aborting the previous request while panning is normal, do not report it
        if (err.name !== 'AbortError') console.error('GraphHopper request failed:', url, err);
        throw err;
    });
}

const redirectUri = location.origin + location.pathname.replace(/\/?$/, '/');
let pendingEdits = JSON.parse(localStorage.getItem('osm_pending') || '[]');
let currentIssue = null, currentWay = null;

const $ = id => document.getElementById(id);

function savePending() {
    localStorage.setItem('osm_pending', JSON.stringify(pendingEdits));
    renderPending();
    issueSource.changed();
}

// ---------------------------------------------------------------- map + issues

const issueSource = new ol.source.Vector();
const issueLayer = new ol.layer.Vector({
    source: issueSource,
    style: feature => {
        const edited = pendingEdits.some(e => e.wayId === feature.get('way_id'));
        return new ol.style.Style({
            image: new ol.style.Circle({
                radius: 7,
                fill: new ol.style.Fill({color: COLORS[feature.get('type')] || '#000'}),
                stroke: new ol.style.Stroke({color: edited ? '#2e9e4f' : '#fff', width: edited ? 3 : 2})
            })
        });
    }
});

const map = new ol.Map({
    target: 'map',
    layers: [new ol.layer.Tile({source: new ol.source.OSM()}), issueLayer],
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
    ghFetch('/info').then(info => {
        map.getView().fit(ol.proj.transformExtent(info.bbox, 'EPSG:4326', 'EPSG:3857'),
            {size: map.getSize(), maxZoom: 16});
    }).catch(err => $('status').textContent = err.message);

function updateHash() {
    const view = map.getView(), center = ol.proj.toLonLat(view.getCenter());
    history.replaceState(null, '',
        '#' + Math.round(view.getZoom()) + '/' + center[1].toFixed(5) + '/' + center[0].toFixed(5));
}

const checkboxes = [...document.querySelectorAll('#issues input[type=checkbox]')];
checkboxes.forEach(cb => cb.addEventListener('change', () => load()));

// the road filter is remembered, it is a setting you pick once and keep
$('road-filter').value = localStorage.getItem('road_filter') || 'major';
$('road-filter').onchange = () => {
    localStorage.setItem('road_filter', $('road-filter').value);
    load();
};

let controller = null;

function load() {
    if (controller) controller.abort();
    const types = checkboxes.filter(cb => cb.checked).map(cb => cb.dataset.type);
    if (types.length === 0) {
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
    const e = ol.proj.transformExtent(map.getView().calculateExtent(map.getSize()), 'EPSG:3857', 'EPSG:4326');
    const bbox = e.map(v => v.toFixed(6)).join(',');
    const majorOnly = $('road-filter').value === 'major';
    $('status').textContent = 'loading ...';
    controller = new AbortController();
    ghFetch('/osm-issues?bbox=' + bbox + '&types=' + types.join(',') + '&major_only=' + majorOnly,
        {signal: controller.signal})
        .then(json => {
            issueSource.clear();
            issueSource.addFeatures(new ol.format.GeoJSON().readFeatures(json, {featureProjection: 'EPSG:3857'}));
            $('status').textContent = json.features.length + ' issue(s) in this view';
        })
        .catch(err => {
            if (err.name === 'AbortError') return;
            // keep whatever is on the map, the error message alone tells what happened
            $('status').textContent = err.message;
        });
}

let moveTimer = null;
map.on('moveend', () => {
    updateHash();
    // wait for the map to come to rest, otherwise every pan step starts and aborts a request
    clearTimeout(moveTimer);
    moveTimer = setTimeout(load, 300);
});

map.on('singleclick', evt => {
    const feature = map.forEachFeatureAtPixel(evt.pixel, f => f, {hitTolerance: 6});
    if (feature) openIssue(feature);
});

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

// we want a photo taken ~40m before the problem and looking at it, so that the bridge and its sign
// are in the picture instead of being right above the camera
function pickPhoto(photos, lat, lon) {
    let best = null;
    for (const p of photos) {
        if (isNaN(p.heading)) continue;
        const dist = distance(lat, lon, p.lat, p.lon);
        if (dist < 20 || dist > 70) continue;
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
        const i = p.lth_name.indexOf('/');
        return {
            lat: +p.lat, lon: +p.lng, heading: parseFloat(p.heading),
            date: (p.shot_date || p.date_added || '').substring(0, 10),
            thumb: 'https://' + p.lth_name.slice(0, i) + '.openstreetcam.org/' + p.lth_name.slice(i + 1),
            page: 'https://kartaview.org/details/' + p.sequence_id + '/' + p.sequence_index,
            source: 'KartaView'
        };
    }));
}

// mapillary cannot open the nearest image by coordinate, it needs the image id from its API
function mapillaryPhotos(lat, lon) {
    const token = settings.mapillaryToken;
    if (!token) return Promise.resolve([]);
    const params = new URLSearchParams({
        access_token: token, fields: 'id,compass_angle,computed_geometry,captured_at,thumb_1024_url',
        lat: lat, lng: lon, radius: '90', limit: '50'
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

function loadPhoto(lat, lon) {
    const req = ++photoRequest, container = $('photo'), links = $('edit-links');
    container.textContent = 'looking for a photo ...';
    Promise.all([
        kartaViewPhotos(lat, lon).catch(err => (console.error('KartaView lookup failed:', err), [])),
        mapillaryPhotos(lat, lon).catch(err => (console.error('Mapillary lookup failed:', err), []))
    ]).then(lists => {
        if (req !== photoRequest) return; // another marker was clicked in the meantime
        const best = pickPhoto([].concat(...lists), lat, lon);
        // the map links point at the photo, so that the map opens where the picture was taken
        const mapLat = best ? best.photo.lat : lat, mapLon = best ? best.photo.lon : lon;
        links.innerHTML = '<a href="https://www.mapillary.com/app/?lat=' + mapLat + '&lng=' + mapLon
            + '&z=19&trafficSign=all" target="_blank">Mapillary signs</a>'
            + '<a href="https://kartaview.org/map/@' + mapLat + ',' + mapLon + ',19z" target="_blank">KartaView map</a>';
        if (!best) {
            container.textContent = 'no street level photo looking at this spot';
            return;
        }
        const p = best.photo;
        container.innerHTML = '';
        const a = document.createElement('a');
        a.href = p.page;
        a.target = '_blank';
        const img = document.createElement('img');
        img.src = p.thumb;
        img.alt = p.source + ' photo';
        a.appendChild(img);
        container.appendChild(a);
        container.appendChild(document.createTextNode(
            p.source + ', ' + Math.round(best.dist) + ' m before the spot' + (p.date ? ', ' + p.date : '')));
    });
}

// ---------------------------------------------------------------- OSM login

// the OAuth 2 dance is done by the osm-auth library, the same one the iD editor uses
let auth = null;

function initAuth() {
    auth = osmAuth({
        url: settings.api,
        apiUrl: settings.api,
        client_id: settings.clientId,
        redirect_uri: redirectUri,
        scope: 'read_prefs write_api',
        singlepage: true,
        auto: false
    });
}

function login() {
    if (!settings.clientId) {
        showSettings(true);
        $('account-state').textContent = 'please enter an OAuth client id first';
        return;
    }
    auth.authenticate(err => {
        if (err) $('account-state').textContent = 'login failed: ' + (err.message || err);
        updateAccount();
    });
}

/** authenticated request against the OSM API, returns the response body as text */
function osmFetch(path, options) {
    options = Object.assign({headers: {}}, options);
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
    if (!loggedIn) {
        $('account-state').textContent = 'not logged in';
        return;
    }
    $('account-state').textContent = 'logged in';
    osmFetch('/api/0.6/user/details.json')
        .then(text => $('account-state').textContent = 'logged in as ' + JSON.parse(text).user.display_name)
        .catch(err => $('account-state').textContent = err.message);
}

// ---------------------------------------------------------------- editing

// the only tags this app may add or change. Nothing else is touched, tags are never deleted and
// the geometry of a way is uploaded exactly as it comes from the API.
const EDITABLE = ['maxheight', 'maxweight', 'bridge'];

function openIssue(feature) {
    const p = feature.getProperties();
    const coords = ol.proj.toLonLat(feature.getGeometry().getCoordinates());
    currentIssue = {type: p.type, lon: coords[0], lat: coords[1], properties: p};
    $('edit').hidden = false;
    $('edit-title').textContent = TITLES[p.type] || p.type;
    $('edit-hint').textContent = p.type === 'missing_bridge'
        ? 'these ways cross without a junction. Careful: usually only a part of one way is the bridge, '
        + 'so it has to be split first - that is easier in the iD editor.'
        : p.type === 'missing_maxheight'
            ? 'the way below the bridge needs the maxheight tag'
            : 'the bridge needs the maxweight tag';
    $('edit-hint').className = p.type === 'missing_bridge' ? 'hint warn' : 'hint';

    // for max_height the way below comes first, it is the one that needs the tag
    const ways = [{id: p.way_id, name: p.way_name, cls: p.road_class}];
    if (p.other_way_id) ways.push({id: p.other_way_id, name: p.other_way_name, cls: p.other_road_class});
    const picker = $('way-picker');
    picker.innerHTML = '';
    ways.forEach((w, i) => {
        const b = document.createElement('button');
        b.textContent = (w.name || '(no name)') + ' [' + w.cls + ']';
        b.onclick = () => {
            [...picker.children].forEach(c => c.classList.remove('selected'));
            b.classList.add('selected');
            loadWay(w.id);
        };
        picker.appendChild(b);
        if (i === 0) b.classList.add('selected');
    });
    loadWay(ways[0].id);
    loadPhoto(coords[1], coords[0]);
}

function loadWay(wayId) {
    currentWay = null;
    updateSaveButton();
    $('way-state').textContent = '';
    $('tags').textContent = 'loading way ' + wayId + ' ...';
    EDITABLE.forEach(key => {
        $('tag-' + key).value = '';
        $('tag-' + key).disabled = true;
    });
    // small link to the way itself, to look at it in OSM or fix something this app cannot do
    const at = '#map=19/' + currentIssue.lat.toFixed(5) + '/' + currentIssue.lon.toFixed(5);
    $('way-links').innerHTML = '<a href="' + settings.api + '/way/' + wayId + '" target="_blank">way '
        + wayId + '</a> &middot; <a href="' + settings.api + '/edit?editor=id&way=' + wayId + at
        + '" target="_blank">open in iD</a>';
    osmRead('/api/0.6/way/' + wayId + '.json').then(json => {
        const way = json.elements[0];
        currentWay = {id: wayId, tags: Object.assign({}, way.tags)};
        const pending = pendingEdits.find(e => e.wayId === wayId);
        EDITABLE.forEach(key => {
            const value = pending && key in pending.changes ? pending.changes[key] : way.tags[key];
            const input = $('tag-' + key);
            input.value = value === undefined ? '' : value;
            input.classList.toggle('changed', !!pending && key in pending.changes);
        });
        renderTags();
        EDITABLE.forEach(key => $('tag-' + key).disabled = false);
        updateSaveButton();
        // jump right to the tag this issue is about
        const wanted = TAGS[currentIssue.type];
        if (wanted && !way.tags[wanted]) $('tag-' + wanted).focus();
    }).catch(err => {
        $('tags').textContent = '';
        $('way-state').textContent = 'could not load way ' + wayId + ' from ' + settings.api
            + ': ' + err.message
            + (settings.isDevApi ? ' - the dev API has its own database, the real ways do not exist there' : '');
    });
}

/** the current tags of the way, read only - just to see what is already mapped */
function renderTags() {
    const box = $('tags');
    box.innerHTML = '';
    if (!currentWay) return;
    const keys = Object.keys(currentWay.tags).sort();
    if (!keys.length) box.textContent = 'this way has no tags';
    keys.forEach(key => {
        const row = document.createElement('div');
        row.className = 'tag';
        const k = document.createElement('span');
        k.className = 'key';
        k.textContent = key;
        const v = document.createElement('span');
        v.textContent = currentWay.tags[key];
        row.appendChild(k);
        row.appendChild(v);
        box.appendChild(row);
    });
}

EDITABLE.forEach(key => $('tag-' + key).oninput = () => {
    if (!currentWay) return;
    const value = $('tag-' + key).value.trim();
    $('tag-' + key).classList.toggle('changed', value !== (currentWay.tags[key] || ''));
    updateSaveButton();
});

/** a tag that is empty or unchanged is nothing to save */
function updateSaveButton() {
    $('save').disabled = !currentWay || !EDITABLE.some(key => {
        const value = $('tag-' + key).value.trim();
        return value && value !== currentWay.tags[key];
    });
}

$('save').onclick = () => {
    if (!currentWay) return;
    const changes = {};
    EDITABLE.forEach(key => {
        const value = $('tag-' + key).value.trim();
        // an empty field is not a change: this app does not delete tags
        if (value && value !== currentWay.tags[key]) changes[key] = value;
    });
    pendingEdits = pendingEdits.filter(e => e.wayId !== currentWay.id);
    if (Object.keys(changes).length) {
        const name = currentIssue.properties.way_id === currentWay.id
            ? currentIssue.properties.way_name : currentIssue.properties.other_way_name;
        pendingEdits.push({wayId: currentWay.id, name: name || '', changes: changes});
    }
    savePending();
    $('edit').hidden = true;
    $('pending').scrollIntoView({block: 'nearest'});
};

$('close-edit').onclick = () => $('edit').hidden = true;

// ---------------------------------------------------------------- upload

function renderPending() {
    // keep the panel while it reports the last upload, otherwise the changeset link would vanish
    $('pending').hidden = pendingEdits.length === 0 && !$('upload-state').innerHTML;
    $('pending-count').textContent = pendingEdits.length;
    const list = $('pending-list');
    list.innerHTML = '';
    pendingEdits.forEach(edit => {
        const item = document.createElement('div');
        item.className = 'pending-item';
        const title = document.createElement('div');
        title.textContent = (edit.name || '(no name)') + ' - way ' + edit.wayId + ' ';
        const remove = document.createElement('a');
        remove.textContent = 'remove';
        remove.onclick = () => {
            pendingEdits = pendingEdits.filter(e => e !== edit);
            savePending();
        };
        title.appendChild(remove);
        item.appendChild(title);
        Object.keys(edit.changes).forEach(key => {
            const code = document.createElement('code');
            code.textContent = edit.changes[key] === null ? '- ' + key : key + ' = ' + edit.changes[key];
            item.appendChild(code);
        });
        list.appendChild(item);
    });
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
        if (EDITABLE.indexOf(key) < 0) throw new Error('refusing to change the tag ' + key);
        if (!changes[key]) throw new Error('refusing to write an empty value for ' + key);
        tags[key] = changes[key];
    });
    if (Object.keys(tags).length < Object.keys(way.tags).length)
        throw new Error('refusing to upload way ' + way.id + ', it would lose tags');

    let xml = '<way id="' + way.id + '" version="' + way.version + '" changeset="' + changesetId + '">';
    way.nodes.forEach(ref => xml += '<nd ref="' + ref + '"/>');
    Object.keys(tags).forEach(key =>
        xml += '<tag k="' + xmlEscape(key) + '" v="' + xmlEscape(tags[key]) + '"/>');
    return xml + '</way>';
}

$('upload').onclick = async () => {
    if (!pendingEdits.length) return;
    const comment = $('comment').value.trim()
        || 'add missing maxheight/maxweight tags at bridges';
    const real = !settings.isDevApi;
    if (!confirm('Upload ' + pendingEdits.length + ' way(s) to ' + settings.api
        + (real ? ' (this changes the real OSM data!)' : '') + '?\n\n'
        + pendingEdits.map(e => 'way ' + e.wayId + ': '
            + Object.keys(e.changes).map(k => e.changes[k] === null ? '-' + k : k + '=' + e.changes[k]).join(', ')).join('\n')))
        return;

    const state = $('upload-state');
    state.innerHTML = '';
    $('upload').disabled = true;
    let changesetId = null;
    try {
        // the ways may have been edited by somebody else in the meantime, so we apply our changes
        // to the current version instead of uploading the version we saw when editing
        state.textContent = 'loading current way versions ...';
        const ways = [];
        for (const edit of pendingEdits) {
            const json = await osmRead('/api/0.6/way/' + edit.wayId + '.json');
            ways.push({way: json.elements[0], changes: edit.changes});
        }

        state.textContent = 'creating changeset ...';
        changesetId = await osmFetch('/api/0.6/changeset/create', {
            method: 'PUT',
            headers: {'Content-Type': 'text/xml'},
            body: '<osm><changeset><tag k="created_by" v="GraphHopper osm-issues"/>'
                + '<tag k="comment" v="' + xmlEscape(comment) + '"/></changeset></osm>'
        });

        state.textContent = 'uploading ...';
        const osmChange = '<osmChange version="0.6" generator="GraphHopper osm-issues"><modify>'
            + ways.map(w => wayXml(w.way, w.changes, changesetId)).join('') + '</modify></osmChange>';
        await osmFetch('/api/0.6/changeset/' + changesetId + '/upload', {
            method: 'POST',
            headers: {'Content-Type': 'text/xml'},
            body: osmChange
        });
        await osmFetch('/api/0.6/changeset/' + changesetId + '/close', {method: 'PUT'});

        pendingEdits = [];
        $('comment').value = '';
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
    if (!confirm('Discard all ' + pendingEdits.length + ' unsaved change(s)?')) return;
    pendingEdits = [];
    savePending();
};

// ---------------------------------------------------------------- settings and start

function showSettings(show) {
    $('settings').hidden = !show;
}

$('settings-toggle').onclick = e => {
    e.preventDefault();
    showSettings($('settings').hidden);
};

$('gh-url').value = settings.gh;
$('gh-url').onchange = () => {
    settings.gh = $('gh-url').value.trim();
    load();
};
$('api-select').value = settings.api;
$('client-id').value = settings.clientId;
$('mapillary-token').value = settings.mapillaryToken;
$('redirect-hint').textContent = 'Register an OAuth 2 application in your OSM settings with the '
    + 'redirect URI ' + redirectUri + ' and the permission "modify the map". OSM only allows https '
    + 'or http://127.0.0.1 as redirect, so open this page via 127.0.0.1 and not via localhost.';

$('api-select').onchange = () => {
    settings.api = $('api-select').value;
    $('client-id').value = settings.clientId;
    initAuth();
    updateAccount();
};
$('client-id').onchange = () => {
    settings.clientId = $('client-id').value.trim();
    initAuth();
};
$('mapillary-token').onchange = () => settings.mapillaryToken = $('mapillary-token').value.trim();
$('login').onclick = login;
$('logout').onclick = () => {
    auth.logout();
    updateAccount();
};

initAuth();
// coming back from the OSM login page, osm-auth exchanges the code in the url for a token
if (new URLSearchParams(location.search).has('code') || new URLSearchParams(location.search).has('error'))
    auth.authenticate(err => {
        history.replaceState(null, '', redirectUri + location.hash);
        if (err) $('account-state').textContent = 'login failed: ' + (err.message || err);
        updateAccount();
    });
else
    updateAccount();
renderPending();
load();
