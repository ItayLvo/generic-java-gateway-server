package gatewayserver;

import com.google.gson.JsonObject;

import java.nio.ByteBuffer;
import java.util.Map;

public interface Parser {
    Map.Entry<String, JsonObject> parse(ByteBuffer input);
}
