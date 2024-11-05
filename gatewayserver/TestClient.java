package gatewayserver;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class TestClient {
    public static void main(String[] args) throws IOException, InterruptedException {
        UDPTest();
        Thread.sleep(1500);
        TCPTest();

    }

    private static void TCPTest() throws IOException {
        SocketChannel channel = SocketChannel.open();
        InetSocketAddress serverSocketAddress = new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 9111);
        channel.connect(serverSocketAddress);

        JsonObject request = new JsonObject();
        request.addProperty("Key", "registerCompany");

        JsonObject data = new JsonObject();
        data.addProperty("Name", "amazon");
        data.addProperty("Number of products", 5);

        request.add("Data", data);

        String requestString = new Gson().toJson(request);
        channel.write(ByteBuffer.wrap(requestString.getBytes(StandardCharsets.UTF_8)));

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        channel.read(buffer);

        JsonObject response = new Gson().fromJson(new String(buffer.array()).trim(), JsonObject.class);
        System.out.println(response);
    }


    private static void UDPTest() throws IOException {
        DatagramChannel channel = DatagramChannel.open();
        InetSocketAddress serverSocketAddress = new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 9111);

        JsonObject request = new JsonObject();
        request.addProperty("Key", "registerCompany");

        JsonObject data = new JsonObject();
        data.addProperty("Name", "google");
        data.addProperty("Number of products", 3);

        request.add("Data", data);

        String requestString = new Gson().toJson(request);
        channel.send(ByteBuffer.wrap(requestString.getBytes(StandardCharsets.UTF_8)), serverSocketAddress);

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        channel.receive(buffer);

        JsonObject response = new Gson().fromJson(new String(buffer.array()).trim(), JsonObject.class);
        System.out.println(response);
    }
}
