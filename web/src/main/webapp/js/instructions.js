var translate = require('./translate.js');
var messages = require('./messages.js');

var routeSegmentPopup = null;

function addInstruction(mapLayer, main, instr, instrIndex, lngLat, useMiles, debug) {
    var sign = instr.sign;
    if (instrIndex === 0)
        sign = "marker-icon-green";
    else
        sign = messages.getSignName(sign);
    var title = instr.text;
    if (instr.annotation_text) {
        if (!title)
            title = instr.annotation_text;
        else
            title = title + ", " + instr.annotation_text;
    }

    var pathname = window.location.pathname;
    var dirname = pathname.substring(0, pathname.lastIndexOf('/'));

    var instructionDiv = $("<tr class='instruction'/>");
    if (sign !== "continue") {
        var indiPic = "<img class='pic' style='vertical-align: middle' src='" +
            dirname + "/img/" + sign + ".png'/>";
        instructionDiv.append("<td class='instr_pic'>" + indiPic + "</td>");
    } else {
        instructionDiv.append("<td class='instr_pic'/>");
    }

    var tdVar = $("<td class='instr_title'>");
    tdVar.text(title);

    instructionDiv.append(tdVar);
    var distance = instr.distance;
    if (distance > 0) {
        instructionDiv.append("<td class='instr_distance'><span>" + translate.createDistanceString(distance, useMiles) + "<br/>" + translate.createTimeString(instr.time) + "</span></td>");
    }

    if (lngLat) {
        instructionDiv.click(function () {
            if (routeSegmentPopup)
                mapLayer.removeLayerFromMap(routeSegmentPopup);

            routeSegmentPopup = L.popup().setLatLng([lngLat[1], lngLat[0]]).setContent(title).openOn(mapLayer.getMap());

        });

        if (debug) {
            // Debug Turn Instructions more easily
            L.marker([lngLat[1], lngLat[0]], {
                icon: L.icon({
                    iconUrl: './img/marker-small-red.png',
                    // Made the instructions icon a bit bigger, as they are placed before the path details
                    iconSize: [16, 16]
                }),
                draggable: true
            }).addTo(mapLayer.getRoutingLayer()).bindPopup(title);
        }
    }
    main.append(instructionDiv);
}

module.exports.create = function (mapLayer, path, urlForHistory, request) {
    var instructionsElement = $("<table class='instructions'>");
    var debug = request.api_params.debug;

    var partialInstr = path.instructions.length > 100;
    var len = Math.min(path.instructions.length, 100);
    for (var m = 0; m < len; m++) {
        var instr = path.instructions[m];
        var lngLat = path.points.coordinates[instr.interval[0]];
        addInstruction(mapLayer, instructionsElement, instr, m, lngLat, request.useMiles, debug);
    }
    var infoDiv = $("<div class='instructions_info'>");
    infoDiv.append(instructionsElement);

    if (partialInstr) {
        var moreDiv = $("<button id='moreButton'>" + translate.tr("more_button") + "…</button>");
        moreDiv.click(function () {
            moreDiv.remove();
            for (var m = len; m < path.instructions.length; m++) {
                var instr = path.instructions[m];
                var lngLat = path.points.coordinates[instr.interval[0]];
                addInstruction(mapLayer, instructionsElement, instr, m, lngLat, request.useMiles);
            }
        });
        instructionsElement.append(moreDiv);
    }

    var hiddenDiv = $("<div id='routeDetails'/>");
    hiddenDiv.hide();

    var toggly = $("<button class='expandDetails'>+</button>");
    toggly.click(function () {
        hiddenDiv.toggle();
    });
    infoDiv.append(toggly);
    var infoStr = "points: " + path.points.coordinates.length;

    hiddenDiv.append("<span>" + infoStr + "</span>");

    var exportLink = $("#export-link a");
    exportLink.attr('href', urlForHistory);
    var osmRouteLink = $("<br/><a>view on OSM</a>");

    var osmVehicle = request.getVehicle();
    if (osmVehicle === "bicycle") {
        osmVehicle = "bike";
    } else if (osmVehicle.indexOf("truck") >= 0) {
        osmVehicle = "car";
    }

    osmRouteLink.attr("href", "http://www.openstreetmap.org/directions?engine=graphhopper_" + osmVehicle + "&route=" + encodeURIComponent(request.from.lat + "," + request.from.lng + ";" + request.to.lat + "," + request.to.lng));
    hiddenDiv.append(osmRouteLink);

    var osrmLink = $("<a>OSRM</a>");
    osrmLink.attr("href", "http://map.project-osrm.org/?z=13&loc=" + request.from + "&loc=" + request.to);
    hiddenDiv.append("<br/><span>Compare with: </span>");
    hiddenDiv.append(osrmLink);
    var googleLink = $("<a>Google</a> ");
    var addToGoogle = "";
    var addToBing = "";
    if (request.getVehicle().toUpperCase() === "FOOT") {
        addToGoogle = "&dirflg=w";
        addToBing = "&mode=W";
    } else if ((request.getVehicle().toUpperCase().indexOf("BIKE") >= 0) ||
        (request.getVehicle().toUpperCase() === "MTB")) {
        addToGoogle = "&dirflg=b";
    }

    googleLink.attr("href", "https://maps.google.com/?saddr=" + request.from + "&daddr=" + request.to + addToGoogle);
    hiddenDiv.append(googleLink);
    var bingLink = $("<a>Bing</a> ");
    bingLink.attr("href", "https://www.bing.com/maps/default.aspx?rtp=adr." + request.from + "~adr." + request.to + addToBing);
    hiddenDiv.append(bingLink);
    if (metaVersionInfo)
        hiddenDiv.append(metaVersionInfo);

    infoDiv.append(hiddenDiv);
    return infoDiv;
};
