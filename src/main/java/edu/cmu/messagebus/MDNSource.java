package edu.cmu.messagebus;

import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpException;

import edu.cmu.messagebus.message.SndDataMessage;

public class MDNSource extends MDNNode {
	
	public MDNSource() {
		super(NodeType.SOURCE);
	}
	@Override
	public void config() throws WarpException {
		super.config();
		Warp.addMethodListener("source/snd_data", "POST", this, "sendData");
	}
	
	public void sendData(SndDataMessage msg) {
		
		if (ClusterConfig.DEBUG) {
			String info = String.format("[DEBUG] MDNSource.sendData(): Start to send data to sink[%s:]", msg.getSinkIP(), msg.getSinkPort());
			System.out.println(info);
		}
		//TODO: Add start to send data to the data-transfer implementation
	}
	
	public static void main(String[] args) {
		MDNSource source = new MDNSource();
		try {
			source.config();
			source.init();
		} catch (WarpException e) {
			e.printStackTrace();
		}
		
	}
	
}
