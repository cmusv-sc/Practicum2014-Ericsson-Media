package edu.cmu.mdnsim.nodes;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import edu.cmu.mdnsim.reporting.SystemClock;

/**
 *	A packet that can hold a flag, packet id and data length fields as well as the payload.
 *  <p>The header of the packet takes 12 bytes. The total size of the packet is limited by PACKET_MAX_LENGTH.
 *  That means the max size of payload is PACKET_MAX_LENGTH - HEADER_LENGTH. If a larger data field is set, when 
 *  serialize the object, a BufferOverflowException will be thrown. Then the program will fail.
 *  
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
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class NodePacket {

	private int flag;
	private int packetId;
	private int dataLength;
	private byte[] data;
	private long transmitTime;
	private long forwardTime;
	
	
	
	//TODO: Update the HEADER_LENGTH
	public static final int HEADER_LENGTH = 28;
	public static final int MAX_PACKET_LENGTH = 1000;
	
	/**
	 * Construct a packet instance based on flag, packetId and totalLength parameters. Data field will be left blank.
	 * Total length of a packet must be in range[HEADER_LENGTH, MAX_PACKET_LENGTH].
	 * @param flag
	 * @param packetId
	 * @param totalLength
	 * @throws IllegalArgumentException when packetId is lower than zero or totalLength is out the range[HEADER_LENGTH, MAX_PACKET_LENGTH]
	 */
	public NodePacket(int flag, int packetId, int totalLength){
		if(packetId < 0 || totalLength < HEADER_LENGTH || totalLength > MAX_PACKET_LENGTH){
			throw new IllegalArgumentException();
		}
		this.flag = flag;
		this.packetId = packetId;
		dataLength = totalLength - HEADER_LENGTH;
		data = new byte[dataLength];
	}
	
	/**
	 * Initialize a NodePacket instance with default data size, PACKET_MAX_LENGTH - HEADER_LENGTH
	 * @param flag
	 * @param packetId
	 */
	public NodePacket(int flag, int packetId){
		this.flag = flag;
		this.packetId = packetId;
		dataLength = MAX_PACKET_LENGTH - HEADER_LENGTH;
		data = new byte[dataLength];
	}
	
	/**
	 * Initialize an instance based on a byte array
	 * @param rawData 
	 * @throws IllegalArgumentException when packetId is lower than zero or totalLength is out the range[HEADER_LENGTH, MAX_PACKET_LENGTH]
	 */
	public NodePacket(byte[] rawData){
		
		if(rawData == null){
			throw new NullPointerException("input null as a byte array for raw data");
		}
		
		if(rawData.length < HEADER_LENGTH || rawData.length > MAX_PACKET_LENGTH){
			throw new IllegalArgumentException();
		}
		
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
		byteBuffer.putLong(transmitTime);
		byteBuffer.putLong(forwardTime);
		byteBuffer.put(data);
		
		return byteBuffer.array();
	}
	
	/**
	 * Deserialize raw data to fill out variable fields of the instance. 
	 * @param rawData
	 * @return length of the total length
	 */
	private int deserialize(byte[] rawData){
		
		assert(rawData != null);
		
		assert(rawData.length >= HEADER_LENGTH && rawData.length <= MAX_PACKET_LENGTH);

		ByteBuffer byteBuffer = ByteBuffer.wrap(rawData, 0, HEADER_LENGTH);
		flag = byteBuffer.getInt();
		packetId = byteBuffer.getInt();
		dataLength = byteBuffer.getInt();
		transmitTime = byteBuffer.getLong();
		forwardTime = byteBuffer.getLong();
		
		if(dataLength >  MAX_PACKET_LENGTH - HEADER_LENGTH){
			dataLength =  MAX_PACKET_LENGTH - HEADER_LENGTH;
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
		if(packetId < 0){
			throw new IllegalArgumentException();
		}
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
		if(data == null){
			throw new NullPointerException("set null for data field");
		}

		this.data = data.clone();
		this.dataLength = data.length;
	}

	public int size(){
		return dataLength + HEADER_LENGTH;
	}
	
	public long getTransmitTime() {
		return this.transmitTime;
	}
	
	public long getForwardTime() {
		return this.forwardTime;
	}
	
	public void setTransmitTime() {
		transmitTime = SystemClock.currentTimeMillis();
		forwardTime = transmitTime;
	}
	
	public void setForwardTime() {
		this.forwardTime = SystemClock.currentTimeMillis();
	}
	
}
