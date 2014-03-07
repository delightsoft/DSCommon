package code.docflow.model;

import code.docflow.collections.Item;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.TemplateFieldCompositeKeyHandler;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

@WithCompositeKeyHandler(TemplateFieldCompositeKeyHandler.class)
public class TemplateField extends Item {
    public String template;
}
