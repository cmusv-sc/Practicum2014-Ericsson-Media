var rmb = RMB.builder().seed("trap.transport.http.url=/_connectTrap\ntrap.transport.websocket.wsuri=/_connectTrapWS")
		.id("nc").build();

list = [];

var updateNC = function() {
	
	var addNC = function(name, url) {
		for (var i = 0; i < list.length; i++) {
			if (list[i] === url) {
				return;
			}
		}
		$("#nc-table").append("<tr><td>" + name + "</td><td>" + url + "</td></tr>");
		list.push(url);
	}
	
	var onUpdate = function(msg) {
		for (i = 0; i < msg.object.length; i++) {
			addNC(msg.object[i].ncName, msg.object[i].ncURL);
		}
	}
	rmb.request().method("GET").to("/mdnsim/nodes").execute(onUpdate);
}

rmb.on("open", function() {
	console.log("rmb is open");
	updateNC();
});

$("#button").click(updateNC);