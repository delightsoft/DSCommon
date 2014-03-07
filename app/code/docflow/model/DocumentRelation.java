package code.docflow.model;

import code.docflow.collections.Item;
import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.ItemCompositeKeyHandler;

import java.lang.reflect.Method;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */


/**
 * Document level relation info.  Aggregates relations from different roles.
 */
@WithCompositeKeyHandler(ItemCompositeKeyHandler.class)
public class DocumentRelation extends Item {

    /**
     * Index within Document.relations.
     */
    @NotYamlField
    public int index;

    /**
     * Interface to java code that evaluates this relation.
     */
    @NotYamlField
    public Method evaluator;
}
