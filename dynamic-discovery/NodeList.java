import java.util.HashMap;

public class NodeList {
    HashMap<String, String> nodeMap;
    Integer numNodes;

    public NodeList() {
        nodeMap = new HashMap<String, String>();
        numNodes = 0;
    }

    public void addNode(String hostname, String queueName) {
        // the number at the end is temporary 
        // to handle nodes running on same host
        String uhostname = hostname.concat(numNodes.toString());
        numNodes++;
        nodeMap.put(uhostname, queueName);
    }

    public void listNode() {

    }
}

