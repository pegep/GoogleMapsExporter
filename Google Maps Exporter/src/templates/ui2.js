/* Initialize TaffyDB from graph JSON data */
var db = TAFFY(graph.nodes);
db.sort('size desc');

/* Limit how many nodes to show on node table */
var visibleNodesLimit = 100;

/* Initialize Mapper */
setTimeout(function() {
    Mapper.flipV(true);
    Mapper.setIdleFunc(function() {
      var bounds = Mapper.getBounds();

      var visibleNodes = db().filter({
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

/* Global variables */
var distinctColumns; // Columns to show in table listing
var excludeKeys = ['___id', '___s']; // Taffy variables?

/* Initial page load functionality */
$(document).ready(function() {
  distinctColumns = Object.keys(DotObject.dot(graph.nodes[0]));
  updateTableData(graph.nodes);
});

/* Helper functions */
function updateTableData(data, sortBy) {
  $('.info table thead').empty();
  $('.info table tbody').empty();

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
