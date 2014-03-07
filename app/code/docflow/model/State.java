package code.docflow.model;

import code.docflow.collections.Item;
import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.ItemCompositeKeyHandler;
import code.utils.BitArray;

import java.util.LinkedHashMap;
import java.util.TreeMap;

/**
 * Document.states sequence item.
 */
@WithCompositeKeyHandler(ItemCompositeKeyHandler.class)
public class State extends Item {

    /**
     * Color to be used as item header background.
     */
    public String color;

    /**
     * Transitions by full name!  Full name includes preconditions.
     */
    public LinkedHashMap<String, Transition> transitions;

    /**
     * List of fields groups available for view.
     */
    public LinkedHashMap<String, Item> view; // LinkedHashMap here, to guarantee the uniqueness of the keys

    /**
     * List of fields groups available for update.
     */
    public LinkedHashMap<String, Item> update;

    /**
     * Transition by name.
     */
    @NotYamlField
    public TreeMap<String, Transition> transitionByName = new TreeMap<String, Transition>();

    @NotYamlField
    public DocType document;

    @NotYamlField
    public int globalIndex;

    @NotYamlField
    public int index;

    @NotYamlField
    public BitArray viewMask;

    @NotYamlField
    public BitArray updateMask;

    @NotYamlField
    public BitArray actionsMask;
}
