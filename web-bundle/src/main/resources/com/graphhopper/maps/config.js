const config = {
    routingApi: location.origin + '/',
    geocodingApi: '',
    defaultTiles: 'OpenStreetMap',
    keys: {
        graphhopper: "",
        maptiler: "missing_api_key",
        omniscale: "missing_api_key",
        thunderforest: "missing_api_key",
        kurviger: "missing_api_key"
    },
    routingGraphLayerAllowed: true,
    extraProfiles: {},
    request: {
        details: [
            'road_class',
            'road_environment',
            'max_speed',
            'average_speed',
        ],
        snapPreventions: ['ferry'],
    },
}