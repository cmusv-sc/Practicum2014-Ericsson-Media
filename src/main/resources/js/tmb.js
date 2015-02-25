(function(parent) {
	
	TMB = {};

	var toUTF8ByteArray = function(src) {
		var bytes = [];

		var s = unescape(encodeURIComponent(src));

		for (var i = 0; i < s.length; i++) {
			var c = s.charCodeAt(i);
			bytes.push(c);
		}

		return bytes;
	};

	var fromUTF8ByteArray = function(arr, offset, length) {
		var str = "";
		if (typeof(offset) == "undefined")
		{
			offset = 0; length = arr.length;
		}
		
		for (var i=offset; i<length; i++)
			str += String.fromCharCode(arr[i]);
		
		return decodeURIComponent( escape( str ) );
	};

	var getBits = function(src, startBit, endBit)
	{
		var mask = (Math.pow(2, endBit - startBit + 1) - 1);
		mask = mask << (32 - endBit);
		var rv = (src & mask) >> (32 - endBit);
		return rv;
	};
	
	var writeInt = function(buf, offset, num) {
		buf[offset++] = getBits(num, 1, 8);
		buf[offset++] = getBits(num, 9, 16);
		buf[offset++] = getBits(num, 17, 24);
		buf[offset++] = getBits(num, 25, 32);
		return 4;
		
	};
	
	var writeBytes = function(buf, offset, bytes) {
		
		buf.set(bytes, offset);
		return bytes.length;
		
	};
	
	var Message = function(){};
	Message.prototype.to = "";
	Message.prototype.from = "";
	Message.prototype.channel = "";
	Message.prototype.op = 0;
	Message.prototype.payload = new Uint8Array(0);
	

	Message.Operation = { 
			HELLO: 0,
			SEND: 1,
			PUB: 2,
			SUB: 3,
			UNSUB: 4,
			BYE: 100
	};
	
	Message.prototype.serialize = function() {

		var to = toUTF8ByteArray(this.to);
		var from = toUTF8ByteArray(this.from);
		var channel = toUTF8ByteArray(this.channel);
		
		var length = 5*4 + 1 + to.length + from.length + channel.length + this.payload.length;
		
		var buf = new Uint8Array(length);
		
		var offset = 0;

		offset += writeInt(buf, offset, length);
		
		buf[offset] = this.op;
		offset++;
		
		offset += writeInt(buf, offset, to.length);
		offset += writeBytes(buf, offset, to);
		
		offset += writeInt(buf, offset, from.length);
		offset += writeBytes(buf, offset, from);
		
		offset += writeInt(buf, offset, channel.length);
		offset += writeBytes(buf, offset, channel);
		
		offset += writeInt(buf, offset, this.payload.length);
		offset += writeBytes(buf, offset, this.payload);
		
		return buf;
		
	};
	
	function readInt(buf) {
		return buf[0] << 24 | buf[1] << 16 | buf[2] << 8 | buf[3];
	}
	
	function toBuffer(buf) {
		var rv = new Uint8Array(buf.length);
		rv.set(buf);
		return rv.buffer;
	} 
	
	Message.deserialize = function(buf, offset) {
		
		buf = new Uint8ClampedArray(buf);
		
		var msg = new Message();
		
		if (!offset) offset=0;
		
		var length = readInt(buf.subarray(offset, offset+=4));
		
		msg.op = buf[offset++];
		
		var toLength = readInt(buf.subarray(offset, offset+=4));
		
		var toBytes = buf.subarray(offset, offset+=toLength);
		msg.to = fromUTF8ByteArray(toBytes, 0, toLength);
		
		var fromLength = readInt(buf.subarray(offset, offset+=4));
		
		var fromBytes = buf.subarray(offset, offset+=fromLength);
		msg.from = fromUTF8ByteArray(fromBytes, 0, fromLength);
		
		var channelLength = readInt(buf.subarray(offset, offset+=4));
		
		var channelBytes = buf.subarray(offset, offset+=channelLength);
		msg.channel = fromUTF8ByteArray(channelBytes, 0, channelLength);
		
		var payloadLength = readInt(buf.subarray(offset, offset+=4));
		
		msg.payload = buf.subarray(offset, offset+=payloadLength);
		
		return msg;
	};
	
	var Client = function(name, uri, debug) {
		this.name = name;
		this.uri = uri;
		var mt = this;
		setTimeout(function() {mt.connect();}, 0);
		if (!debug)
			Trap.Logger.formatter._format = function() {};
	};
	
	Client.prototype.connect = function() {
		var mt = this;
		this._ep = new Trap.ClientEndpoint(this.uri);
		this._ep.onopen = function() {
			var m = new Message();
			m.to = mt.name;
			m.op = Message.Operation.HELLO;
			mt._ep.send(m.serialize());
		};
		
		this._ep.onmessage = function(evt) {
			var m = Message.deserialize(evt.data);
			
			switch(m.op) {
			
				case Message.Operation.HELLO:
					mt.name = m.to
					if (!!mt.onopen)
						mt.onopen();
					break;
	
				case Message.Operation.PUB:
					if (!!mt.onchannel)
						mt.onchannel(m);
					break;
	
				case Message.Operation.SEND:
					if (!!mt.ondirect)
						mt.ondirect(m);
					break;
	
				default:
					return; // No such method
			}
		};
		
		this._ep.onclose = this.onclose;
	};
	
	var dataify = function(data) {
		
		if (typeof(data) == "string")
			return toUTF8ByteArray(data);
		
	};
	
	Client.prototype.send = function(to, data) {
		var m = new Message();
		m.op = Message.Operation.SEND;
		m.to = to;
		m.payload = dataify(data);
		this._ep.send(m.serialize());
	};

	Client.prototype.subscribe = function(channel) {
		var m = new Message();
		m.op = Message.Operation.SUB;
		m.channel = channel;
		this._ep.send(m.serialize());
	};

	Client.prototype.unsubscribe = function(channel) {
		var m = new Message();
		m.op = Message.Operation.UNSUB;
		m.channel = channel;
		this._ep.send(m.serialize());
	};

	Client.prototype.publish = function(channel, data) {
		var m = new Message();
		m.op = Message.Operation.PUB;
		m.channel = channel;
		m.payload = dataify(data);
		this._ep.send(m.serialize());
	};

	Client.prototype.close = function() {
		var m = new Message();
		m.op = Message.Operation.BYE;
		this._ep.send(m.serialize());
	};
	
	var __defineGetter = function(object, getterName, cb)
	{
		
		var newName = "get" + getterName.substr(0,1).toUpperCase() + getterName.substr(1);
		
		if (!cb)
		{
			var privateName = "_" + getterName;
			
			cb = function() {
				return this[privateName];
			};
		}
		
		if (object.__defineGetter__)
		{
			try
			{
				object.__defineGetter__(getterName, cb);
			} catch(e){}
		}
		
		// Also create the getter function as a property of the object
		object[newName] = cb;
	};
	
	__defineGetter(Message.prototype, "string", function() {
		return fromUTF8ByteArray(this.payload);
	});
	
	__defineGetter(Message.prototype, "data", function() {
		return this.payload;
	});
	
	parent.TMB = {};
	parent.TMB.Client = Client;
	
})(self);