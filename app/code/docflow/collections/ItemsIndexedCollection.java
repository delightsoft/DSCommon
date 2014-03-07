package code.docflow.collections;

import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.TreeMap;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

// Note: At the moment this mechnism has no any active use in project, but it widely tested during work on Yaml component. So it might be useful sometime in a future.

public class ItemsIndexedCollection<T extends Item> extends Item {

    public static class DuplicatedItemNameWithinGroup extends Exception {
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this; // suppress stack info
        }
    }

    public ItemsIndexedCollection() {
    }

    public ItemsIndexedCollection(String name) {
        super(name);
    }

    public final ArrayList<T> items = new ArrayList<T>();

    public final TreeMap<String, Integer> itemsMap = new TreeMap<String, Integer>();

    public void add(T item) throws DuplicatedItemNameWithinGroup {
        items.add(item);
        if (itemsMap.put(item.name, items.size() - 1) != null)
            throw new DuplicatedItemNameWithinGroup();
    }

    /**
     * Get item by its name.
     * return null, if there is no such item
     */
    public final T get(String itemName) {
        final Integer index = itemsMap.get(itemName);
        return index == null ? null : items.get(index);
    }

    /**
     * Get element by its index.
     */
    public final T get(int index) {
        return items.get(index);
    }

    /**
     * Get item index by its name.
     * return null, if there is no such item
     */
    public final Integer getIndex(String itemName) {
        return itemsMap.get(itemName);
    }

    protected Objects.ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add("size", items.size());
    }
}
