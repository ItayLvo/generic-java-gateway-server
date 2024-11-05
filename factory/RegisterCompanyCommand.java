package factory;

import com.google.gson.JsonObject;
import connectionservice.RespondableChannel;
import gatewayserver.CommandParser;


import java.nio.ByteBuffer;

public class RegisterCompanyCommand implements Command {
    private final JsonObject data;

    public RegisterCompanyCommand(JsonObject data) {
        this.data = data;
    }

    @Override
    public JsonObject getData() {
        return data;
    }

    @Override
    public void execute(RespondableChannel respondableChannel) {
        try {
            System.out.println("registering company command!");
            //get company name from JSON
            String companyName = data.get("Name").getAsString();

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("Status", "200");
            responseJson.addProperty("Info", "Registered company: " + companyName);

            ByteBuffer responseBuffer = CommandParser.JsonToByteBuffer(responseJson);

            //respond to the client with the "serialized" JSON response
            respondableChannel.respond(responseBuffer);
        } catch (Exception e) {
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("Status", "400");
            responseJson.addProperty("Info", "failed to register company: " + e.getMessage());
            respondableChannel.respond(CommandParser.JsonToByteBuffer(responseJson));
        }
    }
}
