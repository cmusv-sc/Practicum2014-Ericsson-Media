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

//Represents Sigma object
var s = null;

function createGraph(initial_data){
	console.log(initial_data);
	// initial_data = {"edges":
	// [{"source":"1","target":"2","id":"100"},{"source":"1","target":"3","id":"101"}],
	// "nodes":[{"label":"Source","x":0.1,"y":0.1,"id":"1","color":"rgb(0,204,0)","size":6,"tag":"This is source node"},
	//          {"label":"Client","x":0.5,"y":0.5,"id":"2","color":"rgb(0,204,204)","size":6,"tag":"This is client node"},
	//          {"label":"Client2","x":0.2,"y":0.6,"id":"3","color":"rgb(204,0,0)","size":6,"tag":"This is client node2"}
	//          ]}
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
//	updated_data = {"edges":[{"source":"1","target":"2","id":"100","type":"t"},{"source":"1","target":"3","id":"101"}],
//			"nodes":[{"label":"Source","x":0.1,"y":0.1,"id":"1","color":"rgb(0,204,0)","size":6,"tag":"This is source node"},
//			         {"label":"Client","x":0.5,"y":0.5,"id":"2","color":"rgb(0,204,204)","size":6,"tag":"This is client node"},
//			         {"label":"Client2","x":0.2,"y":0.6,"id":"3","color":"rgb(204,0,0)","size":6,"tag":"This is client node2"}]};
	console.log(updated_data);
	s.graph.clear();
	s.graph.read(updated_data);
	s.refresh();  
}


	Warp.on("connect", function() {
		console.log("Now registered at " + Warp.uri);
		//init();
	});
	/*function init(){
		console.log(Warp.uri);
		init = {warpURI: Warp.uri, nodeIP: "", port: "", type: "WEB_CLIENT"};
		Warp.send({to: "warp://cmu-sv:mdn-manager/discover",data: init});
	}*/
	function startSimulation(){		
		start = {sourceNodeName: "node-0000", sinkNodeName: "node-0001", dataSize: 10000, 
					streamRate: 1, streamID: "1"};
		Warp.send({to: "warp://cmu-sv:mdn-manager/start_simulation",data: start});
	}
	
	Warp.on({		
		post: function(m) {
			console.log("POST data: " + m.text); //m.object for JSON
		},
		message: function(m) {
			console.log("Got uncaught message: " + m.text); //m.text for string			
		}		
	});
	Warp.at("/create").on("message", function(m) {
		console.log("Got message: " + m.object);
		createGraph(m.object);
	});
	Warp.at("/update").on("message", function(m) {
		console.log("Got message: " + m.object);
		refreshGraph(m.object);
	});
	//Initialization
$(document).ready(function() {
	$("#btnStart").click(function(e){
		//e.preventDefault();
		//Make a call to start simulation
		//ajax_request(START_URL);
		startSimulation();
		$("#graph-container").css("bottom",0);
		$("#graph-container").css("right",0);

	}); 
});