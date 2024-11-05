# Gateway Server Project

## Overview

A generic, modular gateway server capable of **handling various communication protocols (HTTP, TCP, UDP), processing requests concurrently, and communicating with a Database**. It is designed with reusability and extensibility in mind, allowing components to be integrated into other projects, including a **plug-and-play** module that allows **extending functionallity without down-time** necessary.

This project is still a work in progress and is intended for showcasing different technologies, concepts and tools I've learned. It demonstrates modularity, concurrency, and extensibility in server design - and also highlights design patterns and custom implementations.

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
  - [Connection Service](#connection-service)
  - [Request Processing Service (RPS)](#request-processing-service-rps)
  - [Thread Pool and Blocking Priority Queue](#thread-pool-and-blocking-priority-queue)
  - [Plugin Service](#plugin-service)
- [Technologies Used](#technologies-used)
- [Usage](#usage)
  - [Dynamic Plugin Loading](#dynamic-plugin-loading)
  - [Sending Requests](#sending-requests)
- [Future Work](#future-work)




## Features

- **Concurrency Management, Custom Thread Pool**: Implements a custom complex thread pool and a blocking priority queue for efficient and concurrent request handling.
- **Plug-and-Play Service**: Supports dynamic loading of plugins without server downtime using **Publisher-Subscriber** implementation.
- **Protocol Support and Asynchronous Processing**: Handles TCP, UDP, and HTTP communications using Java NIO and `HttpServer` packages.
- **Modular Design**: Separates concerns into independent modules (Connection Service and RPS) for better maintainability and reusability.
- **Generic Parsing and Command Execution**: Uses interfaces for parsers and commands to allow flexibility and extensibility.
- **Database Interaction**: Connects to a MySQL database using Connector/J, with data exchanged in JSON format.

## Architecture

### Connection Service

**Function**: Manages incoming client connections over TCP, UDP, and HTTP protocols.

**Implementation**: Uses Java NIO and `HttpServer` for non-blocking, asynchronous communication.

**Process**:

- Listens for incoming connections using a `Selector` for multiplexing.
- Accepts TCP connections and registers them for reading.
- Receives UDP packets and processes them.
- Handles HTTP requests using `HttpServer`.

**Code Example**:

```java
public class ConnectionService {
    private final RequestProcessingService requestProcessingService;
    private final Selector selector;

    public ConnectionService(RequestProcessingService requestProcessingService) throws IOException {
        this.requestProcessingService = requestProcessingService;
        this.selector = Selector.open();
    }

    public void start() {
        // Start listener thread
        // Initialize HTTP service
    }

    public void addTCPConnection(String hostname, int port) throws IOException {
        ServerSocketChannel tcpServerSocketChannel = ServerSocketChannel.open();
        tcpServerSocketChannel.configureBlocking(false);
        tcpServerSocketChannel.bind(new InetSocketAddress(hostname, port));
        tcpServerSocketChannel.register(selector, SelectionKey.OP_ACCEPT, new TCPConnector());
    }

    // Similar method for UDP connection
}
```

### Request Processing Service (RPS)

**Function**: Processes incoming requests and executes corresponding commands.

**Implementation**: Utilizes a `Parser` interface to interpret requests and a `Command Factory` to generate command objects.

**Process**:

- Parses the request type and data using a `Parser`.
- Creates and executes command objects via a `Factory`.
- Interacts with the MySQL database via Connector/J.
- Formats responses in JSON and sends them back to the client.

**Code Example**:

```java
public class RequestProcessingService {
    private final Parser parser;
    private final Factory<String, Command, JsonObject> commandFactory = new Factory<>();
    private ThreadPool threadPool;

    public RequestProcessingService(Parser parser) {
        this.parser = parser;
    }

    public void start() {
        threadPool = new ThreadPool();
        initializeFactory();
        PluginService pluginService = new PluginService("plugins", Command.class.getName());
        pluginService.start();
    }

    public void handleRequest(ByteBuffer request, RespondableChannel respondableChannel) {
        Runnable requestRunnable = () -> {
            Map.Entry<String, JsonObject> keyDataPair = parseRequest(request);
            Command command = createCommand(keyDataPair.getKey(), keyDataPair.getValue());
            command.execute(respondableChannel);
        };
        threadPool.submit(requestRunnable);
    }

    // Additional methods...
}
```

### Thread Pool and Blocking Priority Queue

**Function**: Manages concurrency by handling multiple tasks simultaneously.

**Implementation**: Custom-built thread pool based on a blocking priority queue (`WaitablePQueue`).

**Process**:

- Tasks are wrapped and submitted to the thread pool.
- The thread pool manages worker threads that execute tasks based on priority.
- Supports dynamic adjustment of thread numbers, pausing, and resuming.

**Code Snippet**:

```java
public class ThreadPool implements Executor {
    private final WaitablePQueue<Task<?>> taskQueue = new WaitablePQueue<>();
    private final AtomicInteger currentNumberOfThreads;

    public ThreadPool(int nThreads) {
        currentNumberOfThreads = new AtomicInteger(0);
        for (int i = 0; i < nThreads; ++i) {
            Worker worker = new Worker();
            worker.start();
        }
    }

    @Override
    public void execute(Runnable runnable) {
        submit(runnable);
    }

    public Future<?> submit(Runnable command) {
        Callable<Object> callableWrapper = Executors.callable(command);
        return submit(callableWrapper, Priority.MEDIUM);
    }

    // Additional methods...
}
```

### Plugin Service

**Function**: Allows dynamic loading of plugins (JAR files) to extend server functionality without downtime.

**Implementation**:

- Monitors a specified directory for new JAR files using `DirMonitor`.
- Loads classes from detected JAR files using `DynamicJarLoader`.
- Adds new command implementations to the `Factory`.

**Process**:

- Upon detecting a new JAR, the service loads relevant classes that implement the `Command` interface.
- Adds constructors of new commands to the factory for future requests.

**Code Example**:

```java
private class PluginService {
    private final String pluginDirectory;
    private final DynamicJarLoader dynamicJarLoader;

    public PluginService(String pluginDirectory, String interfaceName) {
        this.pluginDirectory = pluginDirectory;
        dynamicJarLoader = new DynamicJarLoader(pluginDirectory, interfaceName);
    }

    public void start() {
        loadInitialJARsFromDir();
        DirMonitor dirMonitor = new DirMonitor(pluginDirectory, this::handleJARDetected);
        Thread dirMonitorThread = new Thread(dirMonitor);
        dirMonitorThread.setDaemon(true);
        dirMonitorThread.start();
    }

    // Additional methods...
}
```

## Technologies Used

- **Java SE 8**: Core programming language.
- **Java NIO**: For asynchronous, non-blocking I/O operations.
- **Java `HttpServer`**: For handling HTTP requests.
- **MySQL Connector/J**: For database connectivity.
- **Gson**: For JSON parsing and serialization.
- **Custom Thread Pool and Blocking Priority Queue**: For managing concurrency.
- **Dynamic Class Loading**: For the plug-and-play service using `URLClassLoader`.



## Usage

### Dynamic Plugin Loading

- **Directory Monitoring**: The server monitors a specified directory (`/plugins`) for new JAR files.
- **Adding Plugins**:

  - Place your plugin JAR files into the `/plugins` directory.
  - Ensure your plugin classes implement the `Command` interface.

- **Automatic Loading**: The server automatically detects and loads new plugins without requiring a restart.

### Sending Requests

- **Supported Protocols**: TCP, UDP, and HTTP.
- **Request Format**:

  - **JSON**: Requests should be in JSON format with a "Key" and "Data" field.
  - **Example**:

    ```json
    {
      "Key": "registerCompany",
      "Data": {
        "companyName": "ExampleCorp",
        "address": "1234 Main St"
      }
    }
    ```

- **Using TCP/UDP**:

  - Connect to the server's TCP or UDP port.
  - Send the JSON request as a byte stream.

- **Using HTTP**:

  - **POST** requests can be sent to endpoints like `http://localhost:8080/company`.
  - The request body should contain the JSON data.
  - **Example using `curl`**:

    ```bash
    curl -X POST -H "Content-Type: application/json" -d '{
      "companyName": "ExampleCorp",
      "address": "1234 Main St"
    }' http://localhost:8080/company
    ```

## Future Work

- **Improved Documentation**: Add detailed code comments and user documentation.
- **Performance Optimization**: Fine-tune the thread pool and resource management.
- **Security**: Integrate HTTPS, validate and encrypt client data (required all around).




---

*Note: This project is a work in progress and is intended for educational purposes. The current implementation includes TODOs, tests, and comments aimed at future development.*
