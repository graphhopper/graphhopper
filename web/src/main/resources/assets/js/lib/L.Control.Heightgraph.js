L.Control.Heightgraph = L.Control.extend({
    options: {
        position: "bottomright",
        width: 800,
        height: 125,
        margins: {
            top: 20,
            right: 50,
            bottom: 25,
            left: 50
        },
        mappings: undefined,
        expand: true
    },
    onAdd: function(map) {
        var opts = this.options;
        var container = this._container = L.DomUtil.create('div', 'heightgraph');
        L.DomEvent.disableClickPropagation(container);
        var buttonContainer = this._button = L.DomUtil.create('div', "heightgraph-toggle", container);
        var link = L.DomUtil.create('a', "heightgraph-toggle-icon", buttonContainer);
        var closeButton = this._closeButton = L.DomUtil.create('a', "heightgraph-close-icon", container);
        this._showState = false;
        this._initToggle();
        this._margin = this.options.margins;
        this._width = this.options.width - this._margin.left - this._margin.right;
        this._height = this.options.height - this._margin.top - this._margin.bottom;
        this._mappings = this.options.mappings;
        this._svgWidth = this._width - this._margin.left - this._margin.right;
        this._svgHeight = this._height - this._margin.top - this._margin.bottom;
        var svg = this._svg = d3.select(this._container)
            .append("svg")
            .attr("class", "heightgraph-container")
            .attr("width", this._svgWidth + this._margin.left + this._margin.right)
            .attr("height", this._svgHeight + this._margin.top + this._margin.bottom)
            .append("g")
            .attr("transform", "translate(" + this._margin.left + "," + this._margin.top + ")");
        return container;
    },
    onRemove: function(map) {
        this._container = null;
        this._svg = undefined;
    },
    /**
     * add Data from geoJson and call all functions
     * @param {Object} data
     */
    addData: function(data) {
        if (this._svg !== undefined) {
            this._svg.selectAll("*")
                .remove();
        }
        this._data = data;
        this._selection();
        this._prepareData();
        this._computeStats();
        this._appendScales();
        this._appendGrid();
        this._createChart(this._selectedOption);
        if (this._data.length > 1) this._createSelectionBox();
        if (this.options.expand) this._expand();
    },
    _initToggle: function() {
        if (!L.Browser.touch) {
            L.DomEvent.disableClickPropagation(this._container);
        } else {
            L.DomEvent.on(this._container, 'click', L.DomEvent.stopPropagation);
        }
        if (!L.Browser.android) {
            L.DomEvent.on(this._button, 'click', this._expand, this);
            L.DomEvent.on(this._closeButton, 'click', this._expand, this);
        }
    },
    _dragHandler: function() {
        //we don´t want map events to occur here
        d3.event.preventDefault();
        d3.event.stopPropagation();
        this._gotDragged = true;
        this._drawDragRectangle();
    },
    /**
     * Draws the currently dragged rectabgle over the chart.
     */
    _drawDragRectangle: function() {
        if (!this._dragStartCoords) {
            return;
        }
        var dragEndCoords = this._dragCurrentCoords = d3.mouse(this._background.node());
        var x1 = Math.min(this._dragStartCoords[0], dragEndCoords[0]),
            x2 = Math.max(this._dragStartCoords[0], dragEndCoords[0]);
        if (!this._dragRectangle && !this._dragRectangleG) {
            var g = d3.select(this._container)
                .select("svg")
                .select("g");
            this._dragRectangleG = g.append("g");
            this._dragRectangle = this._dragRectangleG.append("rect")
                .attr("width", x2 - x1)
                .attr("height", this._svgHeight)
                .attr("x", x1)
                .attr('class', 'mouse-drag')
                .style("fill", "grey")
                .style("opacity", 0.5)
                .style("pointer-events", "none");
        } else {
            this._dragRectangle.attr("width", x2 - x1)
                .attr("x", x1);
        }
    },
    /**
     * Removes the drag rectangle and zoms back to the total extent of the data.
     */
    _resetDrag: function() {
        if (this._dragRectangleG) {
            this._dragRectangleG.remove();
            this._dragRectangleG = null;
            this._dragRectangle = null;
            this._map.fitBounds(bounds);
        }
    },
    /**
     * Handles end of dragg operations. Zooms the map to the selected items extent.
     */
    _dragEndHandler: function() {
        if (!this._dragStartCoords || !this._gotDragged) {
            this._dragStartCoords = null;
            this._gotDragged = false;
            this._resetDrag();
            return;
        }
        var item1 = this._findItemForX(this._dragStartCoords[0]),
            item2 = this._findItemForX(this._dragCurrentCoords[0]);
        this._fitSection(item1, item2);
        this._dragStartCoords = null;
        this._gotDragged = false;
    },
    _dragStartHandler: function() {
        d3.event.preventDefault();
        d3.event.stopPropagation();
        this._gotDragged = false;
        this._dragStartCoords = d3.mouse(this._background.node());
    },
    /**
     * Make the map fit the route section between given indexes. 
     */
    _fitSection: function(index1, index2) {
        var start = Math.min(index1, index2),
            end = Math.max(index1, index2);
        var ext = [this._areasFlattended[start].latlng, this._areasFlattended[end].latlng];
        this._map.fitBounds(ext);
    },
    /**
     * Expand container when button clicked and shrink when close-Button clicked
     */
    _expand: function() {
        if (!this._showState) {
            d3.select(this._button)
                .style("display", "none");
            d3.select(this._container)
                .selectAll('svg')
                .style("display", "block");
            d3.select(this._closeButton)
                .style("display", "block");
        } else {
            d3.select(this._button)
                .style("display", "block");
            d3.select(this._container)
                .selectAll('svg')
                .style("display", "none");
            d3.select(this._closeButton)
                .style("display", "none");
        }
        this._showState = !this._showState;
    },
    /**
     * reacts on changes in selection box and updates heightprofile
     * @param {integer} selectedOption
     */
    _selection: function(selectedOption) {
        this._selectedOption = selectedOption === undefined ? 0 : this._selectedOption;
        if (selectedOption !== undefined) {
            if (this._svg !== undefined) {
                // remove areas
                this._svg.selectAll("path.area")
                    .remove();
                // remove top border
                this._svg.selectAll("path.border-top")
                    .remove();
                // remove legend
                this._svg.selectAll(".legend")
                    .remove();
                // remove horizontal Line
                this._svg.selectAll(".lineSelection")
                    .remove();
                this._svg.selectAll(".horizontalLine")
                    .remove();
                this._svg.selectAll(".horizontalLineText")
                    .remove();
                this._createChart(selectedOption);
            }
        }
    },
    /**
     * Creates a random int between 0 and max
     */
    _randomNumber: function(max) {
        return Math.round((Math.random() * (max - 0) + 0));
    },
    _d3ColorCategorical: [{
        "name": "schemeAccent"
    }, {
        "name": "schemeDark2"
    }, {
        "name": "schemeSet2"
    }, {
        "name": "schemeSet1"
    }, {
        "name": "schemeCategory10"
    }, {
        "name": "schemeSet3"
    }, {
        "name": "schemePaired"
    }, {
        "name": "schemeCategory20"
    }, {
        "name": "schemeCategory20b"
    }, {
        "name": "schemeCategory20c"
    }],
    /**
     * Prepares the data needed for the height graph
     */
    _prepareData: function() {
        this._profile = {};
        this._profile.coordinates = [];
        this._profile.elevations = [];
        this._profile.cumDistances = [];
        this._profile.cumDistances.push(0);
        this._profile.blocks = [];
        var data = this._data;
        var categorical = [];
        var colorScale;
        if (this._mappings === undefined) {
            var randomNumber = this._randomNumber(categorical.length);
            colorScale = d3.scaleOrdinal(d3[this._d3ColorCategorical[randomNumber].name]);
        }
        for (var y = 0; y < data.length; y++) {
            var cumDistance = 0;
            this._profile.blocks[y] = {};
            this._profile.blocks[y].info = {
                id: y,
                text: data[y].properties.summary
            };
            this._profile.blocks[y].distances = [];
            this._profile.blocks[y].attributes = [];
            this._profile.blocks[y].geometries = [];
            this._profile.blocks[y].legend = {};
            var i, cnt = 0,
                usedColors = {};
            for (i = 0; i < data[y].features.length; i++) {
                // data is redundant in every elemtent of data which is why we collect it once
                var altitude, ptA, ptB, ptDistance,
                    geometry = [];
                var coordsLength = data[y].features[i].geometry.coordinates.length;
                // save attribute types related to blocks
                var attributeType = data[y].features[i].properties.attributeType;
                // check if mappings are defined, otherwise random colors
                var text, color;
                if (this._mappings === undefined) {
                    if (attributeType in usedColors) {
                        text = attributeType;
                        color = usedColors[attributeType];
                    } else {
                        text = attributeType;
                        color = colorScale(i);
                        usedColors[attributeType] = color;
                    }
                } else {
                    text = this._mappings[data[y].properties.summary][attributeType].text;
                    color = this._mappings[data[y].properties.summary][attributeType].color;
                }
                var attribute = {
                    type: attributeType,
                    text: text,
                    color: color
                };
                this._profile.blocks[y].attributes.push(attribute);
                // add to legend
                if (!(attributeType in this._profile.blocks[y].legend)) {
                    this._profile.blocks[y].legend[attributeType] = attribute;
                }
                for (var j = 0; j < coordsLength; j++) {
                    ptA = new L.LatLng(data[y].features[i].geometry.coordinates[j][1], data[y].features[i].geometry.coordinates[j][0]);
                    altitude = data[y].features[i].geometry.coordinates[j][2];
                    // add elevations, coordinates and point distances only once
                    // last point in feature is first of next which is why we have to juggle with indices
                    if (j < coordsLength - 1) {
                        ptB = new L.LatLng(data[y].features[i].geometry.coordinates[j + 1][1], data[y].features[i].geometry.coordinates[j + 1][0]);
                        ptDistance = ptA.distanceTo(ptB) / 1000;
                        // calculate distances of specific block
                        cumDistance += ptDistance;
                        if (y === 0) {
                            this._profile.elevations.push(altitude);
                            this._profile.coordinates.push(ptA);
                            this._profile.cumDistances.push(cumDistance);
                        }
                        cnt += 1;
                    } else if (j == coordsLength - 1 && i == data[y].features.length - 1) {
                        if (y === 0) {
                            this._profile.elevations.push(altitude);
                            this._profile.coordinates.push(ptB);
                        }
                        cnt += 1;
                    }
                    // save the position which corresponds to the distance along the route. 
                    var position;
                    if (j == coordsLength - 1 && i < data[y].features.length - 1) {
                        position = this._profile.cumDistances[cnt];
                    } else {
                        position = this._profile.cumDistances[cnt - 1];
                    }
                    geometry.push({
                        altitude: altitude,
                        position: position,
                        x: ptA.lng,
                        y: ptA.lat,
                        latlng: ptA,
                        type: text,
                        areaIdx: i
                    });
                }
                this._profile.blocks[y].distances.push(cumDistance);
                this._profile.blocks[y].geometries.push(geometry);
            }
            if (y == data.length - 1) {
                this._profile.totalDistance = cumDistance;
            }
        }
    },
    /**
     * Creates a list with four x,y coords and other important infos for the bars drawn with d3
     */
    _computeStats: function() {
        var max = this._profile.maxElevation = d3.max(this._profile.elevations);
        var min = this._profile.minElevation = d3.min(this._profile.elevations);
        var quantile = this._profile.elevationQuantile = d3.quantile(this._profile.elevations, 0.75);
        this._profile.yElevationMin = (quantile < (min + min / 10)) ? (min - max / 5 < 0 ? 0 : min - max / 5) : min - (max / 10);
        this._profile.yElevationMax = quantile > (max - max / 10) ? max + (max / 3) : max;
    },
    /**
     * Creates a marker on the map while hovering
     * @param {Object} ll: actual coordinates of the route
     * @param {float} alt: height
     * @param {string} type: type of element
     */
    _showMarker: function(ll, height, type) {
        var layerpoint = this._map.latLngToLayerPoint(ll);
        var normalizedY = layerpoint.y - 75;
        if (!this._mouseHeightFocus) {
            var heightG = d3.select(".leaflet-overlay-pane svg")
                .append("g");
            this._mouseHeightFocus = heightG.append('svg:line')
                .attr('class', 'height-focus line')
                .attr('x2', '0')
                .attr('y2', '0')
                .attr('x1', '0')
                .attr('y1', '0');
            this._mouseHeightFocusLabel = heightG.append("g")
                .attr('class', 'height-focus label');
            this._mouseHeightFocusLabelRect = this._mouseHeightFocusLabel.append("rect")
                .attr('class', 'bBox');
            this._mouseHeightFocusLabelTextElev = this._mouseHeightFocusLabel.append("text")
                .attr('class', 'tspan');
            this._mouseHeightFocusLabelTextType = this._mouseHeightFocusLabel.append("text")
                .attr('class', 'tspan');
            var pointG = this._pointG = heightG.append("g")
                .attr('class', 'height-focus circle');
            pointG.append("svg:circle")
                .attr("r", 5)
                .attr("cx", 0)
                .attr("cy", 0)
                .attr("class", "height-focus circle-lower");
        }
        this._mouseHeightFocusLabel.style("display", "block");
        this._mouseHeightFocus.attr("x1", layerpoint.x)
            .attr("x2", layerpoint.x)
            .attr("y1", layerpoint.y)
            .attr("y2", normalizedY)
            .style("display", "block");
        this._pointG.attr("transform", "translate(" + layerpoint.x + "," + layerpoint.y + ")")
            .style("display", "block");
        this._mouseHeightFocusLabelRect.attr("x", layerpoint.x + 3)
            .attr("y", normalizedY)
            .attr("class", 'bBox');
        this._mouseHeightFocusLabelTextElev.attr("x", layerpoint.x + 5)
            .attr("y", normalizedY + 12)
            .text(height + " m")
            .attr("class", "tspan");
        this._mouseHeightFocusLabelTextType.attr("x", layerpoint.x + 5)
            .attr("y", normalizedY + 24)
            .text(type)
            .attr("class", "tspan");
        var maxWidth = this._dynamicBoxSize('text.tspan')[1];
        // box size should change for profile none (no type)
        var maxHeight = (type === "") ? 12 + 6 : 2 * 12 + 6;
        d3.selectAll('.bBox')
            .attr("width", maxWidth + 10)
            .attr("height", maxHeight);
    },
    /**
     * Creates the elevation profile
     */
    _createChart: function(idx) {
        areas = this._profile.blocks[idx].geometries;
        this._areasFlattended = [].concat.apply([], areas);
        for (var i = 0; i < areas.length; i++) {
            this._appendAreas(areas[i], idx, i);
        }
        this._createFocus();
        this._appendBackground();
        this._createBorderTopLine();
        this._createLegend();
        this._createHorizontalLine();
    },
    /**
     *  Creates focus Line and focus box while hovering
     */
    _createFocus: function() {
        var boxPosition = this._profile.yElevationMin;
        var textDistance = 15;
        if (this._focus) {
            this._focus.remove();
            this._focusLineGroup.remove();
        }
        this._focus = this._svg.append("g")
            .attr("class", "focus");
        // background box
        this._focusRect = this._focus.append("rect")
            .attr("x", 3)
            .attr("y", -this._y(boxPosition))
            .attr("display", "none");
        // text line 1
        this._focusDistance = this._focus.append("text")
            .attr("x", 7)
            .attr("y", -this._y(boxPosition) + 1 * textDistance)
            .attr("id", "distance")
            .text('Distance:');
        // text line 2
        this._focusHeight = this._focus.append("text")
            .attr("x", 7)
            .attr("y", -this._y(boxPosition) + 2 * textDistance)
            .attr("id", "height")
            .text('Elevation:');
        // text line 3
        this._focusBlockDistance = this._focus.append("text")
            .attr("x", 7)
            .attr("y", -this._y(boxPosition) + 3 * textDistance)
            .attr("id", "blockdistance")
            .text('Segment length:');
        // text line 4
        this._focusType = this._focus.append("text")
            .attr("x", 7)
            .attr("y", -this._y(boxPosition) + 4 * textDistance)
            .attr("id", "type")
            .text('Type:');
        this._areaTspan = this._focusBlockDistance.append('tspan')
            .attr("class", "tspan");
        this._typeTspan = this._focusType.append('tspan')
            .attr("class", "tspan");
        var height = this._dynamicBoxSize('.focus text')[0];
        d3.selectAll('.focus rect')
            .attr("height", height * textDistance + (textDistance / 2))
            .attr("display", "block");
        this._focusLineGroup = this._svg.append("g")
            .attr("class", "focusLine");
        this._focusLine = this._focusLineGroup.append("line")
            .attr("y1", 0)
            .attr("y2", this._y(this._profile.yElevationMin));
        this._distTspan = this._focusDistance.append('tspan')
            .attr("class", "tspan");
        this._altTspan = this._focusHeight.append('tspan')
            .attr("class", "tspan");
    },
    /**
     *  Creates horizontal Line for dragging
     */
    _createHorizontalLine: function() {
        var self = this;
        this._horizontalLine = this._svg.append("line")
            .attr("class", "horizontalLine")
            .attr("x1", 0)
            .attr("x2", this._width - this._margin.left - this._margin.right)
            .attr("y1", this._y(this._profile.yElevationMin))
            .attr("y2", this._y(this._profile.yElevationMin))
            .style("stroke", "black");
        this._elevationValueText = this._svg.append("text")
            .attr("class", "horizontalLineText")
            .attr("x", this._width - this._margin.left - this._margin.right - 20)
            .attr("y", this._y(this._profile.yElevationMin)-10)
            .attr("fill", "black");
        //<text x="20" y="20" font-family="sans-serif" font-size="20px" fill="red">Hello!</text>
        //triangle symbol as controler
        var jsonCircles = [{
            "x": this._width - this._margin.left - this._margin.right + 7,
            "y": this._y(this._profile.yElevationMin),
            "color": "black",
            "type": d3.symbolTriangle,
            "angle": -90,
            "size": 100
        }];
        var horizontalDrag = this._svg.selectAll('.horizontal-symbol')
            .data(jsonCircles)
            .enter()
            .append('path')
            .attr("class", "lineSelection")
            .attr("d", d3.symbol()
                .type(function(d) {
                    return d.type;
                })
                .size(function(d) {
                    return d.size;
                }))
            .attr("transform", function(d) {
                return "translate(" + d.x + "," + d.y + ") rotate(" + d.angle + ")";
            })
            .attr("id", function(d) {
                return d.id;
            })
            .style("fill", function(d) {
                return d.color;
            })
            .call(d3.drag()
                .on("start", dragstarted)
                .on("drag", dragged)
                .on("end", dragended));

        function dragstarted(d) {
            d3.select(this)
                .raise()
                .classed("active", true);
            d3.select(".horizontalLine")
                .raise()
                .classed("active", true);
        }

        function dragged(d) {
            d3.select(this)
                .attr("transform", function(d) {
                    return "translate(" + d.x + "," + (d3.event.y < 0 ? 0 : (d3.event.y > 150 ? 150 : d3.event.y)) + ") rotate(" + d.angle + ")";
                })
            d3.select(".horizontalLine")
                .attr("y1", (d3.event.y < 0 ? 0 : (d3.event.y > 150 ? 150 : d3.event.y)))
                .attr("y2", (d3.event.y < 0 ? 0 : (d3.event.y > 150 ? 150 : d3.event.y)));
            self._highlightedCoords = self._findCoordsForY(d3.event.y);
            d3.select(".horizontalLineText")
                .attr("y", (d3.event.y < 0 ? 0 : (d3.event.y > 150 ? 150 : d3.event.y-10)))
                .text(d3.format(".0f")(self._y.invert((d3.event.y < 0 ? 0 : (d3.event.y > 150 ? 150 : d3.event.y)))) + " m");
        }

        function dragended(d) {
            d3.select(this)
                .classed("active", false);
            d3.select(".horizontalLine")
                .classed("active", false);
            if (self._markedSegments != undefined) {
                self._map.removeLayer(self._markedSegments);
            }
            self._markSegmentsOnMap(self._highlightedCoords);
        }
    },
    /**
     * Highlights segments on the map above given elevation value
     */
    _markSegmentsOnMap: function(coords) {
        this._markedSegments = L.polyline(coords, {
                color: 'red'
            })
            .addTo(this._map);
    },
    /**
     * Defines the ranges and format of x- and y- scales and appends them
     */
    _appendScales: function() {
        var shortDist = Boolean(this._profile.totalDistance <= 5);
        var yHeightMin = this._profile.yElevationMin;
        var yHeightMax = this._profile.yElevationMax;
        var margin = this._margins,
            width = this._width - this._margin.left - this._margin.right,
            height = this._height - this._margin.top - this._margin.bottom;
        this._x = d3.scaleLinear()
            .range([0, width]);
        this._y = d3.scaleLinear()
            .range([height, 0]);
        this._x.domain([0, this._profile.totalDistance]);
        this._y.domain([yHeightMin, yHeightMax]);
        if (shortDist == true) {
            this._xAxis = d3.axisBottom()
                .scale(this._x)
                .tickFormat(function(d) {
                    return d3.format(".2f")(d) + " km";
                });
        } else {
            this._xAxis = d3.axisBottom()
                .scale(this._x)
                .tickFormat(function(d) {
                    return d3.format(".1f")(d) + " km";
                });
        }
        this._yAxis = d3.axisLeft()
            .scale(this._y)
            .ticks(5)
            .tickFormat(function(d) {
                return d + " m";
            });
        this._yEndAxis = d3.axisRight()
            .scale(this._yEnd)
            .ticks(0);
    },
    /**
     * Appends a background an adds mouse handlers 
     */
    _appendBackground: function() {
        var background = this._background = d3.select(this._container)
            .select("svg")
            .select("g")
            .append("rect")
            .attr("width", this._svgWidth)
            .attr("height", this._svgHeight)
            .style("fill", "none")
            .style("stroke", "none")
            .style("pointer-events", "all")
            .on("mousemove.focus", this._mousemoveHandler.bind(this))
            .on("mouseout.focus", this._mouseoutHandler.bind(this));
        if (L.Browser.android) {
            background.on("touchstart.drag", this._dragHandler.bind(this))
                .on("touchstart.drag", this._dragStartHandler.bind(this))
                .on("touchstart.focus", this._mousemoveHandler.bind(this));
            L.DomEvent.on(this._container, 'touchend', this._dragEndHandler, this);
        } else {
            background.on("mousemove.focus", this._mousemoveHandler.bind(this))
                .on("mouseout.focus", this._mouseoutHandler.bind(this))
                .on("mousedown.drag", this._dragStartHandler.bind(this))
                .on("mousemove.drag", this._dragHandler.bind(this));
            L.DomEvent.on(this._container, 'mouseup', this._dragEndHandler, this);
        }
    },
    /**
     * Appends a grid to the graph 
     */
    _appendGrid: function() {
        this._svg.append("g")
            .attr("class", "grid")
            .attr("transform", "translate(0," + this._svgHeight + ")")
            .call(this._make_x_axis()
                .tickSize(-this._svgHeight, 0, 0)
                .tickFormat(""));
        this._svg.append("g")
            .attr("class", "grid")
            .call(this._make_y_axis()
                .tickSize(-this._svgWidth, 0, 0)
                .ticks(5)
                .tickFormat(""));
        this._svg.append('g')
            .attr("transform", "translate(0," + this._svgHeight + ")")
            .attr('class', 'x axis')
            .call(this._xAxis);
        this._svg.append('g')
            .attr('class', 'y axis')
            .call(this._yAxis);
    },
    /**
     * Appends the areas to the graph
     */
    _appendAreas: function(block, idx, eleIdx) {
        var c = this._profile.blocks[idx].attributes[eleIdx].color;
        var self = this;
        var area = this._area = d3.area()
            .x(function(d) {
                var xDiagCoord = self._x(d.position);
                d.xDiagCoord = xDiagCoord;
                return xDiagCoord;
            })
            .y0(this._svgHeight)
            .y1(function(d) {
                return self._y(d.altitude);
            })
            .curve(d3.curveLinear);
        this._areapath = this._svg.append("path")
            .attr("class", "area");
        this._areapath.datum(block)
            .attr("d", this._area)
            .attr("stroke", c)
            .style("fill", c)
            .style("pointer-events", "none");
    },
    // gridlines in x axis function
    _make_x_axis: function() {
        return d3.axisBottom()
            .scale(this._x);
    },
    // gridlines in y axis function
    _make_y_axis: function() {
        return d3.axisLeft()
            .scale(this._y);
    },
    /**
     * Appends a selection box for different blocks
     */
    _createSelectionBox: function() {
        var self = this;
        var svg = d3.select(this._container)
            .select("svg");
        var margin = this._margins,
            width = this._width - this._margin.left - this._margin.right,
            height = this._height - this._margin.top - this._margin.bottom;
        var jsonCircles = [{
            "x": width - 50,
            "y": height + 48,
            "color": "#000",
            "type": d3.symbolTriangle,
            "id": "leftArrowSelection",
            "angle": -360
        }, {
            "x": width - 35,
            "y": height + 45,
            "color": "#000",
            "type": d3.symbolTriangle,
            "id": "rightArrowSelection",
            "angle": 180
        }];
        var selectionSign = svg.selectAll('.select-symbol')
            .data(jsonCircles)
            .enter()
            .append('path')
            .attr("class", "select-symbol")
            .attr("d", d3.symbol()
                .type(function(d) {
                    return d.type;
                }))
            .attr("transform", function(d) {
                return "translate(" + d.x + "," + d.y + ") rotate(" + d.angle + ")";
            })
            .attr("id", function(d) {
                return d.id;
            })
            .style("fill", function(d) {
                return d.color;
            })
            .on("click", function(d) {
                if (d.id == "rightArrowSelection") arrowRight();
                if (d.id == "leftArrowSelection") arrowLeft();
            });
        var length = this._profile.blocks.length;
        var id = this._selectedOption;
        chooseSelection(id);

        function arrowRight() {
            var counter = self._selectedOption += 1;
            if (counter == self._profile.blocks.length) {
                counter = 0;
                self._selectedOption = 0;
            }
            chooseSelection(counter);
            self._selection(self._selectedOption);
        }

        function arrowLeft() {
            var counter = self._selectedOption -= 1;
            if (counter == -1) {
                counter = self._profile.blocks.length - 1;
                self._selectedOption = self._profile.blocks.length - 1;
            }
            chooseSelection(counter);
            self._selection(self._selectedOption);
        }

        function chooseSelection(id) {
            var type = self._profile.blocks[id].info;
            var data = [{
                "selection": type.text
            }];
            if (self._selectionText) self._selectionText.remove();
            self._selectionText = svg.selectAll('selection_text')
                .data(data)
                .enter()
                .append('text')
                .attr("x", width - 20)
                .attr("y", height + 50)
                .text(function(d) {
                    return d.selection;
                })
                .attr("class", "select-info")
                .attr("id", "selectionText");
        }
    },
    /**
     * Creates and appends legend to chart
     */
    _createLegend: function() {
        var self = this;
        //console.log(Object.values(self._profile.blocks[this._selectedOption].legend), self._profile.blocks[this._selectedOption].legend, true);
        //var data = Object.values(self._profile.blocks[this._selectedOption].legend);
        var data = [];
        for (var item in this._profile.blocks[this._selectedOption].legend) {
            data.push(this._profile.blocks[this._selectedOption].legend[item]);
        }
        var margin = this._margins,
            width = this._width - this._margin.left - this._margin.right,
            height = this._height - this._margin.top - this._margin.bottom;
        var leg = [{
            "text": "Legend"
        }];
        var legendRectSize = 7;
        var legendSpacing = 7;
        var legend = this._svg.selectAll('.hlegend-hover')
            .data(data)
            .enter()
            .append('g')
            .attr('class', 'legend')
            .style("display", "none")
            .attr('transform', function(d, i) {
                var height = legendRectSize + legendSpacing;
                var offset = height * 2;
                var horz = legendRectSize - 15;
                var vert = i * height - offset;
                return 'translate(' + horz + ',' + vert + ')';
            });
        legend.append('rect')
            .attr('class', 'legend-rect')
            .attr('x', 15)
            .attr('y', 6 * 6)
            .attr('width', 6)
            .style('stroke', 'black')
            .attr('height', 6)
            .style('fill', function(d, i) {
                return d.color;
            });
        legend.append('text')
            .attr('class', 'legend-text')
            .attr('x', 30)
            .attr('y', 6 * 7)
            .text(function(d, i) {
                var textProp = d.text;
                self._boxBoundY = (height - (2 * height / 3) + 7) * i;
                return textProp;
            });
        legendHover = this._svg.selectAll('.legend-hover')
            .data(leg)
            .enter()
            .append('g')
            .attr('class', 'legend-hover');
        legendHover.append('text')
            .attr('class', 'legend-menu')
            .attr("class", "no-select")
            .attr('x', 15)
            .attr('y', height + 40)
            .text(function(d, i) {
                return d.text;
            })
            .on('mouseover', function() {
                d3.select('.legend-box')
                    .style("display", "block");
                d3.selectAll('.legend')
                    .style("display", "block");
            })
            .on('mouseleave', function() {
                d3.select('.legend-box')
                    .style("display", "none");
                d3.selectAll('.legend')
                    .style("display", "none");
            });
    },
    /**
     * calculates the margins of boxes 
     * @param {String} className: name of the class
     * @return {array} borders: number of text lines, widest range of text
     */
    _dynamicBoxSize: function(className) {
        var cnt = d3.selectAll(className)
            .nodes()
            .length;
        var widths = [];
        for (var i = 0; i < cnt; i++) {
            widths.push(d3.selectAll(className)
                .nodes()[i].getBoundingClientRect()
                .width);
        }
        var maxWidth = d3.max(widths);
        var borders = [cnt, maxWidth];
        return borders;
    },
    /**
     * Creates top border line on graph 
     */
    _createBorderTopLine: function() {
        var self = this;
        var data = this._areasFlattended;
        var borderTopLine = d3.line()
            .x(function(d) {
                var x = self._x;
                return x(d.position);
            })
            .y(function(d) {
                var y = self._y;
                return y(d.altitude);
            })
            .curve(d3.curveBasis);
        this._svg.append("svg:path")
            .attr("d", borderTopLine(data))
            .attr('class', 'border-top');
    },
    /*
     * Handles the mouseout event when the mouse leaves the background
     */
    _mouseoutHandler: function() {
        if (this._focusLine) {
            this._pointG.style('display', 'none');
            this._focus.style('display', 'none');
            this._focusLine.style('display', 'none');
            this._mouseHeightFocus.style('display', 'none');
            this._mouseHeightFocusLabel.style('display', 'none');
        }
    },
    /*
     * Handles the moueseover the chart and displays distance and altitude level
     */
    _mousemoveHandler: function(d, i, ctx) {
        var coords = d3.mouse(this._svg.node());
        var areaLength;
        var item = this._areasFlattended[this._findItemForX(coords[0])],
            alt = item.altitude,
            dist = item.position,
            ll = item.latlng,
            areaIdx = item.areaIdx,
            type = item.type;
        var boxWidth = this._dynamicBoxSize('.focus text')[1] + 10;
        if (areaIdx === 0) {
            areaLength = this._profile.blocks[this._selectedOption].distances[areaIdx];
        } else {
            areaLength = this._profile.blocks[this._selectedOption].distances[areaIdx] - this._profile.blocks[this._selectedOption].distances[areaIdx - 1];
        }
        this._showMarker(ll, alt, type);
        this._distTspan.text(" " + dist.toFixed(1) + ' km');
        this._altTspan.text(" " + alt + ' m');
        this._areaTspan.text(" " + areaLength.toFixed(1) + ' km');
        this._typeTspan.text(" " + type);
        this._focusRect.attr("width", boxWidth);
        this._focusLine.style("display", "block")
            .attr('x1', this._x(dist))
            .attr('x2', this._x(dist));
        var xPositionBox = this._x(dist) - (boxWidth + 5);
        var totalWidth = this._width - this._margin.left - this._margin.right;
        if (this._x(dist) + boxWidth < totalWidth) {
            this._focus.style("display", "initial")
                .attr("transform", "translate(" + this._x(dist) + "," + this._y(this._profile.yElevationMin) + ")");
        }
        if (this._x(dist) + boxWidth > totalWidth) {
            this._focus.style("display", "initial")
                .attr("transform", "translate(" + xPositionBox + "," + this._y(this._profile.yElevationMin) + ")");
        }
    },
    /*
     * Finds a data entry for a given x-coordinate of the diagram
     */
    _findItemForX: function(x) {
        var bisect = d3.bisector(function(d) {
                return d.position;
            })
            .left;
        var xinvert = this._x.invert(x);
        return bisect(this._areasFlattended, xinvert);
    },
    /*
     * Finds data entries above a given y-elevation value and returns geo-coordinates
     */
    _findCoordsForY: function(y) {
        var self = this;

        function bisect(b, yinvert) {
            //save indexes of elevation values above the horizontal line
            var list = [];
            for (var i = 0; i < b.length; i++) {
                if (b[i].altitude >= yinvert) {
                    list.push(i);
                }
            }
            //split index list into coherent blocks of coordinates
            var newList = [];
            var start = 0;
            for (var i = 0; i < list.length - 1; i++) {
                if (list[i + 1] != list[i] + 1) {
                    newList.push(list.slice(start, i + 1));
                    start = i + 1;
                }
            }
            newList.push(list.slice(start, list.length))
            //get lat lon coordinates based on indexes
            for (var i = 0; i < newList.length; i++) {
                for (var j = 0; j < newList[i].length; j++) {
                    newList[i][j] = b[newList[i][j]].latlng;
                }
            }
            return newList
        }
        var yinvert = this._y.invert(y);
        return bisect(this._areasFlattended, yinvert);
    },
});
L.control.heightgraph = function(options) {
    return new L.Control.Heightgraph(options);
};