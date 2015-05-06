package code.docflow.utils;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import java.util.TreeMap;

/**
 * Solves dilemma that enums upper case names do not match their .toString().toUpperCase() values, by indexing
 * upper case version of toStrings.
 */
public class EnumCaseInsensitiveIndex<T extends Enum<T>> {

    TreeMap<String, T> index = new TreeMap<String, T>();

    T defaultValue;

    public EnumCaseInsensitiveIndex(Class<T> enumType) {
        final T[] vals = enumType.getEnumConstants();
        if (vals != null)
            for (int i = 0; i < vals.length; i++) {
                T val = vals[i];
                index.put(val.toString().toUpperCase(), val);
            }

        defaultValue = enumType.getEnumConstants()[0];
    }

    public T get(String value) {
        return index.get(value.toUpperCase());
    }

    public T getDefaultValue() {
        return defaultValue;
    }
}
