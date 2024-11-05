package factory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class Factory<K, T, D> {

    private final Map<K, Function<D, ? extends T>> creationFunctions = new HashMap<>();

    public T create(K key, D data) {
        Function<D, ? extends T> createFunction = creationFunctions.get(key);
        if (createFunction == null) {
            throw new RuntimeException("Create function not found for key: " + key);
        }

        return createFunction.apply(data);
    }

    public void add(K key, Function<D, ? extends T> createFunction) {
        creationFunctions.put(key, createFunction);
    }

}
