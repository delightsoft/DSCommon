package code.docflow.utils;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.collections.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Group calculator that takes list of indexed items as an argument.
 */
public abstract class ItemsGroupCalculator extends GroupCalculator {

    private final HashMap<String, Integer> indexedItems = new HashMap<String, Integer>();
    private final HashMap<String, BitArray> groupsMasks;
    private final int bitArraySize;

    protected ItemsGroupCalculator(Iterable<Item> items) {
        int ind = 0;
        HashMap<String, ArrayList<Integer>> groups = null;
        for (Item item : items) {
            indexedItems.put(item.name.toUpperCase(), ind);
            if (item._groups != null) {
                if (groups == null)
                    groups = new HashMap<String, ArrayList<Integer>>();
                for (String groupName : item._groups) {
                    ArrayList<Integer> group = groups.get(groupName.toUpperCase());
                    if (group == null)
                        groups.put(groupName.toUpperCase(), group = new ArrayList<Integer>());
                    group.add(ind);
                }
            }
            ind++;
        }
        bitArraySize = ind;
        if (groups == null)
            groupsMasks = null;
        else {
            groupsMasks = new HashMap<String, BitArray>();
            for (Map.Entry<String, ArrayList<Integer>> entry : groups.entrySet()) {
                final BitArray mask = new BitArray(bitArraySize);
                for (Integer p : entry.getValue())
                    mask.set(p, true);
                groupsMasks.put(entry.getKey(), mask);
            }
        }
    }

    @Override
    public BitArray newBitArray() {
        return new BitArray(bitArraySize);
    }

    @Override
    public BitArray getGroup(String name) {
        return groupsMasks == null ? null : groupsMasks.get(name.toUpperCase());
    }

    @Override
    public int getItemIndex(String name) {
        return indexedItems.get(name.toUpperCase());
    }
}
