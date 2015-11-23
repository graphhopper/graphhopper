L.NumberedDivIcon = L.Icon.extend({
  options: {
    iconUrl: './img/marker_hole.png',
    number: '',
    shadowUrl: null,
    iconSize: new L.Point(25, 41),
    iconAnchor: new L.Point(12, 40),
    popupAnchor: new L.Point(0, -33),
    shadowSize: new L.Point(50, -64),
    shadowAnchor: new L.Point(4, -62),
    className: 'leaflet-div-icon'
  },

  createIcon: function () {
    var div = document.createElement('div');
    var img = this._createImg(this.options['iconUrl']);
    var numdiv = document.createElement('div');
    numdiv.setAttribute ( "class", "number" );
    numdiv.innerHTML = this.options['number'] || '';
    div.appendChild ( img );
    div.appendChild ( numdiv );
    this._setIconStyles(div, 'icon');
    return div;
  },

  //you could change this to add a shadow like in the normal marker if you really wanted
  createShadow: function () {
    return null;
  }
});