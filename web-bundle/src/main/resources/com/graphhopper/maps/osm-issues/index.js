// Simple map app that shows OSM ways with missing bridge related tags, see /osm-issues
const MIN_ZOOM = 11;

const COLORS = {
    missing_maxheight: '#e6194b',
    missing_maxweight: '#f58231',
    missing_bridge: '#4363d8'
};

const TITLES = {
    missing_maxheight: 'missing max_height below a bridge',
    missing_maxweight: 'bridge without max_weight',
    missing_bridge: 'missing bridge tag (ways cross without junction)'
};

const source = new ol.source.Vector();
const layer = new ol.layer.Vector({
    source: source,
    style: feature => new ol.style.Style({
        image: new ol.style.Circle({
            radius: 7,
            fill: new ol.style.Fill({color: COLORS[feature.get('type')] || '#000'}),
            stroke: new ol.style.Stroke({color: '#fff', width: 2})
        })
    })
});

const popup = new ol.Overlay({
    element: document.getElementById('popup'),
    autoPan: {animation: {duration: 200}}
});

const map = new ol.Map({
    target: 'map',
    layers: [new ol.layer.Tile({source: new ol.source.OSM()}), layer],
    overlays: [popup],
    view: new ol.View(parseHash() || {center: [0, 0], zoom: 2})
});

// without a position in the url hash we show the area of the imported map
if (!parseHash())
    fetch('/info').then(res => res.json()).then(info => {
        map.getView().fit(ol.proj.transformExtent(info.bbox, 'EPSG:4326', 'EPSG:3857'),
            {size: map.getSize(), maxZoom: 16});
    }).catch(() => {});

function parseHash() {
    const parts = window.location.hash.replace('#', '').split('/');
    if (parts.length === 3 && !isNaN(parts[0]))
        return {center: ol.proj.fromLonLat([+parts[2], +parts[1]]), zoom: +parts[0]};
    return null;
}

function updateHash() {
    const view = map.getView();
    const center = ol.proj.toLonLat(view.getCenter());
    window.history.replaceState(null, '',
        '#' + Math.round(view.getZoom()) + '/' + center[1].toFixed(5) + '/' + center[0].toFixed(5));
}

const status = document.getElementById('status');
const checkboxes = [...document.querySelectorAll('#panel input[type=checkbox]')];
checkboxes.forEach(cb => cb.addEventListener('change', () => load()));

let controller = null;

function load() {
    if (controller) controller.abort();
    const types = checkboxes.filter(cb => cb.checked).map(cb => cb.dataset.type);
    if (map.getView().getZoom() < MIN_ZOOM || types.length === 0) {
        source.clear();
        status.textContent = types.length === 0 ? 'no issue type selected' : 'zoom in to load issues';
        return;
    }
    const e = ol.proj.transformExtent(map.getView().calculateExtent(map.getSize()), 'EPSG:3857', 'EPSG:4326');
    const bbox = [e[0], e[1], e[2], e[3]].map(v => v.toFixed(6)).join(',');
    status.textContent = 'loading ...';
    controller = new AbortController();
    fetch('/osm-issues?bbox=' + bbox + '&types=' + types.join(','), {signal: controller.signal})
        .then(res => res.json().then(json => {
            if (!res.ok) throw new Error(json.message || res.statusText);
            return json;
        }))
        .then(json => {
            source.clear();
            source.addFeatures(new ol.format.GeoJSON().readFeatures(json, {featureProjection: 'EPSG:3857'}));
            status.textContent = json.features.length + ' issue(s) in this view';
        })
        .catch(err => {
            if (err.name === 'AbortError') return;
            source.clear();
            status.textContent = err.message;
        });
}

map.on('moveend', () => {
    updateHash();
    load();
});

function wayLinks(id, name, roadClass, lat, lon) {
    const label = (name ? name : '(no name)') + ' [' + roadClass + ']';
    return '<div class="way">' + escapeHtml(label) + '<br/>'
        + '<a href="https://www.openstreetmap.org/way/' + id + '" target="_blank">way ' + id + '</a> &middot; '
        + '<a href="https://www.openstreetmap.org/edit?editor=id&way=' + id
        + '#map=19/' + lat.toFixed(5) + '/' + lon.toFixed(5) + '" target="_blank">edit</a></div>';
}

