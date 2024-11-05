package gatewayserver;

import connectionservice.RespondableChannel;
import factory.*;
import pluginservice.DirMonitor;
import pluginservice.DynamicJarLoader;
import threadpool.ThreadPool;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
        //create and run a Plugin Service to monitor and load JARs from this specific directory
        PluginService pluginService = new PluginService("/home/itay/git/Java/GatewayServer/plugins", Command.class.getName());
        pluginService.start();
    }

    public void stop() {
        threadPool.shutdown();
        try {
            threadPool.awaitTermination();
        } catch (InterruptedException e) {
            throw new RuntimeException("Error shutting down the Thread Pool", e);
        }
    }

    public Map.Entry<String, JsonObject> parseRequest(ByteBuffer request) {
        return parser.parse(request);
    }

    public Command createCommand(String key, JsonObject data) {
        return commandFactory.create(key, data);
    }


    //private method to set up all pre-built factory creation methods
    private void initializeFactory() {
        //TODO this is an example pre-made recipe
        addRecipeToCommandFactory("registerCompany", RegisterCompanyCommand::new);
    }

    private void addRecipeToCommandFactory(String key, Function<JsonObject, Command> newCommandConstructor) {
        System.out.println("***adding to factory: " + key); //TODO remove test prints
        System.out.println("***adding to factory: " + newCommandConstructor);
        commandFactory.add(key, newCommandConstructor);
    }


    public void handleRequest(ByteBuffer request, RespondableChannel respondableChannel) {
        //create a Runnable that parses the request, creates a command using the Command Factory, and then executes it.
        //then submit that Runnable to the thread pool
        Runnable requestRunnable = new Runnable() {
            @Override
            public void run() {
                Map.Entry<String, JsonObject> keyDataPair = parseRequest(request);
                System.out.println("command type = " + keyDataPair.getKey() + " , data = " + keyDataPair.getValue().toString());
                Command command = createCommand(keyDataPair.getKey(), keyDataPair.getValue());
                command.execute(respondableChannel);
            }
        };
        threadPool.submit(requestRunnable);
    }


    private class PluginService {
        private final String pluginDirectory;
        private final DynamicJarLoader dynamicJarLoader;

        public PluginService(String pluginDirectory, String interfaceName) {
            this.pluginDirectory = pluginDirectory;
            dynamicJarLoader = new DynamicJarLoader(pluginDirectory, interfaceName);
        }

        public void start() {
            loadInitialJARsFromDir();

            //create and run the directory watcher thread
            DirMonitor dirMonitor = new DirMonitor(pluginDirectory, this::handleJARDetected);
            Thread dirMonitorThread = new Thread(dirMonitor);
            dirMonitorThread.setDaemon(true);
            dirMonitorThread.start();
        }


        private void handleJARDetected(Path pathToJAR) {
            if (!pathToJAR.toString().endsWith(".jar")) {
                throw new RuntimeException("Invalid file");
            }

            String fullPath = pluginDirectory + "/" + pathToJAR.toString();
            List<Class<?>> newCommandsList = loadClassesFromJAR(fullPath);
            
            for (Class<?> newCommandClass : newCommandsList) {
                //extract the constructor from the new Command class
                Constructor<?> newCommandConstructor = getCommandConstructor(newCommandClass);

                //create a Function<JsonObject, Command> from the constructor
                Function<JsonObject, Command> constructorFunction = (dataArgument) -> {
                    try {
                        //instantiate the command using the constructor
                        return (Command)newCommandConstructor.newInstance(dataArgument);
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                        throw new RuntimeException("Failed to create command instance", e);
                    }
                };

                //add the new Command recipe (constructor) to the factory
                String newCommandName = newCommandClass.getSimpleName();
                RequestProcessingService.this.addRecipeToCommandFactory(newCommandName, constructorFunction);
            }
        }


        private List<Class<?>> loadClassesFromJAR(String fullPath) {
            List<Class<?>> newCommandsList;
            //load relevant classes from the JAR into a list
            try {
                newCommandsList = dynamicJarLoader.loadClassesFromJAR(fullPath);
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException("Couldn't load classes from JAR file", e);
            }
            return newCommandsList;
        }


        private Constructor<?> getCommandConstructor(Class<?> newCommand) {
            Constructor<?> commandConstructor;
            try {
                commandConstructor = newCommand.getConstructor(JsonObject.class);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Couldn't get a constructor which accepts JsonObject as an argument", e);
            }
            return commandConstructor;
        }


        private void loadInitialJARsFromDir() {
            File JarDirectory = new File(pluginDirectory);
            if (!JarDirectory.exists() || !JarDirectory.isDirectory()) {
                throw new RuntimeException("JAR plugin directory does not exist");
            }

            for (File file : JarDirectory.listFiles()) {
                if (file.getName().endsWith(".jar")) {
                    handleJARDetected(file.toPath());
                }
            }
        }


    }   //end of PluginService inner class

}
