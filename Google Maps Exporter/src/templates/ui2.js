/* Initialize TaffyDB from graph JSON data */
var db = TAFFY(graph.nodes);
db.sort('size desc');

/* Nodes visible on map */
var visibleNodes = [];

/* Limit how many nodes to show on node table */
var visibleNodesLimit = 500;

/* Columns to show in table listing */
var distinctColumns; // automatically detected from grpah JSON data

/* Columns to exlucde from table data */
var excludeKeys = ['___id', '___s']; // Taffy variables?

/* Sort table data by this column */
var sortByColumn = 'size';

/* Direction of sort */
var descending = true;

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

  /* Click listener on the table header, we'll sort the table by clicked column */
  $('.info table').on('click', 'th', function(event) {
        var newSortByColumn = $(event.target).text();
        if (newSortByColumn == sortByColumn) {
          descending = !descending;
        }
        sortByColumn = newSortByColumn;
        updateTableData(visibleNodes);
  });

  /* When hovering on table row highlight the selected node on the map */
  $('.info table').on('mouseover', 'tbody tr', function() {
    var node = db({id: $(this).data('id').toString()}).first();
    highlightNode(node);
  });

  /* Remove highlighting when mouse leaves the row */
  $('.info table').on('mouseout', 'tbody tr', function() {
    var node = db({id: $(this).data('id').toString()}).first();
    unhighlightNode(node);
  });
});

/* Helper functions */
var zFactor = 152000; // Mysterious number that makes circles the right size
var circleCache = {};
var highlightCircleCache = {};

/* Add highlighted circle on the map */
function highlightNode(node) {
  var loc = Mapper.coord2LatLng(node.x + 1, node.y - 1);
  var nodeSize = node.size / Math.abs(maxx - minx) * zFactor;
  var nodeCircle = new google.maps.Circle({
    strokeWeight: 3,
    strokeColor: '#FF0000',
    map: Mapper.getMap(),
    center: new google.maps.LatLng(loc[0], loc[1]),
    radius: nodeSize,
    id: node.id
  });
  highlightCircleCache[node.id] = nodeCircle;
}

/* Remove highlighting from a circle on the map */
function unhighlightNode(node) {
  var circle = highlightCircleCache[node.id];
  circle.setMap(null);
  delete highlightCircleCache[node.id];
}

/* Remove highlight from all nodes on the map */
function unhighlightAll() {
  Object.keys(highlightCircleCache).forEach(function(key) {
    if (highlightCircleCache[key]) {
      highlightCircleCache[key].setMap(null);
      delete highlightCircleCache[key];
    }
  });
}

/* Scroll table to show hovered node on the map */
function scrollToNodeInTable(node) {
  /* Don't scroll table if checkbox is not checked */
  if (!$('#hover-focus').is(':checked')) {
    return;
  }
  var id = node.id || node;
  $('.info table tr').removeClass('highlight-border');
  $('.info').scrollTop(0);
  var row = $('.info table').first().find('tr[data-id="' + id + '"]').first();
  row.addClass('highlight-border');
  $('.info').scrollTop(row.position().top);
}

/* Removes circles from the map - useful to reset if lots of circles are drawn */
function removeCircles(data) {
  Object.keys(data).forEach(function(key) {
    data[key].setMap(null);
    delete data[key];
  });
}

/* Add circles on the map which can be hovered with a mouse cursor */
function updateCircles(data) {
  /* Removing node circles from map may help with performance issues */
  if (Object.keys(circleCache).length > 5000) {
    removeCircles(circleCache);
  }

  /* Go through all nodes */
  data.forEach(function(node) {
    var nodeCircleExists = !!circleCache[node.id];

    /* Skip adding nodes that are already on the map */
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

      /* Highlight node in table view when hovering over a node on a map */
      google.maps.event.addListener(nodeCircle, 'mouseover', function() {
        scrollToNodeInTable(node);
      });

      /* Store circles in a buffer so we can access the circle later */
      circleCache[node.id] = nodeCircle;
    };
  });
}

/* Sort table data by given column - don't add the data to table though */
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

/* Add given data to the table below the map */
function updateTableData(data) {
  $('.info table thead').empty();
  $('.info table tbody').empty();

  /* Sort data by selected column */
  data = sortData(data, sortByColumn);

  /* Construct title row which shows column names */
  var tr = $('<tr></tr>').appendTo('.info table thead');
  distinctColumns.forEach(function(key) {
    /* Don't list columns that are not interesting, we probably want to exclude
     * other columns such as x, y, color and size too. These columns are shown
     * to make this example more interesting looking.
     */
    if (excludeKeys.indexOf(key) > -1) {
      return true;
    }

    /* Add the column, 'key' is the name of the column */
    tr.append('<th title="' + key + '">' + key + '</th>');
  });

  /* The HTML here is empty - it could have some pre-existing rows */
  var html = $('.info table tbody').html();

  /* Construct data rows for the table */
  data.forEach(function(item) {
    var htmlRow = '<tr data-id="' + item.id + '">';

    /* Go through columns that we want to add */
    distinctColumns.forEach(function(key) {
      /* Skip columns that we still might not want to show */
      if (excludeKeys.indexOf(key) > -1) {
        return true;
      }

      /* Using dot.notation to pick keys from the data */
      var value = DotObject.pick(key, item) || '';
      htmlRow += '<td '
        + 'title="' + value + '" '
        + 'style="color: ' + item.color + '"'
        + '>' + value + '</td>';
    });

    /* Each individual row is appended to the resulting HTML string */
    htmlRow += '</tr>';
    html += htmlRow;
  });

  /* Actually add the data rows on the table */
  $('.info table tbody')[0].innerHTML = html;
};
