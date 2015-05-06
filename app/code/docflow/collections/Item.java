package code.docflow.collections;

import code.docflow.yaml.annotations.FlagName;
import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.ItemCompositeKeyHandler;
import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.Set;

/**
 * Base class for all yaml items.
 */
@WithCompositeKeyHandler(ItemCompositeKeyHandler.class)
public class Item {

    public String name;

    @FlagName("final")
    public boolean _final;

    @FlagName("top")
    public boolean _top;

    @NotYamlField
    public Set<String> _groups;

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
