/* Initialize TaffyDB from graph JSON data */
var db = TAFFY(graph.nodes);
db.sort('size desc');

/* Nodes visible on map */
var visibleNodes = [];

/* Limit how many nodes to show on node table */
var visibleNodesLimit = 100;

/* Columns to show in table listing */
var distinctColumns; // automatically detected from grpah JSON data

/* Columns to exlucde from table data */
var excludeKeys = ['___id', '___s']; // Taffy variables?

/* Sort table data by this column */
var sortByColumn = 'size';

/* Direction of sort */
var descending = false;

/* Initialize Mapper */
setTimeout(function() {
    Mapper.setIdleFunc(function() {
      var bounds = Mapper.getBounds();

      // flip coordinates vertically or something... offsetting at least
      // this fixes the logically working getBounds() to pass correct
      // kind of values for spatial database query
      var bmaxy = bounds.maxy - maxy - miny;
      var bminy = bounds.miny - maxy - miny;

      visibleNodes = db().filter({
          x: {
              gte: bounds.minx,
              lte: bounds.maxx
              },
          y: {
              gte: bminy,
              lte: bmaxy
              }
      }).order('size desc').limit(visibleNodesLimit).get();

      updateTableData(visibleNodes);
      updateCircles(visibleNodes);
    });
}, 500);

/* Initial page load functionality */
$(document).ready(function() {
  Mapper.flipV(false);
  distinctColumns = Object.keys(DotObject.dot(graph.nodes[0]));
  updateTableData(graph.nodes);

  $('.info table').on('click', 'th', function(event) {
        var newSortByColumn = $(event.target).text();
        if (newSortByColumn == sortByColumn) {
          descending = !descending;
        }
        sortByColumn = newSortByColumn;
        updateTableData(visibleNodes);
  });
});

/* Helper functions */
zFactor = 152000;
var circleCache = {};

function scrollToNodeInTable(node) {
  var id = node.id || node;
  $('.info table tr').removeClass('highlight-border');
  $('.info').scrollTop(0);
  var row = $('.info table').first().find('tr[data-id="' + id + '"]').first();
  row.addClass('highlight-border');
  $('.info').scrollTop(row.position().top);
}

function removeCircles(data) {
  Object.keys(data).forEach(function(key) {
    data[key].setMap(null);
    delete data[key];
  });
}

function updateCircles(data) {
  /* Removing node circles from map may help with performance issues */
  if (Object.keys(circleCache).length > 500) {
    removeCircles(circleCache);
  }

  data.forEach(function(node) {
    var nodeCircleExists = !!circleCache[node.id];

    if (!nodeCircleExists) {
      var loc = Mapper.coord2LatLng(node.x, node.y);
      var nodeSize = node.size / Math.abs(maxx - minx) * zFactor;
      nodeCircle = new google.maps.Circle({
        strokeWeight: 1,
        strokeColor: '#FFFFFF',
        fillOpacity: 0,
        map: Mapper.getMap(),
        center: new google.maps.LatLng(loc[0], loc[1]),
        radius: nodeSize,
        clickable: true,
        id: node.id
      });
      google.maps.event.addListener(nodeCircle, 'mouseover', function() {
        console.log(node);
        scrollToNodeInTable(node);
      });
      circleCache[node.id] = nodeCircle;
    };
  });
}

function sortData(data, sortBy) {
  sortBy = sortBy || sortByColumn;

  var definedItems = data.filter(function(n) { return typeof DotObject.pick(sortBy, n) !== 'undefined'; });
  var undefinedItems = data.filter(function(n) { return typeof DotObject.pick(sortBy, n) === 'undefined'; });

  definedItems.sort(function(a, b) {
    if (!isNaN(parseInt(a[sortBy])) && !isNaN(parseInt(a[sortBy]))) {
      return descending
        ? parseInt(a[sortBy]) - parseInt(b[sortBy])
        : parseInt(b[sortBy]) - parseInt(a[sortBy])
    }

    return descending
      ? DotObject.pick(sortBy, b).localeCompare(DotObject.pick(sortBy, a))
      : DotObject.pick(sortBy, a).localeCompare(DotObject.pick(sortBy, b));
  });

  data = undefinedItems.concat(definedItems);
  data.reverse();

  lastData = data;

  return data;
}

function updateTableData(data) {
  $('.info table thead').empty();
  $('.info table tbody').empty();

  data = sortData(data, sortByColumn);

  var tr = $('<tr></tr>').appendTo('.info table thead');
  distinctColumns.forEach(function(key) {
    if (excludeKeys.indexOf(key) > -1) {
      return true;
    }

    tr.append('<th title="' + key + '">' + key + '</th>');
  });

  var html = $('.info table tbody').html();
  data.forEach(function(item) {
    var htmlRow = '<tr data-id="' + item.id + '">';

    distinctColumns.forEach(function(key) {
      if (excludeKeys.indexOf(key) > -1) {
        return true;
      }

      var value = DotObject.pick(key, item) || '';
      htmlRow += '<td '
        + 'title="' + value + '" '
        + 'style="color: ' + item.color + '"'
        + '>' + value + '</td>';
    });
    htmlRow += '</tr>';
    html += htmlRow;
  });

  $('.info table tbody')[0].innerHTML = html;
};
