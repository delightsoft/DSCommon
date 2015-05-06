package code.docflow.compiler;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.collections.Item;
import code.docflow.compiler.enums.BuiltInFields;
import code.docflow.compiler.enums.BuiltInTemplates;
import code.docflow.compiler.enums.BuiltInTypes;
import code.docflow.model.*;
import code.docflow.yaml.YamlMessages;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.utils.BitArray;
import code.docflow.utils.EnumUtil;
import com.google.common.base.Strings;

import java.util.HashSet;
import java.util.LinkedHashMap;

public class Compiler210TemplatesStep1 {

    public static void doJob(DocflowConfig docflowConfig, final Result result) {
        for (DocType docType : docflowConfig.documents.values()) {

            if (docType.udt)
                continue;

            // apply defaults
            if (docType.templates == null)
                docType.templates = new LinkedHashMap<String, Template>();

            Template none = docType.templates.get(BuiltInTemplates.NONE);
            if (none != null) {
                result.addMsg(YamlMessages.error_DocumentTemplateBuiltInTemplateOverride, docType.name, none.name);
            }

            Template create = docType.templates.get(BuiltInTemplates.CREATE);
            if (create != null) {
                result.addMsg(YamlMessages.error_DocumentTemplateBuiltInTemplateOverride, docType.name, create.name);
            }

            if (!docType.report) {
                Template id = docType.templates.get(BuiltInTemplates.ID.getUpperCase());
                if (id != null) {
                    result.addMsg(YamlMessages.error_DocumentTemplateBuiltInTemplateOverride, docType.name, id.name);
                } else {
                    id = new Template();
                    id.name = BuiltInTemplates.ID.toString();
                    id.mode = "L";
                    id.screen = false;
                    docType.templates.put(BuiltInTemplates.ID.getUpperCase(), id);
                }
            }

            if (!docType.report && !docType.simple) {
                Template history = docType.templates.get(BuiltInTemplates.HISTORY.getUpperCase());
                if (history != null) {
                    result.addMsg(YamlMessages.error_DocumentTemplateBuiltInTemplateOverride, docType.name, history.name);
                } else {
                    history = new Template();
                    history.name = BuiltInTemplates.HISTORY.toString();
                    history.mode = "L";
                    history.screen = false;

                    // Process refers fields, so they would be stored as ID only
                    history.fieldsTemplates = new LinkedHashMap<String, TemplateField>();
                    for (Field fld : docType.fields.values()) {
                        if (fld.implicitFieldType != null || fld.derived) // leaving State field in template
                            continue;
                        if (fld.type == BuiltInTypes.REFERS) {
                            TemplateField templateField = new TemplateField();
                            templateField.name = fld.name;
                            templateField.template = BuiltInTemplates.ID.toString();
                            history.fieldsTemplates.put(templateField.name.toUpperCase(), templateField);
                        }
                    }
                    docType.templates.put(BuiltInTemplates.HISTORY.getUpperCase(), history);
                }
            }

            Template list = docType.templates.get(BuiltInTemplates.LIST.getUpperCase());
            if (list == null) {
                list = new Template();
                list.name = BuiltInTemplates.LIST.toString();
                list.accessedFields = new HashSet<String>();
                docType.templates.put(list.name.toUpperCase(), list);
            }
            if (!list.accessedFields.contains("COLUMNS") || list.columns.size() < 1) {
                list.columns = new LinkedHashMap<String, Item>();
                if (list.fields != null)
                    // Rule: If fields is defined, but columns not, when columns are only fields defined by fields
                    list.columns = list.fields;
                else
                    // Rule: By default 'list' template includes all non-implicit fields
                    for (Field field : docType.fields.values())
                        list.columns.put(field.name.toUpperCase(), new Item(field.name));
            }
            if (!list.accessedFields.contains("MODE"))
                list.mode = "";
            if (!list.accessedFields.contains("SCREEN"))
                list.screen = true;

            Template export = docType.templates.get(BuiltInTemplates.EXPORT.getUpperCase());
            if (export == null) {
                export = new Template();
                export.name = BuiltInTemplates.EXPORT.toString();
                export.accessedFields = new HashSet<String>();
                docType.templates.put(export.name.toUpperCase(), export);
            }
            if (!export.accessedFields.contains("COLUMNS") || export.columns.size() < 1) {
                export.columns = new LinkedHashMap<String, Item>();
                if (export.fields != null)
                    // Rule: If fields is defined, but columns not, when columns are only fields defined by fields
                    export.columns = export.fields;
                else
                    // Rule: By default 'export' template includes all non-implicit fields
                    for (Field field : docType.fields.values())
                        export.columns.put(field.name.toUpperCase(), new Item(field.name));
            }
            if (!export.accessedFields.contains("QUERY"))
                export.query = "list";
            if (!export.accessedFields.contains("MODE"))
                export.mode = "";
            if (!export.accessedFields.contains("SCREEN"))
                export.screen = true;

            if (!docType.report) {
                Template dict = docType.templates.get(BuiltInTemplates.DICT.getUpperCase());
                if (dict == null) {
                    dict = new Template();
                    dict.name = BuiltInTemplates.DICT.toString();
                    dict.accessedFields = new HashSet<String>();
                    docType.templates.put(dict.name.toUpperCase(), dict);
                }
                if (!dict.accessedFields.contains("FIELDS")) {
                    dict.fields = new LinkedHashMap<String, Item>();
                    Item idField = new Item();
                    idField.name = "id";
                    dict.fields.put(idField.name.toUpperCase(), idField);
                    Item textField = new Item();
                    textField.name = BuiltInFields.TEXT.toString();
                    dict.fields.put(textField.name.toUpperCase(), textField);
                }
                if (!dict.accessedFields.contains("QUERY"))
                    dict.query = "list";
                if (!dict.accessedFields.contains("MODE"))
                    dict.mode = "L";
                if (!dict.accessedFields.contains("SCREEN"))
                    dict.screen = false;

                Template selector = docType.templates.get(BuiltInTemplates.SELECTOR.getUpperCase());
                if (selector == null) {
                    selector = new Template();
                    selector.name = "selector";
                    selector.accessedFields = new HashSet<String>();
                    docType.templates.put(selector.name.toUpperCase(), selector);
                }
                if (!selector.accessedFields.contains("SCREEN"))
                    selector.screen = false;
            }

            Template form = docType.templates.get(BuiltInTemplates.FORM.getUpperCase());
            if (form == null) {
                form = new Template();
                form.name = BuiltInTemplates.FORM.toString();
                form.accessedFields = new HashSet<String>();
                docType.templates.put(form.name.toUpperCase(), form);
            }
            if (!form.accessedFields.contains("MODE"))
                form.mode = "*";
            if (!form.accessedFields.contains("SCREEN"))
                form.screen = true;

            // TODO: QuickFix: Implmenent couple roules for this built-in template: - alwasy editing ...and something should be else
            Template params = docType.templates.get(BuiltInTemplates.PARAMS.getUpperCase());
            if (params == null) {
                params = new Template();
                params.name = BuiltInTemplates.PARAMS.toString();
                params.accessedFields = new HashSet<String>();
                docType.templates.put(params.name.toUpperCase(), params);
            }

            Template part = docType.templates.get(BuiltInTemplates.BASIC.getUpperCase());
            if (part == null) {
                part = new Template();
                part.name = BuiltInTemplates.BASIC.toString();
                part.derive(form);
                docType.templates.put(BuiltInTemplates.BASIC.getUpperCase(), part);
            } else {
                if (!part.accessedFields.contains("MODE"))
                    part.mode = "*";
                if (!part.accessedFields.contains("SCREEN"))
                    part.screen = true;
            }

            Template formDialog = docType.templates.get(BuiltInTemplates.FORM_DIALOG.getUpperCase());
            if (formDialog == null) {
                formDialog = new Template();
                formDialog.name = BuiltInTemplates.FORM_DIALOG.toString();
                formDialog.derive(form);
                docType.templates.put(BuiltInTemplates.FORM_DIALOG.getUpperCase(), formDialog);
            } else {
                if (!formDialog.accessedFields.contains("MODE"))
                    formDialog.mode = "*";
                if (!formDialog.accessedFields.contains("SCREEN"))
                    formDialog.screen = true;
            }

            if (!docType.report) {
                if (docType.linkedDocument) {
                    Template linkedDocument = docType.templates.get(BuiltInTemplates.LINKED_DOCUMENT.getUpperCase());
                    if (linkedDocument == null) {
                        linkedDocument = new Template();
                        linkedDocument.name = BuiltInTemplates.LINKED_DOCUMENT.toString();;
                        linkedDocument.accessedFields = new HashSet<String>();
                        docType.templates.put(linkedDocument.name.toUpperCase(), linkedDocument);
                    }
                    if (!linkedDocument.accessedFields.contains("MODE"))
                        linkedDocument.mode = "*";
                    if (!linkedDocument.accessedFields.contains("SCREEN"))
                        linkedDocument.screen = true;
                    if (linkedDocument.fieldsTemplates == null)
                        linkedDocument.fieldsTemplates = new LinkedHashMap<String, TemplateField>();
                }
                Template formTitle = docType.templates.get(BuiltInTemplates.FORM_TITLE.getUpperCase());
                if (formTitle == null) {
                    formTitle = new Template();
                    formTitle.name = BuiltInTemplates.FORM_TITLE.toString();;
                    formTitle.screen = false;
                    docType.templates.put(formTitle.name.toUpperCase(), formTitle);
                } else {
                    if (!formTitle.accessedFields.contains("SCREEN"))
                        formTitle.screen = true;
                }
            }

            // process
            for (Template tmpl : docType.templates.values()) {

                tmpl.document = docType;

                // process mode
                tmpl.modeMask = Strings.isNullOrEmpty(tmpl.mode) ?
                        JsonTypeBinder.VIEW_MODE :
                        JsonTypeBinder.processMParam(tmpl.mode);

                // process fields
                BitArray removeMask = null;
                if (tmpl.remove != null) {
                    removeMask = new BitArray(docType.allFields.size());
                    for (Item fld : tmpl.remove.values()) {
                        final Field fieldModel = docType.fieldByFullname.get(fld.name.toUpperCase());
                        if (fieldModel == null) {
                            result.addMsg(YamlMessages.error_DocumentTemplateFieldNotFound, docType.name, tmpl.name, fld.name);
                            continue;
                        }
                        for (int k = fieldModel.index; k < fieldModel.endIndex; k++)
                            removeMask.set(k, true);
                        for (FieldStructure s = fieldModel.structure; s != null; s = s.structure)
                            removeMask.set(s.index, true);
                    }
                }

                if (tmpl.fields == null) {
                    tmpl.fieldsMask = new BitArray(docType.allFields.size());
                    tmpl.fieldsMask.inverse();
                    tmpl.fieldsMask.subtract(docType.implicitFieldsMask);
                    if (tmpl.name.equalsIgnoreCase(BuiltInTemplates.HISTORY.toString())) {
                        // add State to visible fields of History template
                        final Field stateField = docType.fieldByFullname.get(BuiltInFields.STATE.name());
                        if (stateField != null)
                            tmpl.fieldsMask.set(stateField.index, true);
                    }
                } else {
                    tmpl.fieldsMask = new BitArray(docType.allFields.size());
                    for (Item fld : tmpl.fields.values()) {
                        final Field fieldModel = docType.fieldByFullname.get(fld.name.toUpperCase());
                        if (fieldModel == null) {
                            result.addMsg(YamlMessages.error_DocumentTemplateFieldNotFound, docType.name, tmpl.name, fld.name);
                            continue;
                        }
                        // this prevents from adding field's sructures when field later will be removed
                        if (removeMask == null || !removeMask.get(fieldModel.index)) {
                            for (int k = fieldModel.index; k < fieldModel.endIndex; k++)
                                tmpl.fieldsMask.set(k, true);
                            for (FieldStructure s = fieldModel.structure; s != null; s = s.structure)
                                tmpl.fieldsMask.set(s.index, true);
                        }
                    }
                }

                // set implicit fields
                if ((tmpl.modeMask & JsonTypeBinder.GENERATE_LIGHT_JSON) == 0) {
                    final boolean skipSomeFields = (tmpl.modeMask & JsonTypeBinder.GENERATE_FULL_JSON) == 0;
                    for (int i = 0; i < docType.implicitFields.size(); i++) {
                        Field field = docType.implicitFields.get(i);
                        if (skipSomeFields)
                            switch (field.implicitFieldType) {
                                case CREATOR:
                                case CREATED:
                                case MODIFIED:
                                    continue;
                            }
                        tmpl.fieldsMask.set(field.index, true);
                    }
                }
                // remove fields as the last step of mask calculation.  it's crutial
                if (removeMask != null)
                    tmpl.fieldsMask.subtract(removeMask);

                if (!tmpl.document.report &&
                        // templates 'list' and 'dict'
                        (EnumUtil.isEqual(BuiltInTemplates.LIST, tmpl.name) || EnumUtil.isEqual(BuiltInTemplates.DICT, tmpl.name)))
                    tmpl.fieldsMask.set(tmpl.document.fieldByFullname.get(BuiltInFields.TEXT.name()).index, true); // always includes 'text' field
            }
        }
    }
}
