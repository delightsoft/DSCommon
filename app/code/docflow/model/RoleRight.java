package code.docflow.model;

import code.docflow.collections.Item;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.RoleRightCompositeKeyHandler;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
@WithCompositeKeyHandler(RoleRightCompositeKeyHandler.class)
public class RoleRight extends Item {
    public String[] relations;
}
