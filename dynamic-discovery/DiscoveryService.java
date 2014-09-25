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
            sb.append(localHostName);
            sb.append(":");
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

                System.out.println(" [x] Node " + message + " is up");

                if (nodeType.equals("master")) {
                	String[] splitMsg = message.split(":");
                	nl.addNode(splitMsg[0], splitMsg[1]);
                	// temp testing. HI message from master
                	if (!splitMsg[1].equals(queueName))
                		this.hiMsg(splitMsg[1]);
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
            String message = "Hi from Master Node";
            tchannel.basicPublish("", qName, null, message.getBytes());
            System.out.println(" [-] Master sent hi to node "+ qName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
