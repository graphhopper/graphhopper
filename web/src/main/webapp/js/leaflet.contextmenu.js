/*
Leaflet.contextmenu, a context menu for Leaflet.
(c) 2014, Adam Ratcliffe, GeoSmart Maps Limited
contribute 2014, Roland Braband, NRC
 */
L.Map.mergeOptions({
    contextmenuItems : []
});

L.Map.ContextMenu = L.Handler.extend({

        statics : {
            BASE_CLS : 'leaflet-contextmenu'
        },

        initialize : function (map) {
            L.Handler.prototype.initialize.call(this, map);

            this._items = [];
            this._sets = [];
            this._state = 0;
            this._defaultState = map.options.contextmenuDefaultState || 1;
            this._activeState = map.options.contextmenuAtiveState || 1;
            this._visible = false;

            var container = this._container = L.DomUtil.create('div', L.Map.ContextMenu.BASE_CLS, map._container);
            container.style.zIndex = 1e4;
            container.style.position = 'absolute';

            if (map.options.contextmenuWidth) {
                container.style.width = map.options.contextmenuWidth + 'px';
            }
            if (map.options.contextmenuSets === undefined || map.options.contextmenuSets.length === 0) {
                map.options.contextmenuSets = [{
                        name : 'set_default',
                        state : this._defaultState
                    }
                ];
            }

            this._createItems();
            this._createSets();
            this._changeState();
            L.DomEvent
            .on(container, 'click', L.DomEvent.stop)
            .on(container, 'mousedown', L.DomEvent.stop)
            .on(container, 'dblclick', L.DomEvent.stop)
            .on(container, 'contextmenu', L.DomEvent.stop);
        },

        addHooks : function () {
            L.DomEvent
            .on(document, ((L.Browser.touch) ? 'touchstart' : 'mousedown'), this._onMouseDown, this)
            .on(document, 'keydown', this._onKeyDown, this);

            this._map.on({
                contextmenu : this._show,
                mouseout : this._hide,
                mousedown : this._hide,
                movestart : this._hide,
                zoomstart : this._hide
            }, this);
        },

        removeHooks : function () {
            L.DomEvent.off(document, 'keydown', this._onKeyDown, this);

            this._map.off({
                contextmenu : this._show,
                mouseout : this._hide,
                mousedown : this._hide,
                movestart : this._hide,
                zoomstart : this._hide
            }, this);

        },

        showAt : function (point, data, state) {
            if (point instanceof L.LatLng) {
                point = this._map.latLngToContainerPoint(point);
            }
            this._showAtPoint(point, data, state);
        },

        hide : function () {
            this._hide();
        },

        setState : function (state) {
            return this._changeState(state);
        },

        setActiveState : function (state) {
            var set,
            state = (state !== undefined) ? state : this._activeState,
            el,
            i,
            l;

            for (i = 0, l = this._sets.length; i < l; i++) {
                set = this._sets[i];
                if (set.state === state) {
                    this._activeState = state;
                    break;
                }
            }
            return set;
        },

        getState : function () {
            return this._state;
        },

        addSet : function (options) {
            return this.insertSet(options);
        },

        insertSet : function (options, id) {
            var id = (id !== undefined) ? id : this._sets.length,
            set = this._createSet(options, id);

            this._sets.push(set);
            return set;
        },

        addItem : function (options) {
            return this.insertItem(options);
        },

        insertItem : function (options, index) {
            var index = (index !== undefined) ? index : this._items.length,
            item = this._createItem(this._container, options, index);

            this._items.push(item);
            this._sizeChanged = true;
            this._map.fire('contextmenu.additem', {
                contextmenu : this,
                el : item.el,
                index : index
            });

            return item.el;
        },

        removeItem : function (item) {
            var container = this._container;

            if (!isNaN(item)) {
                item = container.children[item];
            }

            if (item !== undefined) {
                this._removeItem(L.Util.stamp(item));

                this._sizeChanged = true;

                this._map.fire('contextmenu.removeitem', {
                    contextmenu : this,
                    el : item
                });

            }
        },

        removeAllItems : function () {
            var item;
            
            while (this._container.children.length) {
                item = this._container.children[0];
                this._removeItem(L.Util.stamp(item));
            }
        },

        setDisabled : function (item, disabled) {
            var container = this._container,
            itemCls = L.Map.ContextMenu.BASE_CLS + '-item';

            if (!isNaN(item)) {
                item = container.children[item];
            }

            if (item !== undefined && L.DomUtil.hasClass(item, itemCls)) {
                if (disabled) {
                    L.DomUtil.addClass(item, itemCls + '-disabled');
                    this._map.fire('contextmenu.disableitem', {
                        contextmenu : this,
                        el : item
                    });
                } else {
                    L.DomUtil.removeClass(item, itemCls + '-disabled');
                    this._map.fire('contextmenu.enableitem', {
                        contextmenu : this,
                        el : item
                    });
                }
            }
        },

        setHidden : function (item, hidden) {
            var container = this._container,
            itemCls = L.Map.ContextMenu.BASE_CLS + '-item',
            separatorCls = L.Map.ContextMenu.BASE_CLS + '-separator';

            if (!isNaN(item)) {
                item = container.children[item];
            }
            if (item !== undefined && L.DomUtil.hasClass(item, itemCls)) {
                if (hidden) {
                    L.DomUtil.addClass(item, itemCls + '-hidden');
                    this._map.fire('contextmenu.hideitem', {
                        contextmenu : this,
                        el : item
                    });

                } else {
                    L.DomUtil.removeClass(item, itemCls + '-hidden');
                    this._map.fire('contextmenu.showitem', {
                        contextmenu : this,
                        el : item
                    });
                }
            } else if (item !== undefined && L.DomUtil.hasClass(item, separatorCls)) {
                if (hidden) {
                    L.DomUtil.addClass(item, separatorCls + '-hidden');
                } else {
                    L.DomUtil.removeClass(item, separatorCls + '-hidden');
                }
            }
        },

        isVisible : function () {
            return this._visible;
        },

        // private methods
        _changeState : function (state) {
            var set,
            state = (state !== undefined) ? state : this._defaultState,
            item,
            el,
            i,
            l;
            
            if (state !== this._state) {
                for (i = 0, l = this._sets.length; i < l; i++) {
                    set = this._sets[i];
                    if (set.state === state || (set.name === state && set.state !== this._state)) {
                        this._map.fire('contextmenu.changestate', {
                            contextmenu : this,
                            set : set,
                            state : state
                        });
                        for (i = 0, l = this._items.length; i < l; i++) {
                            item = this._items[i];
                            this.setHidden(this._items[i].el, (item.state.indexOf(set.state) === -1 && item.state.indexOf(set.name) === -1));
                        }
                        this._sizeChanged = true;
                        this._state = state;
                        break;
                    }
                }
            }
            return set;
        },

        _createSets : function () {
            var setOptions = this._map.options.contextmenuSets,
            set,
            i,
            l;

            for (i = 0, l = setOptions.length; i < l; i++) {
                this._sets.push(this._createSet(setOptions[i], this._sets.length));
            }
        },

        _createSet : function (options, id) {
            var name = (options.name !== undefined) ? options.name : 'set_' + id;
            return {
                id : id,
                name : options.name,
                state : options.state
            };
        },

        _createItems : function () {
            var itemOptions = this._map.options.contextmenuItems,
            item,
            i,
            l;

            for (i = 0, l = itemOptions.length; i < l; i++) {
                this._items.push(this._createItem(this._container, itemOptions[i]));
            }
        },

        _createItem : function (container, options, index) {
            if (options.separator || options === '-') {
                return this._createSeparator(container, index, options.state);
            }

            var itemCls = L.Map.ContextMenu.BASE_CLS + '-item',
            state = (options.state !== undefined) ? ((Array.isArray(options.state)) ? options.state : [options.state]) : [this._defaultState],
            cls = (options.disabled) ? (itemCls + ' ' + itemCls + '-disabled') : ((options.hidden) ? (itemCls + ' ' + itemCls + '-hidden') : itemCls),
            el = this._insertElementAt('a', cls, container, index),
            callback = this._createEventHandler(el, options.callback, options.context, options.hideOnSelect),
            html = '';

            if (options.icon) {
                html = '<img class="' + L.Map.ContextMenu.BASE_CLS + '-icon" src="' + options.icon + '"/>';
            } else if (options.iconCls) {
                html = '<span class="' + L.Map.ContextMenu.BASE_CLS + '- icon ' + options.iconCls + '"></span>';
            }

            el.innerHTML = html + options.text;
            el.href = '#';
            L.DomEvent
            .on(el, 'mouseover', this._onItemMouseOver, this)
            .on(el, 'mouseout', this._onItemMouseOut, this)
            .on(el, 'mousedown', L.DomEvent.stopPropagation)
            .on(el, 'click', callback);

            return {
                id : L.Util.stamp(el),
                el : el,
                callback : callback,
                state : state
            };
        },

        _removeItem : function (id) {
            var item,
            callback,
            el,
            i,
            l;

            for (i = 0, l = this._items.length; i < l; i++) {
                item = this._items[i];

                if (item.id === id) {
                    el = item.el;
                    callback = item.callback;

                    if (callback) {
                        L.DomEvent
                        .off(el, 'mouseover', this._onItemMouseOver, this)
                        .off(el, 'mouseover', this._onItemMouseOut, this)
                        .off(el, 'mousedown', L.DomEvent.stopPropagation)
                        .off(el, 'click', item.callback);

                    }

                    this._container.removeChild(el);
                    this._items.splice(i, 1);
                    return item;

                }
            }
            return null;
        },

        _createSeparator : function (container, index, state) {
            var el = this._insertElementAt('div', L.Map.ContextMenu.BASE_CLS + '-separator', container, index),
            state = (state !== undefined) ? ((Array.isArray(state)) ? state : [state]) : [this._defaultState];

            return {
                id : L.Util.stamp(el),
                el : el,
                state : state
            };
        },

        _createEventHandler : function (el, func, context, hideOnSelect) {
            var me = this,
            map = this._map,
            disabledCls = L.Map.ContextMenu.BASE_CLS + '-item-disabled',
            hideOnSelect = (hideOnSelect !== undefined) ? hideOnSelect : true;

            return function (e) {
                if (L.DomUtil.hasClass(el, disabledCls)) {
                    return;

                }

                if (hideOnSelect) {
                    me._hide();

                }

                if (func) {
                    func.call(context || map, me._showLocation);
                }

                me._map.fire('contextmenu:select', {
                    contextmenu : me,
                    el : el
                });
            };
        },

        _insertElementAt : function (tagName, className, container, index) {
            var refEl,
            el = document.createElement(tagName);

            el.className = className;

            if (index !== undefined) {
                refEl = container.children[index];
            }

            if (refEl) {
                container.insertBefore(el, refEl);
            } else {
                container.appendChild(el);
            }

            return el;
        },

        _show : function (e) {
            this._showAtPoint(e.containerPoint);
        },

        _showAtPoint : function (pt, data, state) {
            if (this._items.length) {
                var map = this._map,
                layerPoint = map.containerPointToLayerPoint(pt),
                latlng = map.layerPointToLatLng(layerPoint),
                event = {
                    contextmenu : this,
                    state : state
                },
                state = (state !== undefined) ? state : this._activeState;

                if (data) {
                    event = L.extend(data, event);
                }

                this._showLocation = {
                    state : state,
                    target : (data) ? data.relatedTarget : null,
                    latlng : latlng,
                    layerPoint : layerPoint,
                    containerPoint : pt
                };

                this._setPosition(pt);
                this._changeState(state);
                
                if (!this._visible) {
                    this._container.style.display = 'block';
                    this._visible = true;
                } else {
                    this._setPosition(pt);
                }
                

                this._map.fire('contextmenu.show', event);
            }
        },

        _hide : function () {
            if (this._visible) {
                this.setState(this._defaultState);
                this._visible = false;
                this._container.style.display = 'none';
                this._map.fire('contextmenu.hide', {
                    contextmenu : this
                });
            }
        },

        _setPosition : function (pt) {
            var mapSize = this._map.getSize(),
            container = this._container,
            containerSize = this._getElementSize(container),
            anchor;

            if (this._map.options.contextmenuAnchor) {
                anchor = L.point(this._map.options.contextmenuAnchor);
                pt = pt.add(anchor);
            }

            container._leaflet_pos = pt;

            if (pt.x + containerSize.x > mapSize.x) {
                container.style.left = 'auto';
                container.style.right = Math.max(mapSize.x - pt.x, 0) + 'px';
            } else {
                container.style.left = Math.max(pt.x, 0) + 'px';
                container.style.right = 'auto';
            }

            if (pt.y + containerSize.y > mapSize.y) {
                container.style.top = 'auto';
                container.style.bottom = Math.max(mapSize.y - pt.y, 0) + 'px';
            } else {
                container.style.top = Math.max(pt.y, 0) + 'px';
                container.style.bottom = 'auto';
            }
        },

        _getElementSize : function (el) {
            var size = this._size,
            initialDisplay = el.style.display;

            if (!size || this._sizeChanged) {
                size = {};

                el.style.left = '-999999px';
                el.style.right = 'auto';
                el.style.display = 'block';

                size.x = el.offsetWidth;
                size.y = el.offsetHeight;

                el.style.left = 'auto';
                el.style.display = initialDisplay;

                this._sizeChanged = false;
            }

            return size;
        },

        _onMouseDown : function (e) {
            //console.log('_onMouseDown');
            this._hide();
        },

        _onKeyDown : function (e) {
            var key = e.keyCode;

            // If ESC pressed and context menu is visible hide it
            if (key === 27) {
                this._hide();
            }
        },

        _onItemMouseOver : function (e) {
            L.DomUtil.addClass(e.target, 'over');
        },

        _onItemMouseOut : function (e) {
            L.DomUtil.removeClass(e.target, 'over');
        }
    });

