package code.docflow.model;

import code.docflow.collections.Item;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.ItemCompositeKeyHandler;
import com.google.common.base.Objects;

/**
 * Base class for all yaml items.
 */
@WithCompositeKeyHandler(ItemCompositeKeyHandler.class)
public class DocumentFilter extends Item {

    public String where;

    protected Objects.ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add("where", where);
    }
}
