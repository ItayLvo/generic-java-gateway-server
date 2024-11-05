package gatewayserver;

import connectionservice.ConnectionService;

import java.io.IOException;
import java.nio.ByteBuffer;


public class GatewayServer {
    private final RequestProcessingService RPS;
    private final ConnectionService connectionService;

    public GatewayServer() {
        Parser parser = new CommandParser();
        this.RPS = new RequestProcessingService(parser);
        connectionService = new ConnectionService(RPS);
        setupConnectionService();
    }

    //TODO hard coded or given by user?
    private void setupConnectionService() {
        try {
            connectionService.addTCPConnection("0.0.0.1", 9111);
            connectionService.addUDPConnection("0.0.0.1", 9111);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't set up TCP/UDP socket",e);
        }
    }

    public void startServer() {
        RPS.start();
        connectionService.start();
    }

    public void stopServer() {
        connectionService.stop();
        RPS.stop();
    }


}
