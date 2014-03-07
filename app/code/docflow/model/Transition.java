package code.docflow.model;

import code.docflow.collections.Item;
import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.TransitionCompositeKeyHandler;

import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
@WithCompositeKeyHandler(TransitionCompositeKeyHandler.class)
public class Transition extends Item {
    public String[] preconditions;
    public String endState;

    @NotYamlField
    public String keyInNormalCase;

    @NotYamlField
    public ArrayList<Transition> conditionalTransitions;

    @NotYamlField
    public Method preconditionEvaluator;

    @NotYamlField
    public Action actionModel;

    @NotYamlField
    public State endStateModel;
}
