package code.docflow.yaml.converters;

import code.docflow.collections.Item;
import code.docflow.collections.ItemsIndexedCollection;

import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
@SuppressWarnings({"unchecked"})
public class ItemsMapConverter implements MapConverter {
    @Override
    public Object convert(LinkedHashMap map) {
        final ItemsIndexedCollection<Item> items = new ItemsIndexedCollection<Item>();
        for (Item i : (Collection<Item>) map.values())
            try {
                items.add(i);
            } catch (ItemsIndexedCollection.DuplicatedItemNameWithinGroup duplicatedItemNameWithinGroup) {
                // unexpected
            }
        return items;
    }
}
