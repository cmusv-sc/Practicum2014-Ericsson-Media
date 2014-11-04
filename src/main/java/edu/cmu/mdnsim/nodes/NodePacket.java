package edu.cmu.mdnsim.nodes;

import java.nio.ByteBuffer;

/**
 *
 *       0               1               2               3
 *       0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *      +-+-+-----------+---------------+-------------------------------+
 *      |			                   FLAG          		           1| ; The last bit of the flag indicates if the packet if the last one; 1 for yes, 0 for no
 * 32   +-+-+-----------+---------------+-------------------------------+
 *      |                          Message ID                           | ; ID range [0 , 2^31 - 1]
 * 64   +---------------+---------------+-------------------------------+
 *      |                        Data Length                            | ; Length of the payload
 * 96   +---------------------------------------------------------------+
 *      |                         Payload Data                          | ; Optional content, specified by Data Length. Payload begins at offset (in bytes) 12
 *      +---------------------------------------------------------------+
 * 
 *
 */
public class NodePacket {

	private int flag;
	private int packetId;
	private int dataLength;
	private byte[] data;
	
	private static final int HEADER_LENGTH = 4 * 3;
	private static final int PACKET_MAX_LENGTH = 1000;
	
	public NodePacket(int flag, int packetId, int dataLength){
		this.flag = flag;
		this.packetId = packetId;
		dataLength = Math.min(dataLength, PACKET_MAX_LENGTH - HEADER_LENGTH);
		this.dataLength = dataLength;
		data = new byte[dataLength];
	}
	
	/**
	 * Default data length with packet max length - header length
	 * @param flag
	 * @param packetId
	 */
	public NodePacket(int flag, int packetId){
		this.flag = flag;
		this.packetId = packetId;
		dataLength = PACKET_MAX_LENGTH - HEADER_LENGTH;
		data = new byte[dataLength];
	}
	
	public NodePacket(byte[] rawData){
		this.deserialize(rawData);
	}
	
	/**
	 * @return serialized byte array of the Message
	 */
	public byte[] serialize(){
		ByteBuffer byteBuffer = ByteBuffer.allocate(PACKET_MAX_LENGTH);
		byteBuffer.putInt(flag);
		byteBuffer.putInt(packetId);
		byteBuffer.putInt(dataLength);
		byteBuffer.put(data);
		
		return byteBuffer.array();
	}
	
	/**
	 * Deserialize raw data to fill out Message object fields
	 * @param rawData
	 * @return length of the payload data
	 *  	   -1, if the input raw data is not valid
	 */
	public int deserialize(byte[] rawData){
		if(rawData.length < HEADER_LENGTH){
			return -1;
		}
		ByteBuffer byteBuffer = ByteBuffer.wrap(rawData, 0, HEADER_LENGTH);
		flag = byteBuffer.getInt();
		packetId = byteBuffer.getInt();
		dataLength = byteBuffer.getInt();
		
		if(dataLength != rawData.length - HEADER_LENGTH){
			return -1;
		}
		data = new byte[dataLength];
		System.arraycopy(rawData, HEADER_LENGTH, data, 0, dataLength);
		return rawData.length;
	}
	
	public boolean isLast(){
		return (flag & 1) == 1;
	}
	
	public int getFlag() {
		return flag;
	}

	public void setFlag(int flag) {
		this.flag = flag;
	}

	public int getMessageId() {
		return packetId;
	}

	public void setMessageId(int packetId) {
		this.packetId = packetId;
	}

	public int getDataLength() {
		return dataLength;
	}

	public void setDataLength(int dataLength) {
		if(dataLength >= 0){
			this.dataLength = dataLength;
			this.data = new byte[this.dataLength];
		}
	}
	
	public byte[] getData() {
		return data.clone();
	}

	public void setData(byte[] data) {
		this.data = data.clone();
		this.dataLength = data.length;
	}

	public int size(){
		return dataLength + HEADER_LENGTH;
	}
}
