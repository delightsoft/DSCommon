package code.docflow.model;

import code.docflow.collections.Item;
import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.TargetField;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.TemplateTabCompositeKeyHandler;

import java.util.LinkedHashMap;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

@WithCompositeKeyHandler(TemplateTabCompositeKeyHandler.class)
public class TemplateTab extends Item {

    /**
     * Name of the tab.  Rule: For linkedDocument name must match a name of field that refers the linked document.
     */
    // name - derived from Item

    /**
     * Type of document what is shown in the tab.
     */
    public String docType;

    /**
     * Template to be used to show the document (i.e.: part, linkedDocument).
     */
    public String template;

    /**
     * Optionally, limits list of fields to be shown in the tab.  Key purpose to list field for 'part' template, but
     * can be used in other cases when is needed to skip some fields of the document with a tab.
     *
     * Fields is a part of options (see below), but it's controlled by DocflowConfig logic.
     */
    public LinkedHashMap<String, Item> fields;

    /**
     * Options that transperently are transfered in client script.  Example of use: option that tells should client show
     * new linked document as tab.
     */
    public LinkedHashMap<String, Object> options;
}
