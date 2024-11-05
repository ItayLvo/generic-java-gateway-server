package connectionservice;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import gatewayserver.CommandParser;
import gatewayserver.RequestProcessingService;
import gatewayserver.URIHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


public class ConnectionService {
    private final RequestProcessingService requestProcessingService;
    private final Set<SelectableChannel> openChannels = new HashSet<>();
    private final Selector selector;
    private volatile boolean isConnectionServiceRunning = false;
    private static final int BUFFER_SIZE = 8192;    //2^13B = 8KB
    private final Thread listenerThread = new Thread(this::listen);
    private HttpService httpService;

    public ConnectionService(RequestProcessingService requestProcessingService) {
        this.requestProcessingService = requestProcessingService;
        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException("Selector failed to open", e);
        }
    }

    public void start() {
        isConnectionServiceRunning = true;
        listenerThread.start();
        try {
            httpService = new HttpService("127.0.0.1", 8001);   //TODO hard-coded vs argument?
            httpService.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start HTTP service", e);
        }
    }

    private void listen() {
        try {
            while (isConnectionServiceRunning) {
                //wait for events - blocking until an event is ready
                selector.select();
                //iterate over all selected keys (events)
                for (SelectionKey key : selector.selectedKeys()) {
                    ChannelHandler handler = (ChannelHandler) key.attachment();
                    handler.handle(key.channel());
                }
                //clear the selected keys to prepare for the next set of events
                selector.selectedKeys().clear();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed during selector loop", e);
        } finally {
            cleanupOpenChannels();
        }

    }


    public void stop() {
        if (!isConnectionServiceRunning) {
            throw new IllegalStateException("Connection service is not running");
        }

        isConnectionServiceRunning = false;
        selector.wakeup();
        try {
            //wait for the selector to exit loop and clean up
            listenerThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        cleanupOpenChannels();

        httpService.stop();
    }


    public void addTCPConnection(String hostname, int port) throws IOException {
        if (isConnectionServiceRunning) {
            throw new IllegalStateException("ConnectionService is running, can't add new connections");
        }
        ServerSocketChannel tcpServerSocketChannel = ServerSocketChannel.open();
        openChannels.add(tcpServerSocketChannel);
        tcpServerSocketChannel.configureBlocking(false);
        //bind the server to a specific port number
        tcpServerSocketChannel.bind(new InetSocketAddress(hostname, port));
        //register the server channel with the selector for "accept" events (new connections)
        tcpServerSocketChannel.register(selector, SelectionKey.OP_ACCEPT, new TCPConnector());
    }

    public void addUDPConnection(String hostname, int port) throws IOException {
        if (isConnectionServiceRunning) {
            throw new IllegalStateException("ConnectionService is running, can't add new connections");
        }
        DatagramChannel udpChannel = DatagramChannel.open();
        openChannels.add(udpChannel);
        udpChannel.configureBlocking(false);
        //bind the DatagramChannel to the local address for listening to inbound UDP packets
        udpChannel.bind(new InetSocketAddress(hostname, port));
        //register the channel with the selector for reading events
        udpChannel.register(selector, SelectionKey.OP_READ, new UDPHandler());
    }


    private void cleanupOpenChannels() {
        //close all client tcpClientChannels when the server shuts down
        for (SelectableChannel channel : openChannels) {
            try {
                channel.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        openChannels.clear();
    }


    private class TCPConnector implements ChannelHandler {
        @Override
        public void handle(SelectableChannel channel) {
            try {
                if (channel instanceof ServerSocketChannel) {
                    //accept the new connection and create a SocketChannel for the client
                    SocketChannel client = ((ServerSocketChannel) channel).accept();
                    openChannels.add(client);   //add all client sockets to collection that will be all closed when server dies
                    client.configureBlocking(false);
                    //register the client channel with the selector, interested in reading from the client
                    client.register(selector, SelectionKey.OP_READ, new TCPHandler());
                }
            } catch (IOException e) {
                throw new RuntimeException("Exception when accepting new TCP connection", e);
            }
        }
    }


    private class TCPHandler implements ChannelHandler, RespondableChannel {
        private SocketChannel clientChannel;

        @Override
        public void handle(SelectableChannel channel) {
            if (channel instanceof SocketChannel) {
                clientChannel = (SocketChannel) channel;
                ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
                int bytesRead = 0;
                try {
                    bytesRead = ((SocketChannel) channel).read(byteBuffer); //TODO while loop wrap?
                } catch (IOException e) {
                    throw new RuntimeException("Error reading the TCP message", e);
                }
                if (bytesRead == -1) {
                    //client closed the connection: close the channel and remove it from open channels set
                    try {
                        clientChannel.close();
                        openChannels.remove(clientChannel);
                    } catch (IOException e) {
                        throw new RuntimeException("Error closing the channel", e);
                    }
                }
                byteBuffer.flip();

                requestProcessingService.handleRequest(byteBuffer, this);
            }
        }

        @Override
        public void respond(ByteBuffer data) {
            try {
                clientChannel.write(data);
            } catch (IOException e) {
                throw new RuntimeException("Couldn't respond to client", e);
            }
        }
    }


    private class UDPHandler implements ChannelHandler {
        @Override
        public void handle(SelectableChannel channel) {
            if (channel instanceof DatagramChannel) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
                //handle message from client:
                try {
                    InetSocketAddress clientAddress = (InetSocketAddress) ((DatagramChannel) channel).receive(byteBuffer);
                    UdpResponse udpResponseHandler = new UdpResponse((DatagramChannel) channel, clientAddress);
                    byteBuffer.flip();
                    requestProcessingService.handleRequest(byteBuffer, udpResponseHandler);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        //this class keeps the information needed for the server to respond to a UDP client request
        private class UdpResponse implements RespondableChannel {
            private final DatagramChannel udpClientChannel;
            private final SocketAddress clientAddress;

            public UdpResponse(DatagramChannel udpClientChannel, SocketAddress clientAddress) {
                this.udpClientChannel = udpClientChannel;
                this.clientAddress = clientAddress;
            }

            @Override
            public void respond(ByteBuffer data) {
                try {
                    udpClientChannel.send(data, clientAddress);
                } catch (IOException e) {
                    throw new RuntimeException("Couldn't respond to client", e);
                }
            }
        }
    }


    private class HttpService {
        private final HttpServer httpServer;

        public HttpService(String ip, int port) throws IOException {
            httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getByName(ip), port), 0);
            initHttpServer();
        }

        private void initHttpServer() {
            //register URIs (resources) and their handlers
            registerContexts();
            httpServer.setExecutor(null);
        }

        private void start() {
            httpServer.start();
            System.out.println("Server started on address " + httpServer.getAddress());
        }

        private void stop() {
            httpServer.stop(0);
        }

        private void registerContexts() {
//            httpServer.createContext("/company", new HttpHandlerCompany());
//            httpServer.createContext("/companies", new HttpHandlerCompanies());

            //set one default context "/" for all requests
            httpServer.createContext("/", new InitialHttpHandler());
        }

        //initial HTTP handler for any URI request ("/")
        private class InitialHttpHandler implements HttpHandler {
            @Override
            public void handle(HttpExchange httpExchange) {
                System.out.println("inside initial response handler");
                //initializing the composition/chain of responsibility handlers
                CompanyHandler companyHandler = new CompanyHandler(new ProductHandler(null));
                String[] uriSegments = httpExchange.getRequestURI().getPath().split("/");
                //calling the first handler of the chain
                companyHandler.handle(httpExchange, uriSegments);
            }
        }


        //HttpHandler implementation for "company" URI
        private class CompanyHandler implements URIHandler {
            private final URIHandler productHandler;

            public CompanyHandler(URIHandler nextHandler) {
                productHandler = nextHandler;
            }

            @Override
            public void handle(HttpExchange exchange, String[] uriSegments) {
                //test prints:
                System.out.println("inside company handler:");
                System.out.println(exchange.getRequestURI().getPath());
                System.out.println(Arrays.toString(uriSegments));

                if (uriSegments.length < 1) {
                    //if the URI is empty ("/")
                    respondNotFound(exchange);
                    return;
                }

                //check and handle "/companies"
                if (uriSegments[1].equals("companies")) {
                    if (uriSegments.length != 2) {
                        //can't have URI segments after "companies"
                        respondNotFound(exchange);
                        return;
                    } else {
                        handleCompanies(exchange);
                    }
                }

                //first URI segment must be either "companies" or "company"
                if (!uriSegments[1].equals("company")) {
                    respondNotFound(exchange);
                    return;
                }

                //handle "/company"
                if (uriSegments.length == 2) {
                    handleCompany(exchange, uriSegments);
                    return;
                }

                //handle "/company/{...}"
                if (isValidID(uriSegments[2])) {
                    if (uriSegments.length == 3) {
                        //  "/company/{id}}"
                        handleCompany(exchange, uriSegments);
                    } else {
                        //URI has a product segment -> delegate work to next handler
                        productHandler.handle(exchange, uriSegments);
                    }
                }
                //if company ID is not valid:
                else {
                    respondNotFound(exchange);
                }
            }
        }


        private class ProductHandler implements URIHandler {
            private final URIHandler IOTDeviceHandler;

            public ProductHandler(URIHandler nextHandler) {
                IOTDeviceHandler = nextHandler;
            }

            @Override
            public void handle(HttpExchange exchange, String[] uriSegments) {

                //check product segment validity
                if (uriSegments.length < 4) {
                    respondNotFound(exchange);
                    return;
                }

                //check and handle "/company/{id}/products"
                if (uriSegments[3].equals("products")) {
                    if (uriSegments.length != 4) {
                        //can't have URI segments after "products"
                        respondNotFound(exchange);
                        return;
                    } else {
                        handleProducts(exchange);
                    }
                }

                //product URI segment must start with either "products" or "product"
                if (uriSegments.length < 5 || !uriSegments[3].equals("product")) {
                    respondNotFound(exchange);
                    return;
                }

                //process the "company" part
                if (isValidID(uriSegments[4])) {
                    if (uriSegments.length == 5) {
                        //  "/company/{id}/product/{id}"
                        handleProduct(exchange);
                    } else {
                        //delegate work to next handler
                        IOTDeviceHandler.handle(exchange, uriSegments);
                    }
                }
                //if product ID is not valid:
                else {
                    respondNotFound(exchange);
                }
            }
        }


        private boolean isValidID(String id) {
            return (id != null &&
                    !id.isEmpty() &&
                    id.chars().allMatch(Character::isDigit) &&
                    Integer.parseInt(id) > 0);
        }


        /////////////// concrete handlers ///////////////

        private void respondNotFound(HttpExchange exchange) {
            try {
                exchange.sendResponseHeaders(404, -1);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        private void handleCompanies(HttpExchange exchange) {
            if (exchange.getRequestMethod().equals("GET")) {
                //TODO handle GET companies
            } else if (exchange.getRequestMethod().equals("POST")) {
                respondNotFound(exchange);
            }
        }


        private void handleProducts(HttpExchange exchange) {
            if (exchange.getRequestMethod().equals("GET")) {
                //TODO handle GET products
            } else if (exchange.getRequestMethod().equals("POST")) {
                respondNotFound(exchange);
            }
        }


        private void handleCompany(HttpExchange exchange, String[] uriSegments) {
            if (exchange.getRequestMethod().equals("GET")) {
                //TODO handle GET company
            } else if (exchange.getRequestMethod().equals("POST")) {
                if (uriSegments.length == 2) {
                    handlePostCompany(exchange);
                } else {
                    respondNotFound(exchange);
                }
            }
        }

        private void handlePostCompany(HttpExchange exchange) {
            System.out.println("inside POST handler");
            //read the request body and parse it as JSON
            JsonObject httpBodyJson;
            try (InputStream inputStream = exchange.getRequestBody()) {
                //parse the input stream to a JSON object using Gson
                httpBodyJson = JsonParser.parseReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException e) {
                throw new RuntimeException("Failed to read HTTP request body", e);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse JSON from HTTP request body", e);
            }

            //now `requestJson` contains the JSON object from the request body
            System.out.println(httpBodyJson.toString());  //for debugging: Print out the parsed JSON

            //build the request JSON from Key=registerCompany and Data=<body of JSON request>
            JsonObject requestJson = new JsonObject();
            requestJson.addProperty("Key", "registerCompany");
            requestJson.add("Data", httpBodyJson);
            System.out.println("\n" + requestJson);  //for debugging: Print out the complete JSON

            requestProcessingService.handleRequest(CommandParser.JsonToByteBuffer(requestJson), new HttpResponder(exchange));
        }

        private void handleProduct(HttpExchange exchange) {
            if (exchange.getRequestMethod().equals("GET")) {
                //TODO handle GET product
            } else if (exchange.getRequestMethod().equals("POST")) {
                //TODO handle POST product
            }
        }


        private class HttpResponder implements RespondableChannel {
            private final HttpExchange httpExchange;

            private HttpResponder(HttpExchange httpExchange) {
                this.httpExchange = httpExchange;
            }

            @Override
            public void respond(ByteBuffer data) {
                try (OutputStream os = httpExchange.getResponseBody()) {
                    int responseStatusCode = CommandParser.extractStatusFromResponse(data);
                    httpExchange.sendResponseHeaders(responseStatusCode, data.array().length);
                    os.write(data.array());
                } catch (IOException e) {
                    throw new RuntimeException("Failed responding to HTTP client", e);
                }
            }
        }



        //TODO remove this after connecting to DB
        //old implementation before adding generic "/" handling
        /*
        //HttpHandler implementation for "company" resource/URI
        private class HttpHandlerCompany implements HttpHandler {
            @Override
            public void handle(HttpExchange httpExchange) throws IOException {
                System.out.println("inside company response handler");
                String httpMethod = httpExchange.getRequestMethod();

                if (httpMethod.equals("GET")) {
                    handleGetRequest(httpExchange);
                }
                else if (httpMethod.equals("POST")) {
                    handlePostRequest(httpExchange);
                }
                else if (httpMethod.equals("PUT")) {
                    handlePutRequest(httpExchange);
                }
                else if (httpMethod.equals("DELETE")) {
                    handleDeleteRequest(httpExchange);
                }
                else {
                    handleUnsupportedRequest(httpExchange);
                }
            }


            private void handleDeleteRequest(HttpExchange httpExchange) {
                System.out.println("inside DELETE handler");
            }

            private void handlePutRequest(HttpExchange httpExchange) {
                System.out.println("inside PUT handler");
            }

            private void handlePostRequest(HttpExchange httpExchange) {
                System.out.println("inside POST handler");
                //read the request body and parse it as JSON
                JsonObject httpBodyJson;
                try (InputStream inputStream = httpExchange.getRequestBody()) {
                    //parse the input stream to a JSON object using Gson
                    httpBodyJson = JsonParser.parseReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).getAsJsonObject();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read HTTP request body", e);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse JSON from HTTP request body", e);
                }

                //now `requestJson` contains the JSON object from the request body
                System.out.println(httpBodyJson.toString());  //for debugging: Print out the parsed JSON

                //build the request JSON from Key=registerCompany and Data=<body of JSON request>
                JsonObject requestJson = new JsonObject();
                requestJson.addProperty("Key", "registerCompany");
                requestJson.add("Data", httpBodyJson);
                System.out.println("\n" + requestJson);  //for debugging: Print out the complete JSON

                requestProcessingService.handleRequest(CommandParser.JsonToByteBuffer(requestJson), new HttpResponder(httpExchange));
            }


            private void handleGetRequest(HttpExchange httpExchange) {
                System.out.println("inside GET handler");
                String query = httpExchange.getRequestURI().toString().split("\\?")[1].split("=")[1];
                //simulate accessing database inside a specific Command...
                String response = "GET response = " + query;
                ByteBuffer byteBuffer = ByteBuffer.wrap(response.getBytes());
                requestProcessingService.handleRequest(byteBuffer, new HttpResponder(httpExchange));
            }

            private void handleUnsupportedRequest(HttpExchange httpExchange) {
                String response = ("Unsupported method");
                try (OutputStream os = httpExchange.getResponseBody()) {
                    httpExchange.sendResponseHeaders(400, response.length());
                    os.write(response.getBytes());
                } catch (IOException e) {
                    throw new RuntimeException("Failed responding to HTTP client", e);
                }
            }
        }


        //HttpHandler implementation for "companies" resource/URI
        private class HttpHandlerCompanies implements HttpHandler {
            @Override
            public void handle(HttpExchange httpExchange) throws IOException {

            }
        }



        private class HttpResponder implements RespondableChannel {
            private final HttpExchange httpExchange;

            private HttpResponder(HttpExchange httpExchange) {
                this.httpExchange = httpExchange;
            }

            @Override
            public void respond(ByteBuffer data) {
                try (OutputStream os = httpExchange.getResponseBody()) {
                    httpExchange.sendResponseHeaders(200, data.remaining());
                    os.write(data.array());
                } catch (IOException e) {
                    throw new RuntimeException("Failed responding to HTTP client", e);
                }
            }
        }
        */
    }

}
