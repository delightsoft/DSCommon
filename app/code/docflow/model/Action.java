package code.docflow.model;

import code.docflow.DocflowConfig;
import code.docflow.collections.Item;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.ActionCompositeKeyHandler;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashMap;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
@WithCompositeKeyHandler(ActionCompositeKeyHandler.class)
public class Action extends Item {

    /**
     * True, means that action behaves as a service, not related to specific document.
     */
    public boolean service;

    /**
     * True, action is available for user on screen, so it comes in $a property of an updateable document.
     */
    public boolean display;

    /**
     * True, action allowed to update document prior to action.
     */
    public boolean update;

    /**
     * Question user should asked on UI prior action.
     */
    public String confirm;

    /**
     * True, if action can only be used from server code.
     */
    public boolean internal;

    /**
     * Script what will be used within angular template of this Action button.
     */
    public String script;

    /**
     * Angular ng-if condition that will be applied to the action button.
     */
    public String ngif;

    /**
     * Angular ng-disabled condition that will be applied to the action button.
     */
    public String ngdisabled;

    /**
     * Parameters of action.
     */
    public LinkedHashMap<String, Field> params;

    /**
     * True, if action should shown out of edit form.  Such actions suppose to start new work.
     */
    public boolean outOfForm;

    /**
     * True, if it's exceptional action.  Such actions remain in Other Actions list at the screen.
     */
    public boolean other;

    /**
     * Map of field names accessed while yaml loading.
     */
    @NotYamlField
    public HashSet<String> accessedFields;

    @NotYamlField
    public CrudActions implicitAction;

    @NotYamlField
    public DocType document;

    @NotYamlField
    public int index;

    @NotYamlField
    public Method actionMethod;

    @NotYamlField
    public String paramsClassName;

    @NotYamlField
    public Class paramsClass;

    public String getFullParamsClassName() {
        return document.getClassName() + "$" + paramsClassName;
    }
}
