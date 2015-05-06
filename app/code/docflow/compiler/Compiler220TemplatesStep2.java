package code.docflow.compiler;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.collections.Item;
import code.docflow.compiler.enums.BuiltInTemplates;
import code.docflow.compiler.enums.BuiltInTypes;
import code.docflow.model.*;
import code.docflow.yaml.YamlMessages;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.utils.EnumUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class Compiler220TemplatesStep2 {

    public static void doJob(DocflowConfig docflowConfig, final Result result) {

        TreeMap<String, Template> templateNames = new TreeMap<String, Template>();

        for (DocType docType : docflowConfig.documents.values()) {

            if (docType.udt)
                continue;

            for (Template tmpl : docType.templates.values()) {
                tmpl.templateNameByField = new String[docType.allFields.size()];
                tmpl.templateByField = new Template[docType.allFields.size()];

                String defaultFieldTemplate = tmpl.name;
                boolean defaultRefersToList =
                        EnumUtil.isEqual(BuiltInTemplates.FORM, tmpl.name) ||
                                EnumUtil.isEqual(BuiltInTemplates.LINKED_DOCUMENT, tmpl.name) ||
                                EnumUtil.isEqual(BuiltInTemplates.CREATE, tmpl.name) ||
                                EnumUtil.isEqual(BuiltInTemplates.SELECTOR, tmpl.name);

                if (tmpl.fieldsTemplates != null)
                    for (TemplateField fieldTemplate : tmpl.fieldsTemplates.values()) {
                        final String fldName = fieldTemplate.name.toUpperCase();
                        if (fldName.equalsIgnoreCase(DocflowConfig.DEFAULT_FIELDS_TEMPLATE)) {
                            defaultFieldTemplate = fldName;
                            continue;
                        }
                        final Field fieldModel = docType.fieldByFullname.get(fldName);
                        if (fieldModel == null) {
                            result.addMsg(YamlMessages.error_DocumentTemplateFieldNotFound, docType.name, tmpl.name, fieldTemplate.name);
                            continue;
                        }
                        if (EnumUtil.isEqual(BuiltInTemplates.NONE, fieldTemplate.template))
                            tmpl.fieldsMask.set(fieldModel.index, false);
                        else {
                            tmpl.templateNameByField[fieldModel.index] = fieldTemplate.template;
                            if (fieldModel.type == BuiltInTypes.REFERS) {
                                final FieldReference refFld = (FieldReference) fieldModel;
                                final DocType refDoc = docflowConfig.documents.get(refFld.refDocument.toUpperCase());
                                final Template template = refDoc.templates.get(fieldTemplate.template.toUpperCase());
                                if (template == null) {
                                    result.addMsg(YamlMessages.error_DocumentTemplateNotDefinedTemplate, docType.name,
                                            tmpl.name, fieldModel.name, refDoc.name, fieldTemplate.template);
                                    continue;
                                }
                            }
                            if (fieldModel.type == BuiltInTypes.POLYMORPHIC_REFERS) {
                                final FieldPolymorphicReference refFld = (FieldPolymorphicReference) fieldModel;
                                if (refFld.refDocuments != null)
                                    for (int i = 0; i < refFld.refDocuments.length; i++) {
                                        final DocType refDoc = docflowConfig.documents.get(refFld.refDocuments[i].toUpperCase());
                                        final Template template = refDoc.templates.get(fieldTemplate.template.toUpperCase());
                                        if (template == null) {
                                            result.addMsg(YamlMessages.error_DocumentTemplateNotDefinedTemplate, docType.name,
                                                    tmpl.name, fieldModel.name, refDoc.name, fieldTemplate.template);
                                            continue;
                                        }
                                    }
                                else
                                    for (DocType refDoc : docflowConfig.documents.values()) {
                                        final Template template = refDoc.templates.get(fieldTemplate.template.toUpperCase());
                                        if (template == null) {
                                            result.addMsg(YamlMessages.error_DocumentTemplateNotDefinedTemplate, docType.name,
                                                    tmpl.name, fieldModel.name, refDoc.name, fieldTemplate.template);
                                            continue;
                                        }
                                    }
                            }
                        }
                    }

                for (int i = 0; i < tmpl.templateNameByField.length; i++)
                    if (tmpl.templateNameByField[i] == null) {
                        Field field = docType.allFields.get(i);
                        if (field.type == BuiltInTypes.REFERS && field.implicitFieldType == null) { // reason: implicit FK fields defined as FieldSimple
                            DocType doc = docflowConfig.documents.get(((FieldReference) field).refDocument.toUpperCase());
                            if (doc.dictionary) {
                                tmpl.templateNameByField[i] = BuiltInTemplates.DICT.toString();
                                continue;
                            } else if (defaultRefersToList) {
                                tmpl.templateNameByField[i] = doc.linkedDocument ? BuiltInTemplates.LINKED_DOCUMENT.toString() : BuiltInTemplates.LIST.toString();
                                continue;
                            }
                        }
                        tmpl.templateNameByField[i] = defaultFieldTemplate;
                    }

                if (tmpl.tabs != null) {

                    for (TemplateTab tab : tmpl.tabs.values()) {
                        // Check that document and template mentioned in tab declaration are exist
                        final DocType document = docflowConfig.documents.get(tab.docType.toUpperCase());
                        if (document == null) {
                            result.addMsg(YamlMessages.error_DocumentTemplateTabUnknownDocumentType, docType.name, tmpl.name, tab.name, tab.docType);
                            continue;
                        }
                        final Template template = (document.templates != null) ? document.templates.get(tab.template.toUpperCase()) : null;
                        if (template == null) {
                            result.addMsg(YamlMessages.error_DocumentTemplateTabTemplateNotFoundInDocumentType, docType.name, tmpl.name, tab.name, tab.docType, tab.template);
                            continue;
                        }

                        // Rule: Hide field, if it's name equal to tab name
                        Field field = docType.fields.get(tab.name.toUpperCase());
                        if (field != null)
                            tmpl.templateNameByField[field.index] = BuiltInTemplates.LINKED_DOCUMENT.toString();

                        // Rule: options.fields should contain fields available in the document
                        // Rule: options.fields should contain unique fields
                        // Rule: fields not mentioned in options.fields remain in the main tab
                        if (tab.fields != null)
                            if (document != docType)
                                // Rule: fields can only be specified for a tab with the same docType as yaml file root.
                                result.addMsg(YamlMessages.error_DocumentTemplateTabFieldsMayOnlyBeSpecifiedForRootDocumentType, docType.name, tmpl.name, tab.name);
                            else {
                                for (Map.Entry<String, Item> itemEntry : tab.fields.entrySet()) {
                                    Item item = itemEntry.getValue();
                                    if (docType.fields.get(itemEntry.getKey()) == null) {
                                        result.addMsg(YamlMessages.error_DocumentTemplateTabFieldsContainsMissingField, docType.name, tmpl.name, tab.name, item.name);
                                        continue;
                                    }
                                    if (tmpl.mainTabFields == null)
                                        tmpl.mainTabFields = new LinkedHashMap<String, Field>(docType.fields); // LinkedHashMap cause sequence is valuable
                                    if (tmpl.mainTabFields.remove(itemEntry.getKey()) == null) {
                                        result.addMsg(YamlMessages.error_DocumentTemplateTabFieldAlreadySpecifiedInAnotherTab, docType.name, tmpl.name, tab.name, item.name);
                                        continue;
                                    }
                                }
                                // Rule: Notify when main-tab remained empty
                                if (tmpl.mainTabFields != null && tmpl.mainTabFields.size() == 0)
                                    result.addMsg(YamlMessages.error_DocumentTemplateAllFieldsSplittedBetweenTabs, docType.name, tmpl.name);
                                // Rule: Fields in sub-tab should remain in the same sequence as they defined in the document (document / fields)
                                LinkedHashMap<String, Item> originalSequence = tab.fields;
                                tab.fields = new LinkedHashMap<String, Item>(); // new sequence
                                for (String upperDocField : docType.fields.keySet()) {
                                    Item fld = originalSequence.get(upperDocField);
                                    if (fld != null)
                                        tab.fields.put(upperDocField, fld);
                                }
                            }
                    }
                }

                if (tmpl.columns != null)
                    for (Item column : tmpl.columns.values()) {
                        if (!column.name.startsWith("_")) {
                            final Field fieldModel = docType.fieldByFullname.get(column.name.toUpperCase());
                            if (fieldModel == null) {
                                result.addMsg(YamlMessages.error_DocumentTemplateColumnNoSuchField, docType.name, tmpl.name, column.name);
                                continue;
                            }

                            for (int k = fieldModel.index; k < fieldModel.endIndex; k++)
                                tmpl.fieldsMask.set(k, true);

                            for (FieldStructure s = fieldModel.structure; s != null; s = s.structure)
                                tmpl.fieldsMask.set(s.index, true);

                        } else if (!column.name.equals(DocflowConfig.FIELD_SELF)) {
                            result.addMsg(YamlMessages.error_DocumentTemplateColumnHasUnexpectedName, docType.name, tmpl.name, column.name);
                            continue;
                        }
                    }

                for (int i = 0; i < tmpl.templateNameByField.length; i++) {
                    final String templateName = tmpl.templateNameByField[i];
                    if (templateName.equalsIgnoreCase(tmpl.name))
                        tmpl.templateByField[i] = tmpl;
                    else {
                        Template template = templateNames.get(templateName.toUpperCase()); // it's just flightweight pattern
                        if (template == null) {
                            template = new JsonTypeBinder.TemplateName(templateName);
                            templateNames.put(template.name.toUpperCase(), template);
                        }
                        tmpl.templateByField[i] = template;
                    }
                }
            }
        }
    }
}
