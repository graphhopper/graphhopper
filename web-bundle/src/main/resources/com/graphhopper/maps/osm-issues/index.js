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
    html += '<div class="hint images">check the images: '
        + '<a href="https://www.mapillary.com/app/?lat=' + lat + '&lng=' + lon + '&z=19" target="_blank">Mapillary</a>'
        + '<a href="https://kartaview.org/map/@' + lat + ',' + lon + ',19z" target="_blank">KartaView</a></div>';
    document.getElementById('popup-content').innerHTML = html;
    popup.getElement().style.display = 'block';
    popup.setPosition(feature.getGeometry().getCoordinates());
});

document.getElementById('popup-closer').onclick = () => {
    popup.getElement().style.display = 'none';
    return false;
};

load();
