import java.io.IOException;
import java.util.HashMap;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.QueueingConsumer;

public class DiscoveryService implements Runnable {

    private static final String DISCOVER_EXCHANGE = "exchange_discover";
    ConnectionFactory factory;
    Connection connection;
    Channel channel;
    String amqQueueName;
    String queueName;
    QueueingConsumer consumer;
    NodeList nl;
    String nodeType;

    public DiscoveryService(NodeList nl, String nodeType) {
        if (nodeType.equals("master")) 
            this.nl = nl;
        this.nodeType = nodeType;

        try {
            factory = new ConnectionFactory();
            factory.setHost("localhost");
            connection = factory.newConnection();
            channel = connection.createChannel();

            // declare the exchange
            channel.exchangeDeclare(DISCOVER_EXCHANGE, "fanout");
            // create a queue and bind the queue to the discover exchange
            amqQueueName = channel.queueDeclare().getQueue();
            channel.queueBind(amqQueueName, DISCOVER_EXCHANGE, "");
            
            queueName = amqQueueName.substring(4);
            channel.queueDeclare(queueName, false, false, false, null);
            
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void publishMyself() {
        try {
            // Note: This may not be sufficient for proper DNS resolution
            // Refer http://stackoverflow.com/questions/7348711/recommended-way-to-get-hostname-in-java?lq=1
            String localHostName = java.net.InetAddress.getLocalHost().getHostName();
            StringBuilder sb = new StringBuilder(50);
            sb.append("NodeStatus#");
            sb.append(localHostName);
            sb.append(":");
            sb.append(nodeType);
            sb.append("#");
            sb.append(queueName);
            String publishName = sb.toString();
            // send out the discovery message to the exchange
            channel.basicPublish(DISCOVER_EXCHANGE, "", null, publishName.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() {
        this.publishMyself();

        try {
            consumer = new QueueingConsumer(channel);
            channel.basicConsume(amqQueueName, true, consumer);
            channel.basicConsume(queueName, true, consumer);

            while(true) {
                QueueingConsumer.Delivery delivery = consumer.nextDelivery();
                String message = new String(delivery.getBody());

                if (message.startsWith("NodeStatus#")) {
                	String[] splitMsg = message.split("#");
	                System.out.println(" [x] Node " + splitMsg[2] + " is up");
	
	                if (nodeType.equals("master") && !splitMsg[2].equals(queueName)) {
	                	nl.addNode(splitMsg[1], splitMsg[2]);
	                	/*
	                	// temp testing. HI message from master
	                	if (!splitMsg[2].equals(queueName))
	                		this.hiMsg(splitMsg[2]);
	                	*/
	                	if(nl.numNodes == 2) {
	                		this.startCmd("sink");
	                		// Just to make sure the sink is up and listening
	                		Thread.sleep(1000);
	                		this.startCmd("source");
	                	}
	                }
                }
                else if (message.startsWith("Info#")) {
                	System.out.println(" [x] "+message);
                }
                else if (message.startsWith("Cmd#")) {
                	String[] splitMsg = message.split("#");
                	System.out.println(message);
                	if (this.nodeType.equals("source")) {
                		// source
                		String serverIp = "localhost";
                		int packageSize = 10240;
                		int rate = 5;
                		int serverPort = Integer.parseInt(splitMsg[2]);
                		SourceNode sourceNode = new SourceNode();
                		sourceNode.send(serverIp, serverPort,packageSize, rate);
                	} else if (this.nodeType.equals("sink")) {
                		// sink
                		int listenPort = Integer.parseInt(splitMsg[2]);
                		SinkNode sinkNode = new SinkNode();
                		sinkNode.listenAtPort(listenPort);
                	}
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void hiMsg(String qName) {
        try {
            ConnectionFactory tfactory = new ConnectionFactory();
            tfactory.setHost("localhost");
            Connection tconnection = tfactory.newConnection();
            Channel tchannel = connection.createChannel();

            tchannel.queueDeclare(qName, false, false, false, null);
            String message = "Info#Hi from Master Node";
            tchannel.basicPublish("", qName, null, message.getBytes());
            System.out.println(" [-] Master sent "+message+" to "+ qName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void startCmd(String nType) {
    	// In case of problem discovering source queue, message is sent to master
    	String qName = this.queueName;
    	for (String hostname : this.nl.nodeMap.keySet()) {
    		String[] splitHostname = hostname.split(":");
    		if (splitHostname[1].equals(nType)) {
    			qName = this.nl.nodeMap.get(hostname);
    			break;
    		}
    	}
    	
        try {
            String message = "Cmd#start#5554";
            channel.basicPublish("", qName, null, message.getBytes());
            System.out.println(" [-] Master sent "+message+" to "+ qName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
