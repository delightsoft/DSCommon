package code.docflow.model;

import code.docflow.collections.Item;
import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.ItemCompositeKeyHandler;
import code.docflow.utils.BitArray;

import java.util.LinkedHashMap;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
@WithCompositeKeyHandler(ItemCompositeKeyHandler.class)
public class RoleDocument extends Item {

    public LinkedHashMap<String, RoleRight> view;

    public LinkedHashMap<String, RoleRight> update;

    public LinkedHashMap<String, RoleRight> actions;

    @NotYamlField
    public Role role;

    @NotYamlField
    public DocType document;

    /**
     * View rights for given role and document.
     */
    @NotYamlField
    public BitArray viewMask;

    /**
     * Update rights for given role and document.
     */
    @NotYamlField
    public BitArray updateMask;

    /**
     * Action rights for given role and document.
     */
    @NotYamlField
    public BitArray actionsMask;

    @NotYamlField
    public BitArray retrieveMask;

    /**
     * View rights for given role and document.
     */
    @NotYamlField
    public BitArray fullViewMask;

    /**
     * Update rights for given role and document.
     */
    @NotYamlField
    public BitArray fullUpdateMask;

    /**
     * Action rights for given role and document.
     */
    @NotYamlField
    public BitArray fullActionsMask;

    /**
     * Relations mentioned in given role.
     */
    @NotYamlField
    public Relation[] relations;
}
