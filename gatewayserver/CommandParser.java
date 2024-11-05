package gatewayserver;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.Map;

public class CommandParser implements Parser {

    @Override
    public Map.Entry<String, JsonObject> parse(ByteBuffer input) {
        if (input == null) {
            throw new IllegalArgumentException("Invalid request");
        }

        //extract JSON from bytebuffer
        byte[] request = new byte[input.remaining()];
        input.get(request);
        String jsonString = new String(request).trim();

        //parse the JSON string
        JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();

        //extract "type" and "data"
        String requestType = jsonObject.get("Key").getAsString();
        JsonObject requestData = jsonObject.getAsJsonObject("Data");

        //check validity
        if (requestType == null || requestData == null) {
            throw new IllegalArgumentException("Invalid request");
        }

        //return a Map.Entry with the key and data
        return new AbstractMap.SimpleEntry<>(requestType, requestData);
    }

    public static ByteBuffer JsonToByteBuffer(JsonObject responseJson) {
        //convert JsonObject to a JSON string
        String jsonString = responseJson.toString();
        //convert the JSON string to bytes (default UTF-8 encoding)
        byte[] jsonBytes = jsonString.getBytes();
        //wrap the byte array in a ByteBuffer
        return ByteBuffer.wrap(jsonBytes);
    }

    public static int extractStatusFromResponse(ByteBuffer data) {
        String jsonRequest = new String(data.array()).trim();
        JsonObject jsonObject = new Gson().fromJson(jsonRequest, JsonObject.class);
        int statusCode = jsonObject.get("Status").getAsInt();

        return statusCode;
    }
}
