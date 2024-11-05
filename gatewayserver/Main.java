package gatewayserver;


public class Main {
    public static void main(String[] args) throws InterruptedException {
        GatewayServer server = new GatewayServer();
        Thread.sleep(1000);
        server.startServer();
        Thread.sleep(5000);
        server.stopServer();
    }
}