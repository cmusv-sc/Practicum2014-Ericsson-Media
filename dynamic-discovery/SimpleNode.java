
public class SimpleNode {
    public static void main(String[] args) {
        NodeList nl = null;

        if (args.length < 1) {
            System.out.println("Usage: java SimpleNode <master/source/client>");
            System.exit(1);
        }

        String nType = args[0];

        if (nType.equals("master"))
            nl = new NodeList();

        Thread discoverThread = new Thread(new DiscoveryService(nl, nType));
        discoverThread.start();

        try {
            discoverThread.join();
        } catch(Exception e) {
            e.printStackTrace();
        }

    }

}
