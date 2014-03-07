package code.docflow.model;

import code.docflow.collections.Item;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.RootElementCompositeKeyHandler;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

@WithCompositeKeyHandler(RootElementCompositeKeyHandler.class)
public class RootElement extends Item {
}
