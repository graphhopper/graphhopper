GHRequest = function(host) {
    this.minPathPrecision = 1;
    this.host = host;
    this.from = new GHInput("");
    this.to = new GHInput("");    
    this.vehicle = "car";
};

GHRequest.prototype.init = function(params) {    
    if(params.minPathPrecision)
        this.minPathPrecision = params.minPathPrecision;    
    
    if(params.vehicle)
        this.vehicle = params.vehicle;
    if(params.algoType)
        this.algoType = params.algoType;    
    if(params.algorithm)
        this.algorithm = params.algorithm;
}

GHRequest.prototype.doRequest = function(demoUrl, callback) {
    var encodedPolyline = true;
    var debug = false;
    var url = this.host + "/api/route?" + demoUrl + "&type=jsonp";
    // car
    url += "&vehicle=" + this.vehicle;
    // fastest or shortest
    if(this.algoType)
        url += "&algoType=" + this.algoType;
    // dijkstra, dijkstrabi, astar, astarbi
    if(this.algorithm)
        url += "&algorithm=" + this.algorithm;
    if (encodedPolyline)
        url += "&encodedPolyline=true";
    if (debug)
        url += "&debug=true";
    $.ajax({
        "url": url,
        "success": function(json) {
            // convert encoded polyline stuff to normal json
            if (encodedPolyline && json.route) {
                var tmpArray = decodePath(json.route.coordinates, true);
                json.route.coordinates = null;
                json.route.data = {
                    "type": "LineString",
                    "coordinates": tmpArray
                };
            }
            callback(json);
        },
        "error": function(err) {
            var msg = "API did not response! ";
            if (err && err.statusText && err.statusText != "OK")
                msg += err.statusText;
            
            console.log(msg + " " + JSON.stringify(err));            
            var details = "Error for " + url;
            var json = {
                "info" : {
                    "errors" : {
                        "message" : msg, 
                        "details" : details
                    }
                }
            };
            callback(json);
        },
        "type": "GET",
        "dataType": "jsonp"
    });
};

GHRequest.prototype.getInfo = function(success, error) {
    var url = this.host + "/api/info?type=jsonp";
    console.log(url);    
    return $.ajax({
        "url": url,
        "success": success,
        "error" : error,
        "timeout" : 3000,
        "type" : "GET",
        "dataType": 'jsonp'
    });
}

GHInput = function(str) {
    // either text or coordinates
    this.input = str;
    this.resolvedText = "";
    try {
        var index = str.indexOf(",");
        if (index >= 0) {
            this.lat = round(parseFloat(str.substr(0, index)));
            this.lng = round(parseFloat(str.substr(index + 1)));
            if(!isNaN(this.lat) && !isNaN(this.lng)) {
                this.input = this.toString();
            } else {
                this.lat = false;
                this.lng = false;
            }
        }
    } catch (ex) {
    }
};

GHInput.prototype.setCoord = function(lat, lng) {
    this.resolvedText = "";
    this.lat = round(lat);
    this.lng = round(lng);
    this.input = this.lat + "," + this.lng;
};

GHInput.prototype.toString = function() {
    if (this.lat && this.lng)
        return this.lat + "," + this.lng;
    return null;
};
