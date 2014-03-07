package code.docflow.model;

import code.docflow.collections.Item;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.ItemCompositeKeyHandler;
import com.google.common.base.Objects;

/**
 * Base class for all yaml items.
 */
@WithCompositeKeyHandler(ItemCompositeKeyHandler.class)
public class DocumentSortOrder extends Item {

    public String sortOrder;

    protected Objects.ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add("sortOrder", sortOrder);
    }
}
