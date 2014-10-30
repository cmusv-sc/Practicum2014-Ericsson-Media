package edu.cmu.config;

import java.nio.ByteBuffer;

/**
 *
 *       0               1               2               3
 *       0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *      +-+-+-----------+---------------+-------------------------------+
 *      |			                   FLAG          		           1| ; The last bit of the flag indicates if the packet if the last one; 1 for yes, 0 for no
 * 32   +-+-+-----------+---------------+-------------------------------+
 *      |                          Message ID                           |
 * 64   +---------------+---------------+-------------------------------+
 *      |                        Content Length                         | ; Length of the payload that begins *after* AUTHLEN
 * 96   +---------------------------------------------------------------+
 *      |                         Payload Data                          | ; Optional content, specified by Content Length. Payload begins at offset (in bytes) 12+AUTHLEN
 *      +---------------------------------------------------------------+
 * 
 *
 */
public class NodeMessage {
	private boolean last;
	private int flag;
	private int messageId;
	private int contentLength;
	private byte[] data;
	
	private final int HEADER_LENGTH = 12;
	
	public NodeMessage(boolean last, int messageId, int contentLength){
		flag = last? 1 : 0;
		this.last = last;
		this.messageId = messageId;
		this.contentLength = contentLength;
		data = new byte[contentLength];
	}
	
	public byte[] serialize(){
		ByteBuffer byteBuffer = ByteBuffer.allocate(HEADER_LENGTH + contentLength);
		byteBuffer.putInt(flag);
		byteBuffer.putInt(messageId);
		byteBuffer.putInt(contentLength);
		byteBuffer.put(data);
		
		return byteBuffer.array();
	}
	
	public int deserialize(byte[] rawData){
		if(rawData.length < HEADER_LENGTH){
			return -1;
		}
		ByteBuffer byteBuffer = ByteBuffer.wrap(rawData, 0, HEADER_LENGTH);
		flag = byteBuffer.getInt();
		last = flag == 1? true: false;
		messageId = byteBuffer.getInt();
		contentLength = byteBuffer.getInt();
		
		System.arraycopy(rawData, HEADER_LENGTH, data, 0, contentLength);
		return rawData.length;
	}
	
	public boolean isLast(){
		return last;
	}
}
