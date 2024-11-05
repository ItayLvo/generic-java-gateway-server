package pluginservice;

import java.io.IOException;
import java.nio.file.*;
import java.util.function.Consumer;

public class DirMonitor implements Runnable {
    private final String pathOfDirToMonitor;
    private final Consumer<Path> actionOnJarDetected;

    public DirMonitor(String pathOfDirToMonitor, Consumer<Path> actionOnJarDetected) {
        if (pathOfDirToMonitor == null || pathOfDirToMonitor.isEmpty()) {
            throw new IllegalArgumentException("Invalid path to directory");
        }
        if (actionOnJarDetected == null) {
            throw new IllegalArgumentException("Invalid callback action function on jar detected");
        }

        this.pathOfDirToMonitor = pathOfDirToMonitor;
        this.actionOnJarDetected = actionOnJarDetected;
    }


    @Override
    public void run() {
        monitorDirectory();
    }


    public void monitorDirectory() {
        WatchService watchService;
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initiate Watch Service", e);
        }

        Path pathToMonitor = Paths.get(this.pathOfDirToMonitor);

        try {
            pathToMonitor.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);
            //we don't handle JARs being deleted (so no ENTRY_DELETE)
        } catch (IOException e) {
            throw new RuntimeException("Failed to register new Watch Service events", e);
        }

        WatchKey key;
        try {
            while ((key = watchService.take()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    try {
                        actionOnJarDetected.accept(pathToMonitor.resolve((Path) event.context()));
                    } catch (Throwable e) {
                        //ignore exception to keep DirMonitor thread alive
                        e.printStackTrace();
                    }
                }
                key.reset();

            }
        } catch (InterruptedException e) {
            //ignore exception to keep DirMonitor thread alive
            e.printStackTrace();
        }

        try {
            watchService.close();
        } catch (IOException e) {
            throw new RuntimeException("Error closing the WatcherService", e);
        }

    }
}