function escapeHtml(str) {
    return str.replace(/[&<>"]/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'})[c]);
}

map.on('singleclick', evt => {
    const feature = map.forEachFeatureAtPixel(evt.pixel, f => f, {hitTolerance: 6});
    if (!feature) {
        popup.getElement().style.display = 'none';
        return;
    }
    const p = feature.getProperties();
    const coord = ol.proj.toLonLat(feature.getGeometry().getCoordinates());
    const lon = coord[0], lat = coord[1];
    let html = '<h2 style="color:' + COLORS[p.type] + '">' + TITLES[p.type] + '</h2>';
    if (p.type === 'missing_maxheight') {
        html += '<div class="hint">add max_height to the road below:</div>' + wayLinks(p.way_id, p.way_name, p.road_class, lat, lon);
        html += '<div class="hint">bridge above:</div>' + wayLinks(p.other_way_id, p.other_way_name, p.other_road_class, lat, lon);
    } else if (p.type === 'missing_maxweight') {
        html += '<div class="hint">add max_weight to the bridge:</div>' + wayLinks(p.way_id, p.way_name, p.road_class, lat, lon);
    } else {
        html += '<div class="hint">these ways cross without a junction, so one of them needs a bridge or tunnel tag:</div>';
        html += wayLinks(p.way_id, p.way_name, p.road_class, lat, lon);
        html += wayLinks(p.other_way_id, p.other_way_name, p.other_road_class, lat, lon);
    }
    // Mapillary has no URL to open the nearest image, but it can show its traffic sign layer
    html += '<div class="hint images">check the images: '
        + '<a href="https://www.mapillary.com/app/?lat=' + lat + '&lng=' + lon + '&z=19&trafficSign=all" target="_blank">Mapillary signs</a>'
        + '<a href="https://kartaview.org/map/@' + lat + ',' + lon + ',19z" target="_blank">KartaView map</a></div>';
    html += '<div id="photo" class="hint">looking for a photo ...</div>';
    document.getElementById('popup-content').innerHTML = html;
    loadPhoto(lat, lon, document.getElementById('photo'));
    popup.getElement().style.display = 'block';
    popup.setPosition(feature.getGeometry().getCoordinates());
});

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
        const pLat = +p.lat, pLon = +p.lng, heading = parseFloat(p.heading);
        if (isNaN(heading)) continue;
        const dist = distance(lat, lon, pLat, pLon);
        if (dist < 20 || dist > 70) continue;
        const off = Math.abs((bearing(pLat, pLon, lat, lon) - heading + 540) % 360 - 180);
        if (off > 40) continue;
        const score = off + Math.abs(dist - 40);
        if (!best || score < best.score) best = {score: score, dist: dist, photo: p};
    }
    return best;
}

function photoUrl(name) {
    const i = name.indexOf('/');
    return 'https://' + name.slice(0, i) + '.openstreetcam.org/' + name.slice(i + 1);
}

let photoRequest = 0;

function loadPhoto(lat, lon, container) {
    const req = ++photoRequest;
    fetch('https://api.openstreetcam.org/1.0/list/nearby-photos/', {
        method: 'POST',
        body: new URLSearchParams({lat: lat, lng: lon, radius: '90'})
    }).then(res => res.json()).then(json => {
        if (req !== photoRequest) return; // another marker was clicked in the meantime
        const best = pickPhoto(json.currentPageItems || [], lat, lon);
        if (!best) {
            container.textContent = 'no KartaView photo looking at this spot';
            return;
        }
        const p = best.photo;
        const date = (p.shot_date || p.date_added || '').substring(0, 10);
        container.innerHTML = '<a href="https://kartaview.org/details/' + p.sequence_id + '/' + p.sequence_index
            + '" target="_blank"><img src="' + photoUrl(p.lth_name) + '" alt="KartaView photo"></a>'
            + 'KartaView, ' + Math.round(best.dist) + ' m before the spot' + (date ? ', ' + date : '');
    }).catch(() => {
        if (req === photoRequest) container.textContent = 'KartaView lookup failed';
    });
}

document.getElementById('popup-closer').onclick = () => {
    popup.getElement().style.display = 'none';
    return false;
};

load();
