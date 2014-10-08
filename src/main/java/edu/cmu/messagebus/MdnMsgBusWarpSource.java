package edu.cmu.messagebus;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.WarpURI;
import com.ericsson.research.warp.util.JSON;
import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.messagebus.message.SndDataMessage;
import edu.cmu.messagebus.message.SourceReportMessage;
import edu.cmu.nodes.MdnSourceNode;

public class MdnMsgBusWarpSource extends MdnMsgbusWarpNode {
	
	MdnSourceNode sourceNode;
	
	public MdnMsgBusWarpSource() {
		super(NodeType.SOURCE);
		sourceNode = new MdnSourceNode();
	}
	@Override
	public void config() throws WarpException {
		super.config();
		Warp.addMethodListener("source/snd_data", "POST", this, "sendData");
	}
	
	public void sendData(SndDataMessage msg) {
		try {
			final String streamId = msg.getStreamID();
			final int destPort = msg.getSinkPort();
			final int bytesToTransfer = msg.getDataSize();
			final int rate = msg.getDataRate();
			final InetAddress destAddr = InetAddress.getByName(msg.getSinkIP());
			
			
			if (ClusterConfig.DEBUG) {
				String info = String.format("[DEBUG] MDNSource.sendData(): Start to send data to sink[%s:]", msg.getSinkIP(), msg.getSinkPort());
				System.out.println(info);
			}
			
			//TODO: Add start to send data to the data-transfer implementation
			WarpThreadPool.executeCached(new Runnable() {
				@Override
				public void run() {
					MdnMsgBusWarpSource.this.sourceNode.sendAndReport(
							streamId, destAddr, destPort, bytesToTransfer, rate, MdnMsgBusWarpSource.this
							);
				}
			});
			
			} catch (UnknownHostException uhe) {
				// TODO Auto-generated catch block
				uhe.printStackTrace();
			}
	}
	
	public void sourceReport(SourceReportMessage srcRepMsg) {
		try {
			Warp.send("/", WarpURI.create("warp://cmu-sv:mdn-manager/source_report"),"POST", JSON.toJSON(srcRepMsg).getBytes());
		} catch (WarpException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		MdnMsgBusWarpSource source = new MdnMsgBusWarpSource();
		try {
			source.config();
			source.init();
		} catch (WarpException e) {
			e.printStackTrace();
		}
		
	}
	
}
