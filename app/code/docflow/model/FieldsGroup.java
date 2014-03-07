package code.docflow.model;

import code.docflow.collections.Item;
import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.TargetField;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.ItemCompositeKeyHandler;
import code.utils.BitArray;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

@WithCompositeKeyHandler(ItemCompositeKeyHandler.class)
public class FieldsGroup extends Item {

    @TargetField
    public Item[] fields;

    @NotYamlField
    public boolean implicit;

    @NotYamlField
    public BitArray mask;
}
