package code.docflow.model;

import code.docflow.collections.Item;
import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.TemplateCompositeKeyHandler;
import code.docflow.utils.BitArray;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

@WithCompositeKeyHandler(TemplateCompositeKeyHandler.class)
public class Template extends Item {

    public String mode;

    public LinkedHashMap<String, Item> fields;

    public LinkedHashMap<String, Item> remove;

    public LinkedHashMap<String, TemplateField> fieldsTemplates;

    public LinkedHashMap<String, TemplateTab> tabs;

    public LinkedHashMap<String, Item> columns;

    public LinkedHashMap<Integer, HtmlElement> html;

    /**
     * If not null, uses named query in /api/list request.
     */
    public String query;

    /**
     * True, if template appears in UI as first class screen (not as subtab or something like that).
     */
    public boolean screen = true;

    public void derive(Template parent) {
        if (accessedFields == null)
            accessedFields = new HashSet<String>();
        if (!accessedFields.contains("MODE"))
            mode = parent.mode;
        if (!accessedFields.contains("FIELDS"))
            fields = parent.fields;
        if (!accessedFields.contains("REMOVE"))
            remove = parent.remove;
        if (!accessedFields.contains("FIELDSTEMPLATES"))
            fieldsTemplates = parent.fieldsTemplates;
        if (!accessedFields.contains("TABS"))
            tabs = parent.tabs;
        if (!accessedFields.contains("COLUMNS"))
            columns = parent.columns;
        if (!accessedFields.contains("QUERY"))
            query = parent.query;
        if (!accessedFields.contains("SCREEN"))
            screen = parent.screen;
    }

    /**
     * Map of field names accessed while yaml loading.
     */
    @NotYamlField
    public HashSet<String> accessedFields;

    @NotYamlField
    public DocType document;

    /**
     * Compiled value of 'mode' prop.
     */
    @NotYamlField
    public int modeMask;

    /**
     * Compiled value of 'fields' prop.
     */
    @NotYamlField
    public BitArray fieldsMask;

    /**
     * Compiled value of 'fieldsTemplates' prop.
     */
    @NotYamlField
    public String[] templateNameByField;

    @NotYamlField
    public Template[] templateByField;

    @NotYamlField
    public HashMap<String, Field> mainTabFields;
}
