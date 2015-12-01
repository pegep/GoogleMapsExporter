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
    Mapper.flipV(true);
    Mapper.setIdleFunc(function() {
      var bounds = Mapper.getBounds();

      visibleNodes = db().filter({
          x: {
              gte: bounds.minx,
              lte: bounds.maxx
              },
          y: {
              gte: bounds.miny,
              lte: bounds.maxy
              }
      }).order('size desc').limit(visibleNodesLimit).get();

      updateTableData(visibleNodes);
    });
}, 500);

/* Initial page load functionality */
$(document).ready(function() {
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
