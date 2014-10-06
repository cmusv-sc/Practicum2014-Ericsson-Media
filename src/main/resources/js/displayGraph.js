<script src="js/sigma.min.js"></script>
<script src="js/sigma.parsers.json.min.js"></script>
<script src="js/sigma.layout.forceAtlas2.min.js"></script>
<script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1.6.2/jquery.min.js"></script>
sigma.utils.pkg('sigma.canvas.edges');
sigma.canvas.edges.t = function(edge, source, target, context, settings) {
	var color = edge.color,
	prefix = settings('prefix') || '',
	edgeColor = settings('edgeColor'),
	defaultNodeColor = settings('defaultNodeColor'),
	defaultEdgeColor = settings('defaultEdgeColor');
	if (!color)
		switch (edgeColor) {
		case 'source':
			color = source.color || defaultNodeColor;
			break;
		case 'target':
			color = target.color || defaultNodeColor;
			break;
		default:
			color = defaultEdgeColor;
		break;
		}
	context.strokeStyle = color;
	context.lineWidth = edge[prefix + 'size'] || 1;
	context.lineWidth = 5;
	context.beginPath();
	context.moveTo(
			source[prefix + 'x'],
			source[prefix + 'y']
	);
	context.lineTo(
			target[prefix + 'x'],
			target[prefix + 'y']
	);
	// context.lineTo(
	//   source[prefix + 'x'],
	//   target[prefix + 'y']
	// );
	// context.lineTo(
	//   target[prefix + 'x'],
	//   target[prefix + 'y']
	// );
	context.stroke();
};
initial_data = {"edges":
	[{"source":"1","target":"2","id":"100"},{"source":"1","target":"3","id":"101"}],
	"nodes":[{"label":"Source","x":0.1,"y":0.1,"id":"1","color":"rgb(0,204,0)","size":6,"tag":"This is source node"},
	         {"label":"Client","x":0.5,"y":0.5,"id":"2","color":"rgb(0,204,204)","size":6,"tag":"This is client node"},
	         {"label":"Client2","x":0.2,"y":0.6,"id":"3","color":"rgb(204,0,0)","size":6,"tag":"This is client node2"}
	         ]}


/*sigma.parsers.json('data/sample.json', {
      container: 'graph-container'
    });
 */
var s = null;
// Start the ForceAtlas2 algorithm:
//s.startForceAtlas2();
var START_URL = "localhost:8080/start";
var UPDATE_URL = "localhost:8080/poll";
function ajax_request(url) {
	$.ajax({
		url: url, 
		dataType: 'json',
		error: function(xhr_data) {
			// terminate the script
		},
		success: function(xhr_data) {
			alert(xhr_data);
			if (xhr_data.status == 'pending') {
				// continue polling
				setTimeout(function() { ajax_request(UPDATE_URL); }, 100); //100 milliseconds
			}else {
				if(url == UPDATE_URL){
					updated_data = xhr_data.data;
					refreshGraph(updated_data);
				}else if(url == START_URL){
					initial_data = xhr_data.data;
					createGraph(initial_data);
					//Start polling
					ajax_request(UPDATE_URL);              
				}
			}
		},
		contentType: 'application/json'
	});
}

function createGraph(initial_data){
	s = new sigma({
		graph: initial_data,
		//container: 'graph-container'
		renderer: {
			container: document.getElementById('graph-container'),
			type: 'canvas'
		}
	});
	s.bind('overNode', function(e) {
		console.log(e.type, e.data.node.label, e.data.captor);
		$('<p class="tooltip"></p>')
		.text(e.data.node.tag)
		.appendTo('body')
		.fadeIn('slow');
		var mousex = e.data.captor.clientX; 
		var mousey = e.data.captor.clientY; 
		$('.tooltip')
		.css({ top: mousey, left: mousex })
	});
	s.bind('outNode', function(e) {
		console.log(e.type, e.data.node.label, e.data.captor);
		$('.tooltip').remove();
	});
}
function refreshGraph(updated_data){
	updated_data = {"edges":[{"source":"1","target":"2","id":"100","type":"t"},{"source":"1","target":"3","id":"101"}],
			"nodes":[{"label":"Source","x":0.1,"y":0.1,"id":"1","color":"rgb(0,204,0)","size":6,"tag":"This is source node"},
			         {"label":"Client","x":0.5,"y":0.5,"id":"2","color":"rgb(0,204,204)","size":6,"tag":"This is client node"},
			         {"label":"Client2","x":0.2,"y":0.6,"id":"3","color":"rgb(204,0,0)","size":6,"tag":"This is client node2"}]};
	s.graph.clear();
	s.graph.read(updated_data);
	s.refresh();  
}

////Initialization
//$(document).ready(function() {
//	$("#btnStart").click(function(e){
//		//e.preventDefault();
//		//Make a call to start simulation
//		ajax_request(START_URL);
//		$("#graph-container").css("bottom",0);
//		$("#graph-container").css("right",0);
//
//	}); 
//});

