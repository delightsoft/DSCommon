package code.docflow.yaml;

import code.controlflow.Result;

import java.util.HashSet;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public interface CompositeKeyHandler<K, V> {
    V parse(String value, HashSet<String> accessedFields, Class collectionType, YamlParser parser, Result result);

    K key(V object);
}
