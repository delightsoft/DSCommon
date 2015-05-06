package code.docflow.model;

import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.ItemCompositeKeyHandler;
import code.docflow.utils.BitArray;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class Relation {

    /**
     * Corresponded relation on Document level.
     */
    @NotYamlField
    public DocumentRelation documentRelation;

    /**
     * View rights, added if the relation presents.
     */
    @NotYamlField
    public BitArray viewMask;

    /**
     * Update rights, added if the relation presents.
     */
    @NotYamlField
    public BitArray updateMask;

    /**
     * Actions rights, added if the relation presents.
     */
    @NotYamlField
    public BitArray actionsMask;

    @NotYamlField
    public BitArray retrieveMask;
}
