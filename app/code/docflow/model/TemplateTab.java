package code.docflow.model;

import code.docflow.collections.Item;
import code.docflow.yaml.annotations.TargetField;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.TemplateTabCompositeKeyHandler;

import java.util.LinkedHashMap;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

@WithCompositeKeyHandler(TemplateTabCompositeKeyHandler.class)
public class TemplateTab extends Item {
    public String docType;

    public String template;

    @TargetField
    public LinkedHashMap<String, Object> options;
}
