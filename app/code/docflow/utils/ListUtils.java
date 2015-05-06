package code.docflow.utils;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

public class ListUtils {
    public static <T> T first(LinkedHashMap<String, T> list) {
        for (T t : list.values())
            return t;
        return null;
    }

    /**
     * Solves problem with hibernate lists. They might contain null elements.
     */
    public static int nonNullElements(List list) {
        int c = 0;
        for (Object o : list) {
            if (o != null)
                c++;
        }
        return c;
    }

    /**
     * Solves problem with hibernate lists. They might contain null elements.
     */
    public static <T> T firstNonNullElement(List<T> list) {
        int c = 0;
        for (Object o : list) {
            if (o != null)
                return (T) o;
        }
        return null;
    }
}
