package pluginservice;

import factory.Command;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class DynamicJarLoader {
    private final String interfaceName;
    private final String pluginDirectory;

    public DynamicJarLoader(String pluginDirectory, String interfaceName) {
        this.interfaceName = interfaceName;
        this.pluginDirectory = pluginDirectory;
    }

    public List<Class<?>> loadClassesFromJAR(String pathOfJARFile) throws IOException, ClassNotFoundException {
        List<Class<?>> classList = new ArrayList<>();
        File jarFile = new File(pathOfJARFile);

        //check that the file is actually a .jar file
        if (!jarFile.getName().endsWith(".jar")) {
            throw new RuntimeException("Invalid file");
        }

        String fullPath = pluginDirectory + File.separator + jarFile.getName();
//        System.out.println(fullPath);
        JarFile jar = new JarFile(fullPath);

        URL[] jarURL = {jarFile.toURI().toURL()};
        URLClassLoader classLoader = new URLClassLoader(jarURL);

        Enumeration<JarEntry> jarEntries = jar.entries();
        while (jarEntries.hasMoreElements()) {
            JarEntry jarEntry = jarEntries.nextElement();
            String className = jarEntry.getName();
            if (className.endsWith(".class")) {
                //adds the class to the class list if it class implements the relevant interface
                processClass(className, classLoader, classList);
            }
        }
        return classList;
    }


    private void processClass(String className, URLClassLoader classLoader, List<Class<?>> classList) throws ClassNotFoundException {
        String fullyQualifiedClassName = className.replace('/', '.').substring(0, className.length() - ".class".length());
//                System.out.println(fullyQualifiedClassName);    //TODO delete test prints
        Class<?> clazz = classLoader.loadClass(fullyQualifiedClassName);
//                System.out.println("Current class = " + clazz.getName());
        Class<?>[] currentInterfaces = clazz.getInterfaces();
        for (Class<?> inter : currentInterfaces) {
//                    System.out.println("Interface = " + inter.getName());
            if (inter.getName().equals(interfaceName)) {
//                        System.out.println("Found interface " + inter.getName() + " in class " + clazz.getName());
                classList.add(clazz);
            }
        }
    }


}
