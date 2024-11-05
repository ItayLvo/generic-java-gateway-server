package commandplugins;

import com.google.gson.JsonObject;
import connectionservice.RespondableChannel;
import factory.Command;
import gatewayserver.CommandParser;

import java.nio.ByteBuffer;

public class RegisterProductCommand implements Command {
    private final JsonObject data;

    public RegisterProductCommand(JsonObject data) {
        this.data = data;
    }

    @Override
    public JsonObject getData() {
        return data;
    }

    @Override
    public void execute(RespondableChannel respondableChannel) {
        System.out.println("REGISTER PRODUCT command!\t");
        //get company name from JSON
        String productName = data.get("Name").getAsString();

        JsonObject responseJson = new JsonObject();
        responseJson.addProperty("Status", "success");
        responseJson.addProperty("Info", "Registered product: " + productName);

        ByteBuffer responseBuffer = CommandParser.JsonToByteBuffer(responseJson);

        //respond to the client with the "serialized" JSON response
        respondableChannel.respond(responseBuffer);
    }


}
