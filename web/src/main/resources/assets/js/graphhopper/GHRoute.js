var GHInput = require('./GHInput.js');

var GHroute = function () {
    var route = Object.create(Array.prototype);
    route = (Array.apply(route, arguments) || route);
    GHroute.injectClassMethods(route);
    route._listeners = {};
    return (route);
};

GHroute.injectClassMethods = function (route) {
    for (var method in GHroute.prototype) {
        if (GHroute.prototype.hasOwnProperty(method)) {
            route[method] = GHroute.prototype[method];
        }
    }
    return (route);
};

GHroute.fromArray = function (array) {
    var route = GHroute.apply(null, array);
    return (route);
};

GHroute.isArray = function (value) {
    var stringValue = Object.prototype.toString.call(value);
    return (stringValue.toLowerCase() === "[object array]");
};

GHroute.isObject = function (value) {
    var stringValue = Object.prototype.toString.call(value);
    return (stringValue.toLowerCase() === "[object object]");
};

GHroute.prototype = {
    first: function () {
        return this.getIndex(0);
    },
    last: function () {
        return this.getIndex((this.length - 1));
    },
    getIndex: function (index) {
        index = (isNaN(index)) ? 0 : index;
        if (this[index] instanceof GHInput) {
            return this[index];
        } else
            return false;
    },
    getIndexByCoord: function (value) {
        var point,
                index = false,
                coord = new GHInput(value),
                i,
                l;

        for (i = 0, l = this.length; i < l; i++) {
            point = this[i];
            if (point.toString() === coord.toString()) {
                index = i;
                break;
            }
        }
        return index;
    },
    getIndexFromCoord: function (value) {
        return this.getIndex(this.getIndexByCoord(value));
    },
    size: function () {
        return this.length;
    },
    add: function (value, to) {
        if (GHroute.isArray(value)) {
            for (var i = 0; i < value.length; i++) {
                Array.prototype.push.call(this, (value[i] instanceof GHInput) ? value[i] : new GHInput(value[i]));
                if (to !== undefined) {
                    this.move(-1, to, true);
                    to++;
                } else
                    to = this.lenght - 1;
                this.fire('route.add', {
                    point: this[to],
                    to: to
                });
            }
            return (this);
        } else {
            Array.prototype.push.call(this, (value instanceof GHInput) ? value : new GHInput(value));
            if (to !== undefined)
                this.move(-1, to, true);
            else
                to = this.lenght - 1;
            this.fire('route.add', {
                point: this[to],
                to: to
            });
        }
        return (this[to]);
    },
    removeSingle: function (value) {
        var index = false;
        if (!(isNaN(value) || value >= this.length) && this[value] !== undefined) {
            index = value;
        } else {
            if (value instanceof GHInput) {
                value = value.toString();
            }
            index = this.getIndexByCoord(value);
        }
        if (index !== false) {
            this.remove(index);
        }
        return (this);
    },
    remove: function (from, to) {
        var tmpTo = to || 1;
        Array.prototype.splice.call(this, from, tmpTo);
        if (this.length === 1)
            Array.prototype.push.call(this, new GHInput());
        this.fire('route.remove', {
            from: from,
            to: tmpTo
        });
        return (this);
    },
    addAll: function () {
        for (var i = 0; i < arguments.length; i++) {
            this.add(arguments[i]);
        }
        return (this);
    },
    set: function (value, to, create) {
        if (value instanceof GHInput)
            this[to] = value;
        else if (this[to] instanceof GHInput) {
            this[to].set(value);
        } else if (create)
            return this.add(value, to);
        else
            return false;
        this.fire('route.set', {
            point: this[to],
            to: to
        });
        return (this[to]);
    },
    move: function (old_index, new_index, supress_event) {
        while (old_index < 0) {
            old_index += this.length;
        }
        while (new_index < 0) {
            new_index += this.length;
        }
        if (new_index >= this.length) {
            var k = new_index - this.length;
            while ((k--) + 1) {
                Array.prototype.push.call(this, undefined);
            }
        }
        Array.prototype.splice.call(this, new_index, 0, Array.prototype.splice.call(this, old_index, 1)[0]);
        if (!supress_event)
            this.fire('route.move', {
                old_index: old_index,
                new_index: new_index
            });
        return (this);
    },
    reverse: function () {
        Array.prototype.reverse.call(this);
        this.fire('route.reverse', {});
        return (this);
    },
    isResolved: function () {
        for (var i = 0, l = this.length; i < l; i++) {
            var point = this[i];
            if (!point.isResolved()) {
                return false;
            }
        }
        return true;
    },
    addListener: function (type, listener) {
        if (typeof this._listeners[type] === "undefined") {
            this._listeners[type] = [];
        }
        this._listeners[type].push(listener);
        return this;
    },
    fire: function (event, options) {
        if (typeof event === "string") {
            event = {type: event};
        }
        if (typeof options === "object") {
            for (var attrname in options) {
                event[attrname] = options[attrname];
            }
        }
        if (!event.route) {
            event.route = this;
        }
        if (!event.type) {  //falsy
            throw new Error("Event object missing 'type' property.");
        }
        if (this._listeners[event.type] instanceof Array) {
            var listeners = this._listeners[event.type];
            for (var i = 0, len = listeners.length; i < len; i++) {
                listeners[i].call(this, event);
            }
        }
    },
    removeListener: function (type, listener) {
        if (this._listeners[type] instanceof Array) {
            var listeners = this._listeners[type];
            for (var i = 0, len = listeners.length; i < len; i++) {
                if (listeners[i] === listener) {
                    listeners.splice(i, 1);
                    break;
                }
            }
        }
    }
};

module.exports = GHroute;
