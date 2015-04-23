var rmb = RMB.builder().seed("trap.transport.http.url=/_connectTrap\ntrap.transport.websocket.wsuri=/_connectTrapWS")
		.id("nc").build();

var updateNC = function() {
	
	var onUpdate = function(msg) {
		var list = msg.object;
		console.log(list);

		list.sort(function(a1, a2) {
			if (a1.ncName > a2.ncName) {
				return 1;
			} else if (a1.ncName < a2.ncName) {
				return -1;
			} else {
				return 0;
			}
		});
		
		$('#nc-table').remove('tr');
		$('#nc-table').append('<tr><td>No.</td><td>NC Name</td><td>URL</td></tr>');
		for (var i = 0; i < list.length; i++) {
			$("#nc-table").append("<tr><td>" + (i+1) +"</td><td>" + list[i].ncName + "</td><td>" + list[i].ncURL + "</td></tr>");
		}
	}
	
	rmb.request().method("GET").to("/mdnsim/nodes").execute(onUpdate);
}

rmb.on("open", function() {
	console.log("rmb is open");
	updateNC();
});


$("#button").click(updateNC);