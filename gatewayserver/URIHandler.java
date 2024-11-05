package gatewayserver;

import com.sun.net.httpserver.HttpExchange;

public interface URIHandler {
    void handle(HttpExchange exchange, String[] uriSegments);
}