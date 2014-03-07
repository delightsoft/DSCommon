package code.docflow.model;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.collections.Item;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.ItemCompositeKeyHandler;

@WithCompositeKeyHandler(ItemCompositeKeyHandler.class)
public class FieldEnumItem extends Item {

    public String color;

}
