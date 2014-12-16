var db = TAFFY(graph.nodes);
db.sort('size desc');
db().limit(10).each(function(node) {
    console.log(node);
});

setTimeout(function() {
    Mapper.flipV(true);
    var idleFunc = function() {
        var bounds = Mapper.getBounds();

        var c = db().filter({
            x: {
                gte: bounds.minx, 
                lte: bounds.maxx
                }, 
            y: {
                gte: bounds.miny, 
                lte: bounds.maxy
                }
        }).order('size desc').limit(10);

        $('.info ol').empty();
        c.each(function(node) {
            console.log(node);
            $('.info ol').append('<li>' + node.label + '</li>');
        });
    };
    Mapper.setIdleFunc(idleFunc);
    idleFunc();
}, 500);