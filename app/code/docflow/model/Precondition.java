package code.docflow.model;

import code.docflow.DocflowConfig;
import code.docflow.collections.Item;
import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.ItemCompositeKeyHandler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

public class Precondition {

    public String name;

    public ArrayList<Transition> transitions = new ArrayList<Transition>();
}
