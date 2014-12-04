package edu.cmu.mdnsim.nodes;

import java.nio.ByteBuffer;
import java.util.Arrays;

import edu.cmu.mdnsim.exception.TotalLengthShorterThanHeaderException;

/**
 *	A packet that can hold a flag, packet id and data length fields as well as the payload.
 *  The header of the packet takes 12 bytes. The total size of the packet is limited by PACKET_MAX_LENGTH.
 *  That means the max size of payload is PACKET_MAX_LENGTH - HEADER_LENGTH
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
	
	public static final int HEADER_LENGTH = 4 * 3;
	public static final int PACKET_MAX_LENGTH = 1000;
	
	public NodePacket(int flag, int packetId, int totalLength){

		this.flag = flag;
		this.packetId = packetId;
		int dataLength = 0;
		// To guarantee the total size is in range [HEADER_LENGTH, PACKET_MAX_LENGTH]
		if(totalLength > HEADER_LENGTH){
			dataLength = Math.min(totalLength - HEADER_LENGTH, PACKET_MAX_LENGTH - HEADER_LENGTH);
		}
		this.dataLength = dataLength;
		data = new byte[dataLength];
	}
	
	/**
	 * Initialize a NodePacket instance with default payload size, PACKET_MAX_LENGTH - HEADER_LENGTH
	 * @param flag
	 * @param packetId
	 */
	public NodePacket(int flag, int packetId){
		this.flag = flag;
		this.packetId = packetId;
		dataLength = PACKET_MAX_LENGTH - HEADER_LENGTH;
		data = new byte[dataLength];
	}
	
	/**
	 * Initialize an instance based on a byte array
	 * @param rawData 
	 */
	public NodePacket(byte[] rawData){
		this.deserialize(rawData);
	}
	
	/**
	 * Serialize the NodePacket object to a byte array.
	 * @return serialized byte array of the Message
	 * @throws BufferOverflowException - If the data size exceeds PACKET_MAX_LENGTH - HEADER_LENGTH.
	 * If want to set a larger data into the instance, please first set the final variable  PACKET_MAX_LENGTH
	 */
	public byte[] serialize(){
		ByteBuffer byteBuffer = ByteBuffer.allocate(HEADER_LENGTH + dataLength);
		byteBuffer.putInt(flag);
		byteBuffer.putInt(packetId);
		byteBuffer.putInt(dataLength);
		byteBuffer.put(data);
		
		return byteBuffer.array();
	}
	
	/**
	 * Deserialize raw data to fill out variable fields. If the payload is larger than PACKET_MAX_LENGTH - HEADER_LENGTH,
	 * the extra part will be discarded. Only the portion that can fit in the max payload will be saved.
	 * @param rawData
	 * @return length of the total length
	 */
	public int deserialize(byte[] rawData){
		if(rawData.length < HEADER_LENGTH){
			throw new TotalLengthShorterThanHeaderException();
		}
		ByteBuffer byteBuffer = ByteBuffer.wrap(rawData, 0, HEADER_LENGTH);
		flag = byteBuffer.getInt();
		packetId = byteBuffer.getInt();
		dataLength = byteBuffer.getInt();
		if(dataLength >  PACKET_MAX_LENGTH - HEADER_LENGTH){
			dataLength =  PACKET_MAX_LENGTH - HEADER_LENGTH;
		}
		data = Arrays.copyOfRange(rawData, HEADER_LENGTH, HEADER_LENGTH + dataLength);
		return HEADER_LENGTH + dataLength;
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
	
	public byte[] getData() {
		return data.clone();
	}

	/**
	 * Defensive copy the input byte array to instant field.
	 * @param data the source array that will be copied into the field
	 */
	public void setData(byte[] data) {
		this.data = data.clone();
		this.dataLength = data.length;
	}

	public int size(){
		return dataLength + HEADER_LENGTH;
	}
}
