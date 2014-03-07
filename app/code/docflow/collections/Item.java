package code.docflow.collections;

import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.ItemCompositeKeyHandler;
import com.google.common.base.Objects;

/**
 * Base class for all yaml items.
 */
@WithCompositeKeyHandler(ItemCompositeKeyHandler.class)
public class Item {

    public String name;

    public Item() {
    }

    public Item(String name) {
        this.name = name;
    }

    protected Objects.ToStringHelper toStringHelper() {
        return Objects.toStringHelper(this)
                .add("name", name);
    }

    protected void deepCopy(Item item) {
        item.name = name;
    }

    /**
     * Extend set of fields overriding toStringHelper() method.
     */
    public final String toString() {
        return toStringHelper().toString();
    }
}