L.Map.addInitHook('addHandler', 'contextmenu', L.Map.ContextMenu);
L.Mixin.ContextMenu = {

    // private methods
    _initContextMenu : function () {
        this._items = [];

        this.on('contextmenu', this._showContextMenu, this);
    },

    _showContextMenu : function (e) {
        var itemOptions,
        pt,
        i,
        l;

        if (this._map.contextmenu) {
            pt = this._map.mouseEventToContainerPoint(e.originalEvent);

            for (i = 0, l = this.options.contextmenuItems.length; i < l; i++) {
                itemOptions = this.options.contextmenuItems[i];
                this._items.push(this._map.contextmenu.insertItem(itemOptions, itemOptions.index));
            }

            this._map.once('contextmenu.hide', this._hideContextMenu, this);

            this._map.contextmenu.showAt(pt, {
                relatedTarget : this
            },
            this.options.contextmenuAtiveState);
        }
    },

    _hideContextMenu : function () {
        var i,
        l;

        for (i = 0, l = this._items.length; i < l; i++) {
            this._map.contextmenu.removeItem(this._items[i]);
        }
        this._items.length = 0;
    }
};

L.Marker.mergeOptions({
    contextmenu : false,
    contextmenuItems : []
});

L.Marker.addInitHook(function () {
    if (this.options.contextmenu) {
        this._initContextMenu();
    }
});

L.Marker.include(L.Mixin.ContextMenu);

L.Path.mergeOptions({
    contextmenu : false,
    contextmenuItems : []
});

L.Path.addInitHook(function () {
    if (this.options.contextmenu) {
        this._initContextMenu();
    }
});

L.Path.include(L.Mixin.ContextMenu);