/*
 * This file is part of Google Maps Exporter plugin for Gephi
 *
 * Author: Pekka Maksimainen, (c) 2013-2014
 * 
 * Contact: Gephi forums or http://graphmap.net
 */

$(document).ready(function() {
	Mapper.init('map_canvas', {
            maxZoom: 15
        });
});

var Mapper = (function() {
    var map, 
        coordinateMapType,
        markers = [],
        activeNodes = [],
        flipV, 
        verbose = false,
        initialized = false;

    var basicMarker = new google.maps.MarkerImage('marker.png',
                          new google.maps.Size(20, 20), // size
                          new google.maps.Point(0, 0), // center
                          new google.maps.Point(10, 10)); // anchor

    var grpClick = function() {
        console.log('click');
    }

    function CoordMapType() {};
    CoordMapType.prototype.tileSize = new google.maps.Size(256, 256);
    CoordMapType.prototype.maxZoom = 15;
    CoordMapType.prototype.minZoom = 8;
    CoordMapType.prototype.name = "Tile #s";
    CoordMapType.prototype.alt = "Tile Coordinate Map Type";
    CoordMapType.prototype.isPng = true;
    CoordMapType.prototype.boundaries = {
        minx: null,
        miny: null,
        maxx: null,
        maxy: null
    };
    CoordMapType.prototype.timestamp = (new Date().valueOf() * 0.001) | 0;
    CoordMapType.prototype.getTile = function(coord, zoom, ownerDocument) {
        var numTiles = 1 << zoom;
        var x = coord.x;
        var y = coord.y;
        // wrap horizontally on x-axis
        if (x < 0 || x >= numTiles) {
            x = (x % numTiles + numTiles) % numTiles;
        }
        var z2 = 1 << zoom - 1;
        zoom = zoom - 8;
        x = x - (z2);
        y = y - (z2);
        var div = ownerDocument.createElement('DIV');
        //	div.innerHTML = '(' + coord.x + ', ' + coord.y + ', ' + zoom + ')';
        //	div.innerHTML = '(' + x + ', ' + y + ', ' + zoom + ')<br />' + '(' + coord.x + ', ' + coord.y + ', ' + zoom + ')';
        //	div.style.border = '1px solid red';
        div.style.color = '#FFF';
        div.style.width = this.tileSize.width + 'px';
        div.style.height = this.tileSize.height + 'px';
        div.style.fontSize = '10';
        div.style.background = 'url("tile-' + x + '-' + y + '-' + zoom + '.png")';

        return div;
    };

    var coordinateMapType = new CoordMapType();
    var geocoder = new google.maps.Geocoder();

    function lng2x(lng) {
        var minx = coordinateMapType.boundaries.minx;
        var maxx = coordinateMapType.boundaries.maxx;
        var posx = (lng + 180) / 360 * (maxx - minx) + minx;

        return posx;
    }

    function lat2y(lat) {
        var miny = coordinateMapType.boundaries.miny;
        var maxy = coordinateMapType.boundaries.maxy;
        var posy = (lat + 85) / 170 * (maxy - miny) + miny; // notice that the projection is capped at 85 degrees near poles

        return posy;
    }

    var TILE_SIZE = 256;
    var origo = new google.maps.LatLng(0, 0);

    function DirectProjection() {
        this.pixelOrigin_ = new google.maps.Point(TILE_SIZE / 2, TILE_SIZE / 2);
        this.pixelsPerLonDegree_ = TILE_SIZE / 360;
        this.pixelsPerLonRadian_ = TILE_SIZE / (2 * Math.PI);
    }

    DirectProjection.prototype.fromLatLngToPoint = function(latLng, opt_point) {
        var me = this;
        var point = opt_point || new google.maps.Point(0, 0);
        var origin = me.pixelOrigin_;

        point.x = origin.x + latLng.lng() * me.pixelsPerLonDegree_;

        // NOTE(appleton): Truncating to 0.9999 effectively limits latitude to
        // 89.189.  This is about a third of a tile past the edge of the world
        // tile.
        var siny = bound(Math.sin(degreesToRadians(latLng.lat())), -0.9999, 0.9999);
        point.y = origin.y + 0.5 * Math.log((1 + siny) / (1 - siny)) * -me.pixelsPerLonRadian_;

        var lat = latLng.lat();
        var lng = latLng.lng();

        var x = (lng + 180) / 360 * 256;
        var y = (lat + 85) / 170 * 256;

        point.x = x;
        point.y = y;

        return point;
    };

    DirectProjection.prototype.fromPointToLatLng = function(point) {
        var me = this;
        var origin = me.pixelOrigin_;
        var lng = (point.x - origin.x) / me.pixelsPerLonDegree_;
        var latRadians = (point.y - origin.y) / -me.pixelsPerLonRadian_;

        var lat = radiansToDegrees(2 * Math.atan(Math.exp(latRadians)) - Math.PI / 2);

        var x = point.x / 256 * 360 - 180;
        var y = point.y / 256 * 170 - 85;

        lng = x;
        lat = y;

        return new google.maps.LatLng(lat, lng);
    };


    function degreesToRadians(deg) {
        return deg * (Math.PI / 180);
    }

    function radiansToDegrees(rad) {
        return rad / (Math.PI / 180);
    }

    function bound(value, opt_min, opt_max) {
        if (opt_min != null) value = Math.max(value, opt_min);
        if (opt_max != null) value = Math.min(value, opt_max);

        return value;
    }

    function latLng2Coord(lat, lng) {
        var minx = coordinateMapType.boundaries.minx;
        var miny = coordinateMapType.boundaries.miny;
        var maxx = coordinateMapType.boundaries.maxx;
        var maxy = coordinateMapType.boundaries.maxy;

        var posy = (lat + 90) / 180 * (maxy - miny) + miny;
        var posx = (lng + 180) / 360 * (maxx - minx) + minx;

        return [posx, posy];
    }

    function curry(fn, scope) {
        var scope = scope || window;
        var args = [];
        for (var i = 2, len = arguments.length; i < len; ++i) {
            args.push(arguments[i]);
        };

        return function() {
            fn.apply(scope, args);
        };
    }

    var api = {
        isInitialized: function() {
            return initialized;
        },
        init: function(map_id, options) {
            if (!options) {
                options = {};
            }
            map = new google.maps.Map(document.getElementById(map_id), {
                zoom: 10,
                maxZoom: options.maxZoom || 15,
                center: new google.maps.LatLng(170 / Math.pow(2, 8) / 2, 360 / Math.pow(2, 8) / 2),
                mapTypeId: google.maps.MapTypeId.HYBRID,
                mapTypeIds: ['coordinate'],
                streetViewControl: false,
                mapTypeControl: false,
                backgroundColor: '#000',
                style: google.maps.MapTypeControlStyle.DROPDOWN_MENU,
                panControlOptions: {
                    position: google.maps.ControlPosition.LEFT_CENTER
                },
                zoomControlOptions: {
                    position: google.maps.ControlPosition.LEFT_CENTER
                }
            });

            if (verbose) {
                google.maps.event.addListener(map, 'mousemove', function(event) {
                    console.log('Coord.X.Y: ' + event.latLng);
                    console.log('Point.X.Y: ' + latLng2Coord(event.latLng.lat(), event.latLng.lng()))
                });
            }

            // Fix Firefox scrolling the page when using wheel zoom
            $('#map_canvas').on(
                "MozMousePixelScroll",
                function(event) {
                    event.preventDefault();
                }, false);

            // set the controls for the heart of the sun
            coordinateMapType = new CoordMapType();
            minx = typeof minx === 'undefined' ? 0 : minx;
            miny = typeof miny === 'undefined' ? 0 : miny;
            maxx = typeof maxx === 'undefined' ? 0 : maxx;
            maxy = typeof maxy === 'undefined' ? 0 : maxy;
            coordinateMapType.boundaries.minx = minx;
            coordinateMapType.boundaries.miny = miny;
            coordinateMapType.boundaries.maxx = maxx;
            coordinateMapType.boundaries.maxy = maxy;
            var projection = new DirectProjection();
            coordinateMapType.projection = projection;
            map.mapTypes.set('coordinate', coordinateMapType);
            map.setMapTypeId('coordinate');
            initialized = true;
        },
        setIdleFunc: function(func) {
	       google.maps.event.addListener(map, 'idle', func);
	    },
        getMap: function() {
            return map;
        },
        getClickFunc: function() {
            return grpClick;
        },
        getBounds: function() {
            var bounds = map.getBounds();
            var center = map.getCenter();
            var ne = bounds.getNorthEast();
            var sw = bounds.getSouthWest();
            var span = bounds.toSpan();
            var zoom = map.getZoom();

            // note: coordinates are mirrored so...
            var swCoord = latLng2Coord(ne.lat(), ne.lng());
            var neCoord = latLng2Coord(sw.lat(), sw.lng());

            var my_miny = lat2y(sw.lat());
            var my_minx = lng2x(sw.lng());
            var my_maxy = lat2y(ne.lat());
            var my_maxx = lng2x(ne.lng());

            var tileWidthDegrees = 360 / Math.pow(2, 8);
            var tileHeightDegrees = 170 / Math.pow(2, 8);
            
            my_miny = lat2y(-85 + sw.lat() / tileHeightDegrees * 170);
            my_minx = lng2x(-180 + sw.lng() / tileWidthDegrees * 360);
            my_maxy = lat2y(-85 + ne.lat() / tileHeightDegrees * 170);
            my_maxx = lng2x(-180 + ne.lng() / tileWidthDegrees * 360);

            if (flipV) {
                var tmp = my_miny;
                my_miny = -my_maxy;
                my_maxy = -tmp;
            }
            
            return {
                minx: my_minx,
                miny: my_miny,
                maxx: my_maxx,
                maxy: my_maxy
            };
        },
        getCenter: function() {
            var bounds = this.getBounds();
            var centerx = (bounds.maxx + bounds.minx) / 2;
            var centery = (bounds.maxy + bounds.miny) / 2;
            
            return [centerx, centery];
        },
        coord2LatLng: function(coordx, coordy) {
            var minx = coordinateMapType.boundaries.minx;
            var maxx = coordinateMapType.boundaries.maxx;
            var miny = coordinateMapType.boundaries.miny;
            var maxy = coordinateMapType.boundaries.maxy;

            if (typeof maxx === 'undefined') {
                throw 'Boundaries are not set';
            }

            // Canvas size
            var dimx = (Math.abs(minx - maxx));
            var dimy = (Math.abs(miny - maxy));

            // Point position relative to canvas
            var x = Math.floor(coordx - minx);
            var y = Math.floor(coordy - miny);

            // Flip overlay data on y-axis if necessary, often it is
            if (flipV) {
                y = Math.floor(maxy - coordy); // offset from top bound
                y -= (miny + maxy); // offset from abscissa
            }

            // Position translated to coordinate system
            cx = x / dimx * 360;
            cy = y / dimy * 170;

            cx /= 256;
            cy /= 256;

            lng = cx;
            lat = cy;

            return [lat, lng];
        },
        insertNodes: function(data) {
            var zoomLevel = 15;
            var locConv = function(loc) {
            var lat = loc.lat();
            var lng = loc.lng();
            var zoom = 8;
            var divisor = Math.pow(2, zoom); // tile width in cartesian (1 = 360 degrees in width or so)
            var lng2 = (lng+180) / divisor;
            var lat2 = (lat+85) / divisor;
            return new google.maps.LatLng(lat2, lng2);
            }
            if (zoomLevel > 0) {
            for (i in data.nodes) {
                var node = data.nodes[i];
                var latLng = Mapper.coord2LatLng(node.x, node.y);
                var lat = parseFloat(latLng[0]);
                var lng = parseFloat(latLng[1]);
                var loc = new google.maps.LatLng(lat, lng);
    //          loc = locConv(loc);
                var marker = activeNodes[node.id] ? pointerMarker : basicMarker;
                if (markerIds.indexOf(node.id) < 0) {

                var marker = new google.maps.Marker({
                    position: loc,
                    map: map,
                    title: node.groupName + " (" + node.x + ", " + node.y + "), size: " + node.size + " id: " + node.id,
                    optimized: false,
                    icon: marker,
                    id: node.id,
                    animation: google.maps.Animation.DROP
                });
                google.maps.event.addListener(marker, 'click', Mapper.getClickFunc());

                markers.push(marker);
                markerIds.push(node.id);
                }
            }
            }
        },
        removeNodes: function() {
            // todo: implement removing just the nodes given as parameter
            markerIds = [];
            for (var i = 0; i < markers.length; i++) {
            markers[i].setMap(null);
            }
        },
        flipV: function(b) {
            flipV = b;
        },
        verbose: function(b) {
            verbose = b;
        }
    }

    return api;
})();