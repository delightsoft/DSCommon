package code.docflow.model;

import code.docflow.collections.Item;
import code.docflow.yaml.annotations.NotYamlField;

import java.util.ArrayList;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class Entity extends Item {
    public EntityType type;
    public DocType document;
    public boolean hasTextStorage;

    @NotYamlField
    public Entity parent;

    @NotYamlField
    public Entity outerStructure;

    /**
     * Field within Document that represents repeatable substructure.  In fact this field do not exist in
     * DB.  This information is stored within 'pk' and 'i' field in the structure.
     */
    @NotYamlField
    public Field structureField;

    /**
     * Field within structure, that reference ID of document this entity belongs to.
     */
    @NotYamlField
    public Field fkField;

    @NotYamlField
    public ArrayList<Field> fields = new ArrayList<Field>();

    @NotYamlField
    public String tableName;
}
