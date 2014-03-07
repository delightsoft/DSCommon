package code.docflow;

// TODO: Add in some way version of docflow model.  Will help to determine that client is uses old one
// TODO: Rule: new state must not give access to calculated fields
// TODO: DocflowConfig should require model regeneration if require values of columns do not match to Document

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

import code.controlflow.Result;
import code.docflow.collections.Item;
import code.docflow.model.*;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;
import code.docflow.yaml.builders.DocumentBuilder;
import code.docflow.yaml.builders.ItemBuilder;
import code.docflow.yaml.compositeKeyHandlers.RootElementCompositeKeyHandler;
import code.jsonBinding.JsonTypeBinder;
import code.models.Document;
import code.utils.BitArray;
import code.utils.EnumUtil;
import code.utils.FileUtil;
import code.utils.NamesUtil;
import com.google.common.base.Strings;
import controllers.DocflowGenerator;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.scanner.ScannerException;
import play.Logger;
import play.Play;
import play.exceptions.JavaExecutionException;
import play.exceptions.UnexpectedException;
import play.mvc.Http;
import play.vfs.VirtualFile;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkState;

/**
 * Singletone root for Documents Flow & Rights Management mechanism.
 */
public class DocflowConfig {

    public static final String GENERATED_JAVA_FILE_FINGERPRINT = "// Generate by DocflowGenerator";

    public static final String PATH_DOCFLOW = "docflow";
    public static final String PATH_MODELS = "models";

    public static final String PATH_DOCUMENTS = "docflow/documents";
    public static final String PATH_ROLES = "docflow/roles";
    public static final String PATH_FIELD_TYPES = "docflow/fieldTypes.yaml";

    public static final String MESSAGES_FILE_SUFFIX = ".messages.yaml";

    /**
     * Special case field.  Servs to say that in this table column should be show object itself.
     */
    public static final String FIELD_SELF = "_self";
    public static final String DEFAULT_FIELDS_TEMPLATE = "_default";
    public static final String UDT_DOCUMENT = "_udt";

    public static DocflowConfig instance = new DocflowConfig();

    public static List<VirtualFile> appPath = null;

    public enum BuiltInEnums implements EnumUtil.ComparableEnum {
        USER_ROLES_ENUM("userRoles");
        private final String name;
        private final String upperCase;

        private BuiltInEnums(String name) {
            this.name = name;
            this.upperCase = name.toUpperCase();
        }

        public String toString() {
            return name;
        }

        @Override
        public String getUpperCase() {
            return upperCase;
        }
    }

    public enum BuiltInTemplates implements EnumUtil.ComparableEnum {
        ID("id"),
        NONE("none"),
        LIST("list"),
        CREATE("create"),
        FORM("form"),
        FORM_DIALOG("formDialog"),
        LINKED_DOCUMENT("linkedDocument"),
        ITEM_TITLE("itemTitle"),
        SELECTOR("selector"),
        DICT("dict"),
        HISTORY("history");
        private final String name;
        private final String upperCase;

        private BuiltInTemplates(String name) {
            this.name = name;
            this.upperCase = name.toUpperCase();
        }

        @Override
        public String getUpperCase() {
            return upperCase;
        }

        public String toString() {
            return name;
        }
    }

    public enum BuiltInRoles implements EnumUtil.ComparableEnum {
        SYSTEM("system"),
        ANONYMOUS("anonymous");
        private final String name;
        private final String upperCase;

        private BuiltInRoles(String name) {
            this.name = name;
            this.upperCase = name.toUpperCase();
        }

        @Override
        public String getUpperCase() {
            return upperCase;
        }

        public String toString() {
            return name;
        }
    }

    public enum BuiltInActionsGroups implements EnumUtil.ComparableEnum {
        ALL("all"),
        IMPLICIT("imlicit"),
        IMPLICIT_TOP_LEVEL("imlicitTopLevel");

        private final String name;
        private final String upperCase;

        private BuiltInActionsGroups(String name) {
            this.name = name;
            this.upperCase = name.toUpperCase();
        }

        @Override
        public String getUpperCase() {
            return upperCase;
        }

        public String toString() {
            return name;
        }
    }

    public enum BuiltInActions implements EnumUtil.ComparableEnum {
        NEW_INSTANCE("newInstance"),
        PRE_CREATE("preCreate"),
        PRE_UPDATE("preUpdate");
        private final String name;
        private final String upperCase;

        private BuiltInActions(String name) {
            this.name = name;
            this.upperCase = name.toUpperCase();
        }

        @Override
        public String getUpperCase() {
            return upperCase;
        }

        public String toString() {
            return name;
        }
    }

    public enum BuiltInActionSource implements EnumUtil.ComparableEnum {
        SYSTEM("[System]");
        private final String name;
        private final String upperCase;

        private BuiltInActionSource(String name) {
            this.name = name;
            this.upperCase = name.toUpperCase();
        }

        @Override
        public String getUpperCase() {
            return upperCase;
        }

        public String toString() {
            return name;
        }
    }

    public boolean loaded;
    public LinkedHashMap<String, Field> fieldTypes = new LinkedHashMap<String, Field>();
    public DocType udtTypes;
    public LinkedHashMap<String, Message> messages;
    public LinkedHashMap<String, LinkedHashMap<String, Message>> messagesByFiles;
    public final TreeMap<String, DocType> documents = new TreeMap<String, DocType>();
    public TreeMap<String, Role> roles = new TreeMap<String, Role>();
    public int globalStatesCount;

    public DocType[] documentsArray;

    public TreeMap<String, DocType> documentByTable = new TreeMap<String, DocType>();

    private final Comparator<DocType> DOCUMENT_ACCENDING_SORT_BY_NAME = new Comparator<DocType>() {
        public int compare(DocType o1, DocType o2) {
            return o1.name.compareTo(o2.name);
        }
    };

    public static void _resetForTest() {
        checkState(Play.mode == Play.Mode.DEV);
        instance = new DocflowConfig();
        docTypeMap.clear();
    }

    public void prepare(Result result) {
        loadAllYamlFiles(result);
        if (result.isError())
            result.addMsg(YamlMessages.error_FailedToLoadDocflowConfig);
    }

    private void loadAllYamlFiles(Result result) {

        checkState(documents.size() == 0); // this method should work only once

        loadMessages(result);

        loadFieldTypes(result);
        loadDocuments(result);
        loadRoles(result);
        if (result.isError())
            return;

        // add user roles as build-in enum userRoles
        if (!DocflowGenerator.isCodegenExternal() || appPath != null) {
            FieldEnum userRolesEnum = new FieldEnum();
            userRolesEnum.type = Field.Type.ENUM;
            userRolesEnum.name = BuiltInEnums.USER_ROLES_ENUM.toString();
            userRolesEnum.multiple = true;
            userRolesEnum.sourcePath = (appPath != null ? appPath : Play.javaPath).get(0);
            userRolesEnum.enumTypeName = DocflowConfig.ENUMS_PACKAGE + NamesUtil.turnFirstLetterInUpperCase(userRolesEnum.name);
            userRolesEnum.strValues = new LinkedHashMap<String, FieldEnumItem>();
            userRolesEnum.accessedFields = new HashSet<String>();
            for (Role role : roles.values()) {
                final FieldEnumItem roleItem = new FieldEnumItem();
                roleItem.name = role.name;
                userRolesEnum.strValues.put(roleItem.name.toUpperCase(), roleItem);
            }
            fieldTypes.put(userRolesEnum.name.toUpperCase(), userRolesEnum);
        }

        prepareMessages(result);

        prepareFieldTypes(result);

        documentsStep1(result);
        if (result.isError())
            return;
        documentsStep2(result);
        if (result.isError())
            return;
        documentsStep3(result);
        if (result.isError())
            return;
        documentsStep4(result);
        if (result.isError())
            return;

        templatesStep1(result);
        if (result.isError())
            return;
        templatesStep2(result);
        if (result.isError())
            return;

        rolesStep1(result);
        if (result.isError())
            return;

        if (appPath == null) { // not a code generator
            linkToCodeStep1(result);
            if (result.isError())
                return;
        }

        loaded = true;

        result.addMsg(YamlMessages.debug_DocflowConfigLoadedSuccessfully);
    }

    private void templatesStep2(final Result result) {

        TreeMap<String, Template> templateNames = new TreeMap<String, Template>();

        for (DocType docType : documents.values()) {

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
                        if (fldName.equalsIgnoreCase(DEFAULT_FIELDS_TEMPLATE)) {
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
                            if (fieldModel.type == Field.Type.REFERS) {
                                final FieldReference refFld = (FieldReference) fieldModel;
                                final DocType refDoc = documents.get(refFld.refDocument.toUpperCase());
                                final Template template = refDoc.templates.get(fieldTemplate.template.toUpperCase());
                                if (template == null) {
                                    result.addMsg(YamlMessages.error_DocumentTemplateNotDefinedTemplate, docType.name,
                                            tmpl.name, fieldModel.name, refDoc.name, fieldTemplate.template);
                                    continue;
                                }
                            }
                            if (fieldModel.type == Field.Type.POLYMORPHIC_REFERS) {
                                final FieldPolymorphicReference refFld = (FieldPolymorphicReference) fieldModel;
                                if (refFld.refDocuments != null)
                                    for (int i = 0; i < refFld.refDocuments.length; i++) {
                                        final DocType refDoc = documents.get(refFld.refDocuments[i].toUpperCase());
                                        final Template template = refDoc.templates.get(fieldTemplate.template.toUpperCase());
                                        if (template == null) {
                                            result.addMsg(YamlMessages.error_DocumentTemplateNotDefinedTemplate, docType.name,
                                                    tmpl.name, fieldModel.name, refDoc.name, fieldTemplate.template);
                                            continue;
                                        }
                                    }
                                else
                                    for (DocType refDoc : documents.values()) {
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
                        if (field.type == Field.Type.REFERS && field.implicitFieldType == null) { // reason: implicit FK fields defined as FieldSimple
                            DocType doc = documents.get(((FieldReference) field).refDocument.toUpperCase());
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

                if (tmpl.tabs != null)
                    for (TemplateTab tab : tmpl.tabs.values()) {
                        final DocType document = documents.get(tab.docType.toUpperCase());
                        if (document == null) {
                            result.addMsg(YamlMessages.error_DocumentTemplateTabUnknownDocumentType, docType.name, tmpl.name, tab.name, tab.docType);
                            continue;
                        }
                        final Template template = (document.templates != null) ? document.templates.get(tab.template.toUpperCase()) : null;
                        if (template == null) {
                            result.addMsg(YamlMessages.error_DocumentTemplateTabTemplateNotFoundInDocumentType, docType.name, tmpl.name, tab.name, tab.docType, tab.template);
                            continue;
                        }
                        // hide field, if it's name equal to tab name
                        Field field = docType.fields.get(tab.name.toUpperCase());
                        if (field != null)
                            tmpl.templateNameByField[field.index] = BuiltInTemplates.LINKED_DOCUMENT.toString();
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

                        } else if (!column.name.equals(FIELD_SELF)) {
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

    private void templatesStep1(final Result result) {
        for (DocType docType : documents.values()) {

            // apply defaults
            if (docType.templates == null)
                docType.templates = new LinkedHashMap<String, Template>();

            Template none = docType.templates.get(BuiltInTemplates.NONE);
            if (none != null) {
                result.addMsg(YamlMessages.error_DocumentTemplateImplicitTemplateOverride, docType.name, none.name);
            }

            Template create = docType.templates.get(BuiltInTemplates.CREATE);
            if (create != null) {
                result.addMsg(YamlMessages.error_DocumentTemplateImplicitTemplateOverride, docType.name, create.name);
            }

            if (!docType.report) {
                Template id = docType.templates.get(BuiltInTemplates.ID.getUpperCase());
                if (id != null) {
                    result.addMsg(YamlMessages.error_DocumentTemplateImplicitTemplateOverride, docType.name, id.name);
                } else {
                    id = new Template();
                    id.name = BuiltInTemplates.ID.toString();
                    id.mode = "L";
                    id.screen = false;
                    docType.templates.put(BuiltInTemplates.ID.getUpperCase(), id);
                }

                Template history = docType.templates.get(BuiltInTemplates.HISTORY.getUpperCase());
                if (history != null) {
                    result.addMsg(YamlMessages.error_DocumentTemplateImplicitTemplateOverride, docType.name, history.name);
                } else {
                    history = new Template();
                    history.name = BuiltInTemplates.HISTORY.toString();
                    history.mode = "L";
                    history.screen = false;

                    // add State to visible fields of History template
                    final Field stateField = docType.fieldByFullname.get(ImplicitFields.STATE.name());
                    if (stateField != null) {
                        history.add = new LinkedHashMap<String, Item>();
                        history.add.put(ImplicitFields.STATE.name(), stateField);
                    }

                    // Process refers fields, so they would be stored as ID only
                    history.fieldsTemplates = new LinkedHashMap<String, TemplateField>();
                    for (Field fld : docType.fields.values()) {
                        if (fld.implicitFieldType != null || fld.derived) // leaving State field in template
                            continue;
                        if (fld.type == Field.Type.REFERS) {
                            TemplateField templateField = new TemplateField();
                            templateField.name = fld.name;
                            templateField.template = BuiltInTemplates.ID.toString();
                            history.fieldsTemplates.put(templateField.name.toUpperCase(), templateField);
                        }
                    }
                    docType.templates.put(BuiltInTemplates.HISTORY.getUpperCase(), history);
                }
            }

            Template list = docType.templates.get(DocflowConfig.BuiltInTemplates.LIST.getUpperCase());
            if (list == null) {
                list = new Template();
                list.name = "list";
                list.accessedFields = new HashSet<String>();
                docType.templates.put(list.name.toUpperCase(), list);
            }
            if (!list.accessedFields.contains("COLUMNS") || list.columns.size() < 1) {
                list.columns = new LinkedHashMap<String, Item>();
                list.columns.put("_SELF", new Item("_self"));
            }
            if (!list.accessedFields.contains("MODE"))
                list.mode = "";
            if (!list.accessedFields.contains("SCREEN"))
                list.screen = true;

            if (!docType.report) {
                Template dict = docType.templates.get(BuiltInTemplates.DICT.getUpperCase());
                if (dict == null) {
                    dict = new Template();
                    dict.name = "dict";
                    dict.accessedFields = new HashSet<String>();
                    docType.templates.put(dict.name.toUpperCase(), dict);
                }
                if (!dict.accessedFields.contains("FIELDS")) {
                    dict.fields = new LinkedHashMap<String, Item>();
                    Item idField = new Item();
                    idField.name = "id";
                    dict.fields.put(idField.name.toUpperCase(), idField);
                    Item textField = new Item();
                    textField.name = "text";
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

            Template form = docType.templates.get(DocflowConfig.BuiltInTemplates.FORM.getUpperCase());
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
                        linkedDocument.name = "linkedDocument";
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
                Template formTitle = docType.templates.get(BuiltInTemplates.ITEM_TITLE.getUpperCase());
                if (formTitle == null) {
                    formTitle = new Template();
                    formTitle.name = "formTitle";
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
                    tmpl.fieldsMask = docType.derivedFieldsMask.copy();
                    tmpl.fieldsMask.inverse(); // all but derived (including calculated)
                    tmpl.fieldsMask.subtract(docType.implicitFieldsMask);
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

                if (tmpl.add != null)
                    for (Item fld : tmpl.add.values()) {
                        final Field fieldModel = docType.fieldByFullname.get(fld.name.toUpperCase());
                        if (fieldModel == null) {
                            result.addMsg(YamlMessages.error_DocumentTemplateFieldNotFound, docType.name, tmpl.name, fld.name);
                            continue;
                        }
                        if (removeMask == null || !removeMask.get(fieldModel.index)) {
                            for (int k = fieldModel.index; k < fieldModel.endIndex; k++)
                                tmpl.fieldsMask.set(k, true);
                            for (FieldStructure s = fieldModel.structure; s != null; s = s.structure)
                                tmpl.fieldsMask.set(s.index, true);
                        }
                    }
                if (!tmpl.document.report &&
                        // templates 'list' and 'dict'
                        (EnumUtil.isEqual(DocflowConfig.BuiltInTemplates.LIST, tmpl.name) || EnumUtil.isEqual(BuiltInTemplates.DICT, tmpl.name)))
                    tmpl.fieldsMask.set(tmpl.document.fieldByFullname.get("TEXT").index, true); // always includes 'text' field
            }
        }
    }

    private void prepareMessages(Result result) {
        if (messages != null)
            for (Message msg : messages.values()) {

                final String postfix = msg.params != null ? ("_" + msg.params.size()) : "";
                switch (msg.type) {
                    case ERROR:
                        msg.fieldName = "error_" + NamesUtil.turnFirstLetterInUpperCase(msg.key) + postfix;
                        msg.resultCode = "Failed";
                        break;
                    case INFO:
                        msg.fieldName = "info_" + NamesUtil.turnFirstLetterInUpperCase(msg.key) + postfix;
                        msg.resultCode = "Ok";
                        break;
                    case WARN:
                        msg.fieldName = "warning_" + NamesUtil.turnFirstLetterInUpperCase(msg.key) + postfix;
                        msg.resultCode = "Warning";
                        break;
                }

                if (Strings.isNullOrEmpty(msg.en))
                    msg.en = msg.key;
                else
                    msg.en = replaceParams(msg, msg.en, result);

                if (Strings.isNullOrEmpty(msg.ru))
                    msg.ru = msg.en;
                else
                    msg.ru = replaceParams(msg, msg.ru, result);

                if (Strings.isNullOrEmpty(msg.ruHtml))
                    msg.ruHtml = msg.ru;
                else
                    msg.ruHtml = replaceParams(msg, msg.ruHtml, result);
            }
    }

    public static Pattern messageNamedParam = Pattern.compile("\\$(\\w*)");

    private String replaceParams(Message msg, String src, Result result) {
        final Matcher matcher = messageNamedParam.matcher(src);
        StringBuilder res = null;
        int p = 0;
        while (matcher.find()) {
            final String paramName = matcher.group(1);
            final String replacement = msg.params.get(paramName);
            if (replacement == null) {
                result.addMsg(YamlMessages.error_MessageUnknownParameter, msg.key, paramName);
                continue;
            }
            if (res == null)
                res = new StringBuilder();
            res.append(src, p, matcher.start());
            res.append(replacement);
            p = matcher.end();
        }
        if (res == null)
            return src;
        res.append(src.substring(p));
        return res.toString();
    }

    private void prepareFieldTypes(Result result) {
        final Stack<String> path = new Stack<String>();

        recurrentProcessStructure(fieldTypes, path, result);

        for (Field fldType : fieldTypes.values())
            fldType.udtTypeRoot = true;

        udtTypes = new DocType();
        udtTypes.udt = true;
        udtTypes.report = true; // at least, it will not be linked to db table
        udtTypes.name = UDT_DOCUMENT;
        udtTypes.fields = fieldTypes;
        documents.put(udtTypes.name.toUpperCase(), udtTypes);
    }

    private void recurrentProcessStructure(LinkedHashMap<String, Field> fields, Stack<String> path, Result result) {
        for (Field fldType : fields.values()) {
            if (fldType.udtType != null)
                recurrentProcessFieldType(fldType, path, result);
            else if (fldType.type == Field.Type.STRUCTURE)
                recurrentProcessStructure(((FieldStructure) fldType).fields, path, result);
        }
    }

    private void recurrentProcessFieldType(Field fldType, Stack<String> path, Result result) {

        final Field parentType = fieldTypes.get(fldType.udtType.toUpperCase());
        if (parentType == null) {
            result.addMsg(YamlMessages.error_UDTypeHasUnknownType, fldType.name, fldType.udtType);
            fldType.type = Field.Type.STRING; // Just to let process other fields
            return;
        }

        if (path.contains(parentType.name)) { // it's loop
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < path.size(); i++) {
                if (sb.length() > 0)
                    sb.append(",");
                sb.append(path.get(i));
            }
            result.addMsg(YamlMessages.error_UDTypeCyclingDependenciesWithTypes, fldType.name, sb.toString());
            fldType.type = Field.Type.STRING; // Just to let process other fields
            return;
        }

        if (parentType.type == null) {
            path.push(fldType.name);
            recurrentProcessFieldType(parentType, path, result);
            path.pop();
        } else if (parentType.type == Field.Type.STRUCTURE)
            recurrentProcessStructure(((FieldStructure) parentType).fields, path, result);

        // apply named enum type
        if (fldType.type == Field.Type.ENUM) {
            if (fldType.udtType != null) {
                Field type = fieldTypes.get(fldType.udtType.toUpperCase());
                if (type == null)
                    result.addMsg(YamlMessages.error_TypeHasUnknownType, type.name, type.udtType);
                else if (!(type instanceof FieldEnum))
                    result.addMsg(YamlMessages.error_TypeNotAnEnumType, type.name, type.udtType);
                else
                    type.mergeTo(fldType);
            } else {
                FieldEnum fieldEnum = (FieldEnum) fldType;
                fieldEnum.enumTypeName = "docflow.enums." + NamesUtil.turnFirstLetterInUpperCase(fldType.name);
            }
        }
        // apply named structure type
        else if (fldType.type == Field.Type.STRUCTURE) {
            if (fldType.udtType != null) {
                Field type = fieldTypes.get(fldType.udtType.toUpperCase());
                if (type == null)
                    result.addMsg(YamlMessages.error_TypeHasUnknownType, type.name, type.udtType);
                else if (!(type instanceof FieldStructure))
                    result.addMsg(YamlMessages.error_TypeNotAStructureType, type.name, type.udtType);
                else
                    type.deepCopy().mergeTo(fldType);
            }
        } else
            // TODO: Check that not an enum, structure or refers are merged into simple field
            parentType.mergeTo(fldType);
    }

    private void linkActionsAndPreconditions(DocType docType, Result result) {
        final String className = "docflow.actions.Actions" + docType.name;

        for (Action action : docType.actionsArray)
            if (action.params != null) {
                action.paramsClass = Play.classloader.getClassIgnoreCase(action.getFullParamsClassName());
                if (action.paramsClass == null) {
                    result.addMsg(YamlMessages.error_DocumentActionFailedToFindClass, docType.name, action.name, action.getFullParamsClassName());
                    continue;
                }
            }

        final Class actionsType = Play.classloader.getClassIgnoreCase(className);
        if (actionsType == null) {
            result.addMsg(YamlMessages.error_DocumentNoCorrespondedActionsClass, docType.name, className);
            return;
        }

        final Method[] methods = actionsType.getMethods();
        for (Method method : methods) {
            if (method.getDeclaringClass() != actionsType)
                continue;
            final String methodName = method.getName().toUpperCase();
            final Class<?>[] params = method.getParameterTypes();
            final Class<?> returnType = method.getReturnType();

            if (EnumUtil.isEqual(BuiltInActions.PRE_CREATE, methodName)) {
                boolean ok = params.length == 2;
                if (ok) {
                    final String param0Class = params[0].getCanonicalName();
                    final String returnClass = returnType.getCanonicalName();
                    ok &= param0Class != null && param0Class.equals(docType.getClassName()); // first parameter EntityType
                    ok &= params[1] == Result.class; // 2nd parameter Result
                    ok &= returnClass != null && returnClass.equals(docType.getClassName()); // return void or EntityType
                }
                if (!ok) {
                    result.addMsg(YamlMessages.error_DocumentActionExpectedToHaveSignature, docType.name, actionsType.getName() + "." + method.getName(),
                            "public static " + docType.name + " " + BuiltInActions.PRE_CREATE.toString() + "(" + docType.name + ", Result result)");
                    continue;
                }
                docType.preCreateMethod = method;
                continue;
            }

            if (EnumUtil.isEqual(BuiltInActions.PRE_UPDATE, methodName)) {
                final Action updateAction = docType.actions.get(ImplicitActions.UPDATE.name());
                if (updateAction.params == null) {
                    result.addMsg(YamlMessages.error_DocumentPreUpdateRequiresParametersOnUpdate, docType.name, actionsType.getName() + "." + method.getName());
                    continue;
                }

                boolean ok = params.length == 1;
                if (ok) {
                    final String param0Class = params[0].getCanonicalName();
                    ok &= param0Class != null && param0Class.equals(docType.getClassName()); // first parameter EntityType
                    ok &= returnType == updateAction.paramsClass; // return void or EntityType
                }
                if (!ok) {
                    result.addMsg(YamlMessages.error_DocumentActionExpectedToHaveSignature, docType.name, actionsType.getName() + "." + method.getName(),
                            "public static " + docType.name + "." + updateAction.paramsClassName + " " + BuiltInActions.PRE_UPDATE.toString() + "(" + docType.name + ")");
                    continue;
                }
                docType.preUpdateMethod = method;
                continue;
            }

            Precondition precondition = docType.preconditions == null ? null : docType.preconditions.get(methodName);
            final Action action = docType.actions.get(methodName);

            if (precondition != null) {
                boolean ok = params.length == 1;
                if (ok) {
                    final int mod = method.getModifiers();
                    final String param0Class = params[0].getCanonicalName();
                    ok &= Modifier.isPublic(mod) && Modifier.isStatic(mod);
                    ok &= param0Class.equals(docType.getClassName());
                    ok &= boolean.class == returnType; // return void or EntityType
                }
                if (!ok) {
                    result.addMsg(YamlMessages.error_DocumentPreconditionExpectedToHaveSignature, docType.name,
                            actionsType.getName() + "." + method.getName(),
                            preconditionMethodSignature(docType, precondition.name));
                    continue;
                }
                for (Transition transition : precondition.transitions)
                    transition.preconditionEvaluator = method;

            } else if (action != null) {
                // parameters are: [doc,] [params,] result
                final int paramsCount = (action.service ? 0 : 1) + (action.params != null ? 1 : 0) + 1;
                boolean ok = params.length == paramsCount;
                if (ok) {
                    final int mod = method.getModifiers();
                    final String param0Class = params[0].getCanonicalName();
                    ok &= Modifier.isPublic(mod) && Modifier.isStatic(mod);
                    ok &= action.service || param0Class != null && param0Class.equals(docType.getClassName()); // first parameter EntityType, if this is not a service
                    ok &= !(action.params != null) || params[action.service ? 0 : 1].getName().equals(action.getFullParamsClassName()); // parameters comes after EntityType,  if there are some
                    ok &= params[paramsCount - 1] == Result.class; // last parameter Result
                    ok &= returnType == void.class || Object.class.isAssignableFrom(returnType); // return void or EntityType
                }
                if (!ok) {
                    result.addMsg(YamlMessages.error_DocumentActionExpectedToHaveSignature, docType.name, actionsType.getName() + "." + method.getName(), actionMethodSignature(docType, action));
                    continue;
                }
                action.actionMethod = method;
            } else {
                result.addMsg(YamlMessages.error_DocumentMethodNoSuchActionOrPreconditionInModel, docType.name, actionsType.getName() + "." + method.getName());
                continue;
            }
        }

        for (Action action : docType.actionsArray)
            if (action.service && action.actionMethod == null)
                result.addMsg(YamlMessages.error_DocumentActionServiceActionMustBeImplemented, docType.name, action.name, actionsType.getName(), actionMethodSignature(docType, action));

        if (docType.preconditions != null)
            for (Precondition precondition : docType.preconditions.values()) {
                if (precondition.transitions.get(0).preconditionEvaluator == null)
                    result.addMsg(YamlMessages.error_DocumentPreconditionMustBeImplemented,
                            docType.name, precondition.name, actionsType.getName(),
                            preconditionMethodSignature(docType, precondition.name));
            }
    }

    private String actionMethodSignature(DocType docType, Action action) {
        return "public static [void | Object] " + action.name + "(" +
                (action.service ? "" : docType.name + " doc, ") +
                (action.params == null ? "" : docType.name + "." + action.paramsClassName + " params, ") +
                "Result result)";
    }

    private String preconditionMethodSignature(DocType docType, String methodName) {
        return "public static boolean " + methodName + "(" + docType.name + " doc)";
    }

    /**
     * 1. Populates Document.jsonBinding for all documents.
     * 2. Links Action to java implementations.
     */
    private void linkToCodeStep1(Result result) {

        for (Field fld : fieldTypes.values()) {
            if (fld.type != Field.Type.ENUM)
                continue;
            reflectEnum((FieldEnum) fld, result);
        }

        for (int i = 0; i < documentsArray.length; i++) {
            DocType docType = documentsArray[i];
            if (docType.udt)
                continue;
            final String className = docType.getClassName();
            final Class type = Play.classloader.getClassIgnoreCase(className);
            if (type == null) {
                result.addMsg(YamlMessages.error_DocumentNoCorrespondedModelClass, docType.name, className);
                continue;
            }
            docType.jsonBinder = JsonTypeBinder.factory.get(type);
            if (!docType.report && docType.jsonBinder.recordAccessor == null) {
                result.addMsg(YamlMessages.error_DocumentCorrespondedClassMustBeChildOfEntityBase, docType.name, className, Document.class.getName());
                continue;
            }

            docType.jsonBinder.linkDocumentFieldsToFieldsAccessors(result);

            linkQueriesAndCalculateMethods(docType, result);

            linkActionsAndPreconditions(docType, result);

            linkFilters(docType);

            linkSortOrders(docType);

            linkEnums(docType, result);

            linkRelations(docType, result);
        }
    }

    public static final String ENUMS_PACKAGE = "docflow.enums.";

    private void linkRelations(DocType docType, Result result) {
        if (docType.relations == null)
            return;

        for (DocumentRelation relation : docType.relations.values()) {
            final String className = "docflow.relations." + NamesUtil.turnFirstLetterInUpperCase(relation.name);
            final Class relationsType = Play.classloader.getClassIgnoreCase(className);
            if (relationsType == null) {
                result.addMsg(YamlMessages.error_DocumentNoCorrespondedRelationsClass, docType.name, className);
                continue;
            }
            String methodName = "isTrue";
            try {
                Method method = relationsType.getMethod(methodName, docType.jsonBinder.type, Document.class);
                final Class<?>[] params = method.getParameterTypes();
                final Class<?> returnType = method.getReturnType();
                boolean ok = params.length == 2;
                if (ok) {
                    final int mod = method.getModifiers();
                    final String param0Class = params[0].getCanonicalName();
                    ok &= Modifier.isPublic(mod) && Modifier.isStatic(mod);
                    ok &= param0Class.equals(docType.getClassName());
                    ok &= Document.class.isAssignableFrom(params[1]);
                    ok &= boolean.class == returnType; // return void or EntityType
                }
                if (!ok) {
                    result.addMsg(YamlMessages.error_DocumentRelationExpectedToHaveSignature, docType.name,
                            relationsType.getName() + "." + method.getName(),
                            relationMethodSignature(docType, methodName));
                    continue;
                }
                relation.evaluator = method;
            } catch (NoSuchMethodException e) {
                result.addMsg(YamlMessages.error_DocumentRelationServiceActionMustBeImplemented, docType.name,
                        relation.name, className, relationMethodSignature(docType, methodName));
                continue;
            }
        }
    }

    private String relationMethodSignature(DocType docType, String methodName) {
        return "public static boolean " + methodName + "(" + docType.name + " doc, Document user)";
    }

    private void linkEnums(DocType docType, Result result) {
        for (Field fld : docType.allFields) {
            if (fld.type != Field.Type.ENUM)
                continue;
            FieldEnum fieldEnum = (FieldEnum) fld;
            if (fieldEnum.udtType != null) {
                FieldEnum udtType = (FieldEnum) fieldTypes.get(fieldEnum.udtType.toUpperCase());
                fieldEnum.values = udtType.values;
                fieldEnum.enumType = udtType.enumType;
                continue;
            }
            reflectEnum(fieldEnum, result);
        }
    }

    private void reflectEnum(FieldEnum fieldEnum, Result result) {
        Class enumClass = Play.classloader.getClassIgnoreCase(fieldEnum.enumTypeName);
        if (enumClass == null) {
            result.addMsg(YamlMessages.error_EnumImplementationNotFound, fieldEnum.enumTypeName);
            return;
        }
        if (!enumClass.isEnum()) {
            result.addMsg(YamlMessages.error_EnumNotEnumType, fieldEnum.enumTypeName);
            return;
        }
        fieldEnum.values = new LinkedHashMap<String, Enum>();
        Object[] values = enumClass.getEnumConstants();
        for (int j = 0; j < values.length; j++) {
            Object value = values[j];
            if (fieldEnum.strValues.get(value.toString().toUpperCase()) == null) {
                result.addMsg(YamlMessages.error_EnumImplementationContainsUnderfinedValue, fieldEnum.enumTypeName, value.toString());
                continue;
            }
            fieldEnum.values.put(value.toString().toUpperCase(), (Enum) value);
        }
        for (Item item : fieldEnum.strValues.values()) {
            if (fieldEnum.values.get(item.name.toUpperCase()) == null) {
                result.addMsg(YamlMessages.error_EnumImplementationMissingValue, fieldEnum.enumTypeName, item.name);
                continue;
            }
        }
    }

    private void linkQueriesAndCalculateMethods(DocType docType, Result result) {
        final String listQueryClassName = "docflow.queries.Query" + docType.name;
        final Class listQueryClass = Play.classloader.getClassIgnoreCase(listQueryClassName);
        if (listQueryClass == null) {
            result.addMsg(YamlMessages.error_DocumentNoCorrespondedListQueryClass, docType.name, listQueryClassName);
            return;
        }
        final Method[] methods = listQueryClass.getMethods();
        docType.queryMethods = new TreeMap<String, Method>();
        for (int j = 0; j < methods.length; j++) {
            Method method = methods[j];
            if (method.getDeclaringClass() != listQueryClass)
                continue;
            final int mod = method.getModifiers();
            if (!Modifier.isPublic(mod) || !Modifier.isStatic(mod))
                continue;
            final Class<?>[] params = method.getParameterTypes();
            if (method.getName().equals("calculate")) {
                if (params.length != 3 || !Document.class.isAssignableFrom(params[0]) || params[1] != BitArray.class || params[2] != DocumentAccessActionsRights.class) {
                    result.addMsg(YamlMessages.error_DocumentActionExpectedToHaveSignature, docType.name, listQueryClass.getName() + "." + method.getName());
                    continue;
                }
                docType.calculateMethod = method;
            } else {
                // TODO: Move list to API: 1. Instead of request give parameters map; 2. Add 'result' as 3rd parameter; 3. Refactor existing projects
                if (params.length != 2 || params[0] != Http.Request.class || params[1] != DocumentAccessActionsRights.class) {
                    result.addMsg(YamlMessages.error_DocumentQueryInvalidParameters, docType.name, listQueryClass.getName() + "." + method.getName());
                    continue;
                }
                docType.queryMethods.put(method.getName().toUpperCase(), method);
            }
        }
    }

    private void linkFilters(DocType docType) {
        final String filterClassName = docType.getClassName() + "$Filter";
        final Class filterType = Play.classloader.getClassIgnoreCase(filterClassName);
        if (filterType != null && filterType.isEnum()) {
            docType.filterEnums = new TreeMap<String, Enum>();
            final Object[] vals = filterType.getEnumConstants();
            if (vals.length > 0) {
                for (int j = 0; j < vals.length; j++) {
                    Object val = vals[j];
                    docType.filterEnums.put(val.toString().toUpperCase(), (Enum) val);
                }
                docType.defaultFilterEnum = (Enum) vals[0];
            }
        }
    }

    private void linkSortOrders(DocType docType) {
        final String sortOrderClassName = docType.getClassName() + "$SortOrder";
        final Class sortOrderType = Play.classloader.getClassIgnoreCase(sortOrderClassName);
        if (sortOrderType != null && sortOrderType.isEnum()) {
            docType.sortOrderEnums = new TreeMap<String, Enum>();
            final Object[] vals = sortOrderType.getEnumConstants();
            if (vals.length > 0) {
                for (int j = 0; j < vals.length; j++) {
                    Object val = vals[j];
                    docType.sortOrderEnums.put(val.toString().toUpperCase(), (Enum) val);
                }
                docType.defaultSortOrderEnum = (Enum) vals[0];
            }
        }
    }

    /**
     * 1. Checks that roles refers existing documents
     * 2. Calculates roles rights masks
     * 3. Collect relations menthined in rights in document.relations
     * 4. Calculates rights masks for relations
     */

    private void rolesStep1(Result result) {

        int rindex = 0;

        final Role system = roles.get(BuiltInRoles.SYSTEM.name());
        if (system != null)
            result.addMsg(YamlMessages.error_RoleDocumentHasReservedName, system.name);
        else {
            final Role role = new Role();

            final RoleRight allFields = new RoleRight();
            allFields.name = "_all";
            final LinkedHashMap<String, RoleRight> allFieldsRight = new LinkedHashMap<String, RoleRight>();
            allFieldsRight.put(allFields.name.toUpperCase(), allFields);

            final RoleRight allActions = new RoleRight();
            allActions.name = "all";
            final LinkedHashMap<String, RoleRight> linkedDocAnyActionRight = new LinkedHashMap<String, RoleRight>();
            linkedDocAnyActionRight.put(allFields.name.toUpperCase(), allActions);

            // Rule: System is allowed to 'delete', that should be specified separatly
            final RoleRight deleteAction = new RoleRight();
            deleteAction.name = ImplicitActions.DELETE.toString();
            linkedDocAnyActionRight.put(deleteAction.name.toUpperCase(), deleteAction);

            // Rule: System is allowed to 'create', that should be specified separatly
            LinkedHashMap<String, RoleRight> anyActionRight = new LinkedHashMap<String, RoleRight>(linkedDocAnyActionRight);
            final RoleRight createAction = new RoleRight();
            createAction.name = ImplicitActions.CREATE.toString();
            anyActionRight.put(createAction.name.toUpperCase(), createAction);

            role.system = true;
            role.name = "System";
            role.documents = new LinkedHashMap<String, RoleDocument>();
            for (DocType docType : documents.values()) {
                final RoleDocument roleDocument = new RoleDocument();
                roleDocument.role = role;
                roleDocument.name = docType.name;
                roleDocument.view = roleDocument.update = allFieldsRight;
                roleDocument.actions = docType.linkedDocument ? linkedDocAnyActionRight : anyActionRight;
                role.documents.put(roleDocument.name.toUpperCase(), roleDocument);
            }

            final TreeMap<String, Role> newRoles = new TreeMap<String, Role>();
            newRoles.put(role.name.toUpperCase(), role);
            newRoles.putAll(roles);
            roles = newRoles;
        }

        for (Role role : roles.values()) {
            role.index = rindex++;
            if (role.documents == null)
                role.documents = new LinkedHashMap<String, RoleDocument>(0);
            else
                for (RoleDocument roleDocument : role.documents.values()) {
                    roleDocument.role = role;
                    final DocType doc = documents.get(roleDocument.name.toUpperCase());
                    if (doc == null) {
                        result.addMsg(YamlMessages.error_RoleDocumentContainsUndefinedDocument, role.name, roleDocument.name);
                        continue;
                    }
                    roleDocument.document = doc;
                    roleDocument.viewMask = new BitArray(doc.allFields.size());
                    roleDocument.updateMask = new BitArray(doc.allFields.size());
                    roleDocument.actionsMask = new BitArray(doc.actionsArray.length);

                    // Rule: role is allowed to retrieve document, if document is mentioned within role
                    if (roleDocument.actions == null || roleDocument.actions.get(ImplicitActions.RETRIEVE.name()) == null)
                        roleDocument.actionsMask.set(ImplicitActions.RETRIEVE.index, true);

                    for (int i = 0; i < doc.implicitFields.size(); i++) {
                        Field field = doc.implicitFields.get(i);
                        // note: i == 0 makes first ID viewable.  other (subtables's) IDes expected to be invisible
                        roleDocument.viewMask.set(field.index, i == 0 || field.implicitFieldType != ImplicitFields.ID);
                    }

                    if (doc.relations != null) {
                        roleDocument.relations = new Relation[doc.relations.size()];
                        int i = 0;
                        for (DocumentRelation docRelation : doc.relations.values()) {
                            Relation relation = new Relation();
                            relation.documentRelation = docRelation;
                            relation.viewMask = new BitArray(doc.allFields.size());
                            relation.updateMask = new BitArray(doc.allFields.size());
                            relation.actionsMask = new BitArray(doc.actionsArray.length);
                            relation.retrieveMask = new BitArray(doc.relations.size());
                            relation.retrieveMask.set(docRelation.index, true);
                            roleDocument.relations[i++] = relation;
                        }
                    }

                    if (roleDocument.view != null)
                        for (RoleRight viewRight : roleDocument.view.values()) {
                            String key = viewRight.name.toUpperCase();
                            if (key.startsWith("_")) {
                                final FieldsGroup fieldsGroup = doc.fieldsGroups.get(key.substring(1));
                                if (fieldsGroup == null) {
                                    result.addMsg(YamlMessages.error_RoleDocumentContainsUndefinedFieldsGroupInView, role.name, roleDocument.name, viewRight.name.substring(1));
                                    continue;
                                }
                                if (viewRight.relations != null)
                                    for (int i = 0; i < viewRight.relations.length; i++) {
                                        Relation relation = findRelationByName(roleDocument, doc, role, viewRight.relations[i], result);
                                        if (relation != null)
                                            relation.viewMask.add(fieldsGroup.mask);
                                    }
                                else
                                    roleDocument.viewMask.add(fieldsGroup.mask);
                            } else {
                                final Field field = doc.fieldByFullname.get(key);
                                if (field == null) {
                                    result.addMsg(YamlMessages.error_RoleDocumentContainsUndefinedFieldInView, role.name, roleDocument.name, viewRight.name);
                                    continue;
                                }
                                if (viewRight.relations != null)
                                    for (int i = 0; i < viewRight.relations.length; i++) {
                                        Relation relation = findRelationByName(roleDocument, doc, role, viewRight.relations[i], result);
                                        if (relation != null) {
                                            relation.viewMask.set(field.index, true);
                                            // note: 'field.index + 2' skips ID field
                                            for (int j = field.index + 2; j < field.endIndex; j++)
                                                relation.viewMask.set(j, true);
                                            Field struct = field;
                                            while ((struct = struct.structure) != null)
                                                relation.viewMask.set(struct.index, true);
                                        }
                                    }
                                else {
                                    roleDocument.viewMask.set(field.index, true);
                                    // note: 'field.index + 2' skips ID field
                                    for (int i = field.index + 2; i < field.endIndex; i++)
                                        roleDocument.viewMask.set(i, true);
                                    Field struct = field;
                                    while ((struct = struct.structure) != null)
                                        roleDocument.viewMask.set(struct.index, true);
                                }
                            }
                        }

                    if (roleDocument.update != null)
                        for (RoleRight updateRight : roleDocument.update.values()) {
                            String key = updateRight.name.toUpperCase();
                            if (key.startsWith("_")) {
                                final FieldsGroup fieldsGroup = doc.fieldsGroups.get(key.substring(1));
                                if (fieldsGroup == null) {
                                    result.addMsg(YamlMessages.error_RoleDocumentContainsUndefinedFieldsGroupInUpdate, role.name, roleDocument.name, updateRight.name.substring(1));
                                    continue;
                                }
                                if (updateRight.relations != null)
                                    for (int i = 0; i < updateRight.relations.length; i++) {
                                        Relation relation = findRelationByName(roleDocument, doc, role, updateRight.relations[i], result);
                                        if (relation != null)
                                            relation.updateMask.add(fieldsGroup.mask);
                                    }
                                else
                                    roleDocument.updateMask.add(fieldsGroup.mask);
                            } else {
                                final Field field = doc.fieldByFullname.get(key);
                                if (field == null) {
                                    result.addMsg(YamlMessages.error_RoleDocumentContainsUndefinedFieldInUpdate, role.name, roleDocument.name, updateRight.name);
                                    continue;
                                }
                                if (updateRight.relations != null) {
                                    for (int i = 0; i < updateRight.relations.length; i++) {
                                        Relation relation = findRelationByName(roleDocument, doc, role, updateRight.relations[i], result);
                                        if (relation != null) {
                                            relation.updateMask.set(field.index, true);
                                            // note: 'field.index + 2' skips ID field
                                            for (int j = field.index; j < field.endIndex; j++)
                                                relation.updateMask.set(j, true);
                                            Field struct = field;
                                            while ((struct = struct.structure) != null)
                                                relation.updateMask.set(struct.index, true);
                                        }
                                    }
                                } else {
                                    roleDocument.updateMask.set(field.index, true);
                                    // note: 'field.index + 2' skips ID field
                                    for (int i = field.index + 2; i < field.endIndex; i++)
                                        roleDocument.updateMask.set(i, true);
                                    Field struct = field;
                                    while ((struct = struct.structure) != null)
                                        roleDocument.updateMask.set(struct.index, true);
                                }
                            }
                        }

                    if (roleDocument.actions != null)
                        for (RoleRight actionRight : roleDocument.actions.values()) {
                            if (EnumUtil.isEqual(BuiltInActionsGroups.ALL, actionRight.name)) {

                                BitArray allActions;
                                allActions = new BitArray(roleDocument.document.actionsArray.length);
                                allActions.inverse();

                                // Rule: DELETE and RECOVER actions are not included in 'all' actions group.
                                allActions.set(ImplicitActions.DELETE.index, false);
                                allActions.set(ImplicitActions.RECOVER.index, false);

                                // Rule: linkedDocument may not be created directly.  Only indirectly by assigning to a field of 'subj' document.
                                if (doc.linkedDocument)
                                    allActions.set(ImplicitActions.CREATE.index, false);

                                if (actionRight.relations != null)
                                    for (int i = 0; i < actionRight.relations.length; i++) {
                                        Relation relation = findRelationByName(roleDocument, doc, role, actionRight.relations[i], result);
                                        if (relation != null)
                                            relation.actionsMask.add(allActions);
                                    }
                                else
                                    roleDocument.actionsMask.add(allActions);

                            } else { // regular action
                                final Action action = doc.actions.get(actionRight.name.toUpperCase());
                                if (action == null) {
                                    result.addMsg(YamlMessages.error_RoleDocumentContainsUndefinedActionInActions, role.name, roleDocument.name, actionRight.name);
                                    continue;
                                }
                                final int index = action.index;
                                boolean isRecoverAction = ImplicitActions.RECOVER.name().equals(actionRight.name.toUpperCase());
                                boolean isCreateAction = ImplicitActions.CREATE.name().equals(actionRight.name.toUpperCase());
                                if (isRecoverAction) {
                                    result.addMsg(YamlMessages.error_RoleDocumentMistakenlySpecifiesRecoverAction, role.name, roleDocument.name, ImplicitActions.RECOVER.toString(), ImplicitActions.DELETE.toString());
                                    continue;
                                }
                                if (isCreateAction && doc.linkedDocument) {
                                    result.addMsg(YamlMessages.error_RoleDocumentCreateActionNoAllowedForLinkedDocument, role.name, roleDocument.name, actionRight.name);
                                    continue;
                                }
                                if (actionRight.relations != null) {
                                    for (int i = 0; i < actionRight.relations.length; i++) {
                                        Relation relation = findRelationByName(roleDocument, doc, role, actionRight.relations[i], result);
                                        if (relation != null)
                                            relation.actionsMask.set(index, true);
                                    }
                                } else
                                    roleDocument.actionsMask.set(index, true);
                            }
                        }


                    if (roleDocument.relations == null) {
                        roleDocument.fullViewMask = roleDocument.viewMask;
                        roleDocument.fullUpdateMask = roleDocument.updateMask;
                        roleDocument.fullActionsMask = roleDocument.actionsMask;
                        // Rule: Updatable fields implicitly become viewable
                        roleDocument.viewMask.add(roleDocument.updateMask);
                    } else {
                        roleDocument.fullViewMask = roleDocument.viewMask.copy();
                        roleDocument.fullUpdateMask = roleDocument.updateMask.copy();
                        roleDocument.fullActionsMask = roleDocument.actionsMask.copy();
                        for (Relation relation : roleDocument.relations) {
                            roleDocument.fullViewMask.add(relation.viewMask);
                            roleDocument.fullUpdateMask.add(relation.updateMask);
                            roleDocument.fullActionsMask.add(relation.actionsMask);
                        }

                        // Rule: Updatable fields implicitly become viewable
                        roleDocument.viewMask.add(roleDocument.updateMask);
                        for (Relation relation : roleDocument.relations)
                            relation.viewMask.add(relation.updateMask);
                    }

                    // Rule: If there is any updatable field, user implicitly allowed to update
                    if (!roleDocument.fullUpdateMask.isEmpty() && !roleDocument.fullActionsMask.get(ImplicitActions.UPDATE.index)) {
                        roleDocument.actionsMask.set(ImplicitActions.UPDATE.index, true);
                        roleDocument.fullActionsMask.set(ImplicitActions.UPDATE.index, true);
                    }
                }
        }

        // Rule: Any document expected to be mentioned at least in one role
        for (DocType docType : documents.values()) {
            if (docType.udt)
                continue;
            boolean any = false;
            for (Role role : roles.values())
                if (!role.system && role.documents.get(docType.name.toUpperCase()) != null) {
                    any = true;
                    break;
                }
            if (!any)
                result.addMsg(YamlMessages.error_DocumentIsNotMentionedInAnyRole, docType.name);
        }
    }

    private Relation findRelationByName(RoleDocument roleDocument, DocType doc, Role role, String relationName, Result result) {
        DocumentRelation docRelation = doc.relations == null ? null : doc.relations.get(relationName.toUpperCase());
        if (docRelation == null) {
            result.addMsg(YamlMessages.error_RoleDocumentNotDeclaredRelation, role.name, doc.name, relationName);
            return null;
        }
        return roleDocument.relations[docRelation.index];
    }

    /**
     * 1. Builds permissions masks.
     */
    private void documentsStep4(Result result) {
        for (DocType doc : documents.values()) {

            fixFilterAndSortOrder(doc, result);

            doc.derivedFieldsMask = new BitArray(doc.allFields.size());
            doc.calculatedFieldsMask = new BitArray(doc.allFields.size());
            for (Field field : doc.allFields)
                if (field.derived) {
                    doc.derivedFieldsMask.set(field.index, true);
                    if (field.calculated)
                        doc.calculatedFieldsMask.set(field.index, true);
                }

            doc.implicitFieldsMask = new BitArray(doc.allFields.size());
            for (Item item : doc.fieldsGroups.get(BuiltInActionsGroups.IMPLICIT.getUpperCase()).fields)
                doc.implicitFieldsMask.set(doc.fieldByFullname.get(item.name.toUpperCase()).index, true);

            doc.implicitTopLevelFieldsMask = new BitArray(doc.allFields.size());
            for (Item item : doc.fieldsGroups.get(BuiltInActionsGroups.IMPLICIT_TOP_LEVEL.getUpperCase()).fields)
                doc.implicitTopLevelFieldsMask.set(doc.fieldByFullname.get(item.name.toUpperCase()).index, true);

            doc.notDerivedFieldsMask = doc.derivedFieldsMask.copy();
            doc.notDerivedFieldsMask.inverse();

            doc.serviceActionsMask = new BitArray(doc.actionsArray.length);

            if (!doc.udt) // _udt document is not applicable for CRUD
                for (int i = 0; i < doc.actionsArray.length; i++) {
                    Action action = doc.actionsArray[i];
                    if (action.service)
                        doc.serviceActionsMask.set(action.index, true);
                }

            // Start documents full permissions masks from here
            doc.fullViewMask = doc.implicitTopLevelFieldsMask.copy();
            doc.fullUpdateMask = new BitArray(doc.allFields.size());
            doc.fullActionsMask = doc.serviceActionsMask.copy();

            // Process states and their's transitions
            for (State state : doc.states.values()) {

                // State rights implicates document level rights
                state.viewMask = doc.implicitTopLevelFieldsMask.copy();
                state.updateMask = new BitArray(doc.allFields.size());
                state.actionsMask = new BitArray(doc.actionsArray.length);

                if (ImplicitStates.NEW.name().equals(state.name.toUpperCase())) {
                    state.actionsMask.set(ImplicitActions.CREATE.index, true);
                    state.actionsMask.set(ImplicitActions.RETRIEVE.index, true);
                } else {
                    state.actionsMask.set(ImplicitActions.RETRIEVE.index, true);
                    state.actionsMask.set(ImplicitActions.UPDATE.index, true);
                    state.actionsMask.set(ImplicitActions.DELETE.index, true);
                }

                if (state.view != null)
                    for (Item fieldOrFieldsGroup : state.view.values()) {
                        String key = fieldOrFieldsGroup.name.toUpperCase();
                        if (key.startsWith("_")) {
                            final FieldsGroup fieldsGroup = doc.fieldsGroups.get(key.substring(1));
                            if (fieldsGroup == null) {
                                result.addMsg(YamlMessages.error_DocumentStateContainsUndefinedFieldsGroupInView, doc.name, state.name, fieldOrFieldsGroup.name.substring(1));
                                continue;
                            }
                            state.viewMask.add(fieldsGroup.mask);
                        } else {
                            Field field = doc.fieldByFullname.get(key);
                            if (field == null) {
                                result.addMsg(YamlMessages.error_DocumentStateContainsUndefinedFieldInView, doc.name, state.name, fieldOrFieldsGroup.name);
                                continue;
                            }
                            for (int i = field.index; i < field.endIndex; i++)
                                state.viewMask.set(i, true);
                            for (FieldStructure s = field.structure; s != null; s = s.structure)
                                state.viewMask.set(s.index, true);
                        }
                    }
                if (state.update != null)
                    for (Item fieldOrFieldsGroup : state.update.values()) {
                        String key = fieldOrFieldsGroup.name.toUpperCase();
                        if (key.startsWith("_")) {
                            final FieldsGroup fieldsGroup = doc.fieldsGroups.get(key.substring(1));
                            if (fieldsGroup == null) {
                                result.addMsg(YamlMessages.error_DocumentStateContainsUndefinedFieldsGroupInUpdate, doc.name, state.name, fieldOrFieldsGroup.name.substring(1));
                                continue;
                            }
                            state.updateMask.add(fieldsGroup.mask);
                        } else {
                            Field field = doc.fieldByFullname.get(key);
                            if (field == null) {
                                result.addMsg(YamlMessages.error_DocumentStateContainsUndefinedFieldInUpdate, doc.name, state.name, fieldOrFieldsGroup.name);
                                continue;
                            }
                            for (int i = field.index; i < field.endIndex; i++)
                                state.updateMask.set(i, true);
                            for (FieldStructure s = field.structure; s != null; s = s.structure)
                                state.updateMask.set(s.index, true);
                        }
                    }

                if (state.transitions != null)
                    for (Transition transition : state.transitions.values())
                        state.actionsMask.set(doc.actions.get(transition.name.toUpperCase()).index, true);

                state.updateMask.subtract(doc.implicitFieldsMask);
                state.updateMask.subtract(doc.derivedFieldsMask);

                doc.fullViewMask.add(state.viewMask);
                doc.fullUpdateMask.add(state.updateMask);
                doc.fullActionsMask.add(state.actionsMask);

            }
            // Rule: Exclude derived field from any update
            doc.fullUpdateMask.subtract(doc.derivedFieldsMask);

            // Rule: only fields updateable in first state are actually required in DB scheme
            final State state = doc.statesArray[0];
            final BitArray.EnumTrueValues it = state.updateMask.getEnumTrueValues();
            int fi;
            while ((fi = it.next()) >= 0) {
                final Field field = doc.allFields.get(fi);
                field.dbRequired = field.required;
            }
            for (int i = 0; i < doc.allFields.size(); i++) {
                Field field = doc.allFields.get(i);
                if (field.type == Field.Type.STRUCTURE && (!field.dbRequired || !state.updateMask.get(field.index))) {
                    FieldStructure fieldStructure = (FieldStructure) field;
                    for (Field fld : fieldStructure.fields.values())
                        fld.dbRequired = false;
                }
            }
        }
    }

    /**
     * 1. Builds doc.actionsArray.
     * 2. Creates doc.states view/update/actions rights masks.
     * 3. Checks states transitions names - they must correspond to actions names.
     */
    private void documentsStep3(Result result) {
        int stateGlobalIndex = 0;
        for (DocType doc : documents.values()) {

            // create actions structures
            final ImplicitActions[] implicitActions = ImplicitActions.values();
            final Action updateAction = doc.actions.get(ImplicitActions.UPDATE.name());
            final Action deleteAction = doc.actions.get(ImplicitActions.DELETE.name());
            final Action recoverAction = doc.actions.get(ImplicitActions.RECOVER.name());
            doc.actionsArray = new Action[doc.actions.size() + implicitActions.length
                    + (updateAction != null ? -1 : 0)
                    + (deleteAction != null ? -1 : 0)
                    + (recoverAction != null ? -1 : 0)];

            // process actions from yaml
            int ai = implicitActions.length;
            for (Action action : doc.actions.values()) {
                if (EnumUtil.isEqual(BuiltInActionsGroups.ALL, action.name)) {
                    result.addMsg(YamlMessages.error_ActionHasReservedName, doc.name, action.name);
                    continue;
                }
                if (EnumUtil.isEqual(BuiltInActions.NEW_INSTANCE, action.name) && !action.service) {
                    result.addMsg(YamlMessages.error_ActionMustBeAService, doc.name, action.name);
                    continue;
                }
                action.document = doc;
                if (action != updateAction && action != deleteAction && action != recoverAction) {
                    action.index = ai;
                    doc.actionsArray[ai++] = action;
                }

                if (action.params != null) {
                    if (action == deleteAction || action == recoverAction) {
                        result.addMsg(YamlMessages.error_ActionParameterNotSupported, doc.name, action.name);
                        continue;
                    }
                    for (Field param : action.params.values()) {
                        param.fullname = param.name;
                        param.dbRequired = param.required;
                        param.template = "_" + doc.name + "__" + action.name + "_" + param.name;
                        if (param.type == null) {
                            final Field fldType = fieldTypes.get(param.udtType.toUpperCase());
                            if (fldType == null) {
                                result.addMsg(YamlMessages.error_ActionParameterHasUnknownType, doc.name, action.name, param.name, param.udtType);
                                continue;
                            }
                            fldType.mergeTo(param);
                        }
                    }
                    action.paramsClassName = NamesUtil.turnFirstLetterInUpperCase(action.name) + "Params";
                }

                if (action.outOfForm && action.other)
                    result.addMsg(YamlMessages.error_ActionOutOfFormAndOtherInTheSameTime, doc.name, action.name);
            }

            // add implicit actions
            for (ai = 0; ai < implicitActions.length; ai++) {
                ImplicitActions implAction = implicitActions[ai];
                // to make possible to define params on update.  params for update will be populated by preUpdate method
                final boolean redefinedUpdateAction = implAction == ImplicitActions.UPDATE && updateAction != null;
                final boolean redefinedDeleteAction = implAction == ImplicitActions.DELETE && deleteAction != null;
                final boolean redefinedRecoverAction = implAction == ImplicitActions.RECOVER && recoverAction != null;
                Action action = redefinedUpdateAction ? updateAction :
                        redefinedDeleteAction ? deleteAction :
                                redefinedRecoverAction ? recoverAction :
                                        new Action();
                if (action.accessedFields == null)
                    action.accessedFields = new HashSet<String>();
                action.implicitAction = implAction;
                action.name = implAction.name().toLowerCase();
                action.document = doc;
                action.index = ai;
                // TODO: Add for delete and update action override of attributes
                action.update = implAction.update;
                if (!action.accessedFields.contains("DISPLAY"))
                    action.display = implAction.display;
                if (!action.accessedFields.contains("OTHER"))
                    action.other = implAction.other;
                if (!redefinedUpdateAction && !redefinedDeleteAction && !redefinedRecoverAction) {
                    final Action prev = doc.actions.put(action.name.toUpperCase(), action);
                    if (prev != null)
                        result.addMsg(YamlMessages.error_ActionHasReservedName, doc.name, prev.name);
                }
                doc.actionsArray[ai] = action;
            }

            doc.statesArray = new State[doc.states.size()];
            int si = 0;
            for (State state : doc.states.values()) {

                doc.statesArray[state.index = si++] = state;
                state.document = doc;
                state.globalIndex = stateGlobalIndex++;

                if (state.transitions == null)
                    continue;

                for (boolean processNonConditionalTransitions = true; ; processNonConditionalTransitions = false) {

                    for (Transition transition : state.transitions.values()) {

                        if (processNonConditionalTransitions ^ (transition.preconditions == null))
                            continue;

                        final Action action = doc.actions.get(transition.name.toUpperCase());
                        if (action == null) {
                            result.addMsg(YamlMessages.error_DocumentStateTransitionNoSuchAction, doc.name, state.name, transition.name);
                            continue;
                        }
                        transition.actionModel = action;

                        if (action.implicitAction != null && action.implicitAction != ImplicitActions.CREATE) {
                            result.addMsg(YamlMessages.error_DocumentStateTransitionRefersDocumentWideAction, doc.name, state.name, transition.name, action.name);
                            continue;
                        }

                        // process endState
                        State endState = doc.states.get(transition.endState.toUpperCase());
                        if (endState == null) {
                            result.addMsg(YamlMessages.error_DocumentStateTransitionNoSuchEndState, doc.name, state.name, transition.name, transition.endState);
                            continue;
                        }
                        transition.endStateModel = endState;

                        if (processNonConditionalTransitions)
                            state.transitionByName.put(transition.name.toUpperCase(), transition);
                        else {
                            final Transition unconditionalTransition = state.transitionByName.get(transition.name.toUpperCase());
                            if (unconditionalTransition == null) {
                                result.addMsg(YamlMessages.error_DocumentStateConditionalTransitionHasNoCorrespondedUnconditionalTransition, doc.name, state.name, transition.keyInNormalCase);
                                continue;
                            }

                            if (unconditionalTransition.conditionalTransitions == null)
                                unconditionalTransition.conditionalTransitions = new ArrayList<Transition>();

                            unconditionalTransition.conditionalTransitions.add(transition);

                            for (int i = 0; i < transition.preconditions.length; i++) {
                                String preconditionName = transition.preconditions[i];
                                String key = preconditionName.toUpperCase();
                                if (doc.preconditions == null)
                                    doc.preconditions = new LinkedHashMap<String, Precondition>();
                                Precondition precondition = doc.preconditions.get(key);
                                if (precondition == null) {
                                    precondition = new Precondition();
                                    precondition.name = preconditionName;
                                    doc.preconditions.put(key, precondition);
                                }
                                precondition.transitions.add(transition);
                            }
                        }
                    }

                    if (!processNonConditionalTransitions || result.isError())
                        break;
                }
            }

            // index relations
            if (doc.relations != null) {
                int i = 0;
                for (DocumentRelation relation : doc.relations.values())
                    relation.index = i++;
            }
        }
        globalStatesCount = stateGlobalIndex;
    }

    private static void buildStructureLevelMask(FieldStructure s, DocType doc) {
        s.mask = new BitArray(doc.allFields.size());
        s.mask = new BitArray(doc.allFields.size());
        for (int i = s.index + 1; i < s.endIndex; i++)
            s.mask.set(i, true);

        for (Field field : s.fields.values()) {
            if (field.type != Field.Type.STRUCTURE)
                continue;
            FieldStructure fs = (FieldStructure) field;
            buildStructureLevelMask(fs, doc);
            if (s.levelMask == null)
                s.levelMask = s.mask.copy();
            s.levelMask.subtract(fs.levelMask);
        }

        if (s.levelMask == null)
            s.levelMask = s.mask;
    }

    /**
     * 1. Adds 'implicit' fields group.
     * 2. Builds fields groups rights masks.
     * 3. Builds level masks.
     */
    private void documentsStep2(Result result) {
        for (DocType doc : documents.values()) {

            if (doc.fieldsGroups == null)
                doc.fieldsGroups = new LinkedHashMap<String, FieldsGroup>();

            // Creates 'implicit' and 'implicitTopLevel' fields groups.  Take list of fields from doc.implicitFields list.
            final FieldsGroup implicit = new FieldsGroup();
            implicit.implicit = true;
            implicit.name = BuiltInActionsGroups.IMPLICIT.toString();
            implicit.fields = new Item[doc.implicitFields.size()];
            final FieldsGroup implicit_top_level = new FieldsGroup();
            implicit_top_level.implicit = true;
            implicit_top_level.name = BuiltInActionsGroups.IMPLICIT.toString();
            final ArrayList<Item> implicitTopLevel = new ArrayList<Item>();
            for (int i = 0; i < doc.implicitFields.size(); i++) {
                final Item item = new Item();
                final Field field = doc.implicitFields.get(i);
                item.name = field.fullname; // it's correct: fullname must be assigned to Item.name in this case
                implicit.fields[i] = item;
                final String upCaseName = field.fullname.toUpperCase();
                if (ImplicitFields.ID.getUpperCase().equals(upCaseName) ||
                        ImplicitFields.SUBJ.getUpperCase().equals(upCaseName) ||
                        ImplicitFields.TEXT.getUpperCase().equals(upCaseName) ||
                        ImplicitFields.REV.getUpperCase().equals(upCaseName) ||
                        ImplicitFields.STATE.getUpperCase().equals(upCaseName) ||
                        ImplicitFields.MODIFIED.getUpperCase().equals(upCaseName) ||
                        ImplicitFields.CREATED.getUpperCase().equals(upCaseName) ||
                        ImplicitFields.DELETED.getUpperCase().equals(upCaseName) ||
                        ImplicitFields.TEXTSTORAGE.getUpperCase().equals(upCaseName))
                    implicitTopLevel.add(item);
            }
            implicit_top_level.fields = implicitTopLevel.toArray(new Item[0]);

            if (doc.fieldsGroups.put(BuiltInActionsGroups.IMPLICIT.getUpperCase(), implicit) != null) {
                result.addMsg(YamlMessages.error_FieldsGroupHasReservedName, doc.name, implicit.name);
                continue;
            }

            if (doc.fieldsGroups.put(BuiltInActionsGroups.IMPLICIT_TOP_LEVEL.getUpperCase(), implicit_top_level) != null) {
                result.addMsg(YamlMessages.error_FieldsGroupHasReservedName, doc.name, implicit_top_level.name);
                continue;
            }

            // Creates 'all' fields group.
            final FieldsGroup all = new FieldsGroup();
            all.implicit = true;
            all.name = "all";
            all.fields = new Item[doc.fields.size()];
            int v = 0;
            for (Field fld : doc.fields.values()) {
                final Item item = new Item();
                item.name = fld.fullname; // it's correct: fullname must be assigned to Item.name in this case
                all.fields[v++] = item;
            }

            if (doc.fieldsGroups.put(all.name.toUpperCase(), all) != null) {
                result.addMsg(YamlMessages.error_FieldsGroupHasReservedName, doc.name, all.name);
                continue;
            }

            // Builds masks of fields groups.  Checks that all field of the document were covered by groups.
            final BitArray docMask = new BitArray(doc.fieldByFullname.size());
            for (FieldsGroup group : doc.fieldsGroups.values()) {
                group.mask = new BitArray(doc.fieldByFullname.size());
                for (int i = 0; i < group.fields.length; i++) {
                    String fieldName = group.fields[i].name; // name, just string within Item

                    if (fieldName.equals(FIELD_SELF))
                        continue;

                    final Field field = doc.fieldByFullname.get(fieldName.toUpperCase());
                    if (field == null) {
                        result.addMsg(YamlMessages.error_FieldFromGroupNotFound, doc.name, group.name, fieldName);
                        continue;
                    }

                    for (int k = field.index; k < field.endIndex; k++)
                        group.mask.set(k, true);

                    for (FieldStructure s = field.structure; s != null; s = s.structure)
                        group.mask.set(s.index, true);
                }
                docMask.add(group.mask);
            }
            docMask.inverse();
            final BitArray.EnumTrueValues tv = docMask.getEnumTrueValues();
            for (int fi = tv.next(); fi != -1; fi = tv.next())
                result.addMsg(YamlMessages.warning_FieldNotMentionedInGroups, doc.name, doc.allFields.get(fi).fullname);

            // build level mask
            doc.levelMask = new BitArray(doc.allFields.size());
            doc.levelMask.inverse();

            for (Field field : doc.fields.values()) {
                if (field.type != Field.Type.SUBTABLE && field.type != Field.Type.TAGS)
                    continue;
                FieldStructure fs = (FieldStructure) field;
                buildStructureLevelMask(fs, doc);
                doc.levelMask.subtract(fs.mask);
            }
        }
    }

    private static void fixFilterAndSortOrder(DocType docType, Result result) {

        // filters
        if (docType.filters == null) {
            docType.filters = new LinkedHashMap<String, DocumentFilter>();
            if (docType.states.size() > 2) { // then auto filters by states
                DocumentFilter all = new DocumentFilter();
                all.name = "all";
                docType.filters.put(all.name.toUpperCase(), all);
                for (int i = 0; i < docType.statesArray.length; i++) {
                    State state = docType.statesArray[i];
                    DocumentFilter filterByState = new DocumentFilter();
                    filterByState.name = "state" + NamesUtil.turnFirstLetterInUpperCase(state.name);
                    filterByState.where = "doc.state='" + NamesUtil.wordsToUpperUnderscoreSeparated(state.name) + "' and doc.deleted=false";
                    docType.filters.put(filterByState.name.toUpperCase(), filterByState);
                }
            }
        } else
            for (DocumentFilter filter : docType.filters.values()) {
                if (filter.where != null && !docType.report)
                    filter.where = "(" + filter.where + ") and doc.deleted=false";
                else if (filter.where == null && filter.name.startsWith("state") && !filter.name.equals("state")) {
                    String[] ands = filter.name.substring("state".length()).split("Or");
                    boolean isNot = false;
                    if (ands[0].startsWith("Not")) {
                        isNot = true;
                        ands[0] = ands[0].substring("Not".length());
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < ands.length; i++) {
                        String stateName = ands[i];
                        State state = docType.states.get(stateName.toUpperCase());
                        if (state == null) {
                            result.addMsg(YamlMessages.error_SortOrderFieldNotFound, docType.name, filter.name,
                                    NamesUtil.turnFirstLetterInLowerCase(stateName));
                            continue;
                        }
                        if (sb.length() > 0)
                            sb.append(isNot ? " and " : " or ");
                        else if (!isNot)
                            sb.append("(");
                        sb.append(isNot ? "doc.state<>'" : "doc.state='").append(NamesUtil.wordsToUpperUnderscoreSeparated(state.name)).append("'");
                    }
                    if (!isNot)
                        sb.append(")");
                    sb.append(" and doc.deleted=false");
                    filter.where = sb.toString();
                }
            }

        DocumentFilter all = docType.filters.get("ALL");
        DocumentFilter deleted = docType.filters.get("DELETED");

        if (all == null) {
            all = new DocumentFilter();
            all.name = "all";

            docType.filters.put(all.name.toUpperCase(), all);
        }
        if (all.where == null)
            all.where = docType.report ? "" : "doc.deleted=false";

        if (!docType.report) {
            if (deleted == null) {
                deleted = new DocumentFilter();
                deleted.name = "deleted";
                docType.filters.put(deleted.name.toUpperCase(), deleted);
            }
            if (deleted.where == null)
                deleted.where = "doc.deleted=true";
        }

        // sortOrders
        if (docType.sortOrders != null)
            for (DocumentSortOrder sortOrder : docType.sortOrders.values()) {
                if (sortOrder.sortOrder != null || !(sortOrder.name.startsWith("by") && !sortOrder.equals("by")))
                    continue;
                String[] ands = sortOrder.name.substring("by".length()).split("And");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < ands.length; i++) {
                    String field = ands[i];
                    if (field.endsWith("Desc"))
                        field = field.substring(0, field.length() - "Desc".length());
                    Field fld = docType.fieldByFullname.get(field.toUpperCase().replace("_", "."));
                    if (fld == null) {
                        result.addMsg(YamlMessages.error_SortOrderFieldNotFound, docType.name, sortOrder.name,
                                NamesUtil.turnFirstLetterInLowerCase(field));
                        continue;
                    }
                    if (sb.length() > 0)
                        sb.append(", ");
                    sb.append("doc.").append(fld.fullname.replace("_", "."));
                    if (field.endsWith("Desc"))
                        sb.append(" desc");
                }
                sortOrder.sortOrder = sb.toString();
            }
        else {
            docType.sortOrders = new LinkedHashMap<String, DocumentSortOrder>();
            generateDefaultSortOrdersForLevel(docType.fields, docType);
            if (docType.sortOrders.size() == 0) {
                DocumentSortOrder so = new DocumentSortOrder();
                so.name = "_none";
                docType.sortOrders.put(so.name.toUpperCase(), so);
            }
        }
    }

    private static void generateDefaultSortOrdersForLevel(LinkedHashMap<String, Field> fields, DocType docType) {
        for (Field field : fields.values()) {
            if (field.implicitFieldType != null && !(
                    field.implicitFieldType == ImplicitFields.CREATED ||
                            field.implicitFieldType == ImplicitFields.MODIFIED))
                continue;
            if (field.type == Field.Type.ENUM ||
                    field.type == Field.Type.TAGS ||
                    field.type == Field.Type.SUBTABLE ||
                    field.type == Field.Type.REFERS ||
                    field.type == Field.Type.POLYMORPHIC_REFERS)
                continue;
            if (field.type == Field.Type.STRUCTURE) {
                FieldStructure fieldStructure = (FieldStructure) field;
                if (fieldStructure.single)
                    generateDefaultSortOrdersForLevel(fieldStructure.fields, docType);
                continue;
            }
            DocumentSortOrder so = new DocumentSortOrder();
            so.name = "by" + NamesUtil.turnFirstLetterInUpperCase(field.fullname).replace(".", "_");
            so.sortOrder = "doc." + field.fullname;
            docType.sortOrders.put(so.name.toUpperCase(), so);
        }
    }

    /**
     * 1. Checks that fields do not have reserved names.
     * 2. Create Document.entities.
     * 3. Adds implicit fields (like id, rev, create etc.).
     * 4. Sets Field.index and Field.endIndex.
     * 5. Sets Field.fullname.
     * 6. Builds Document.allFields.
     * 7. Builds Document.implicitFields.
     * 8. Processes 'textstorage' attribute on fields, and conditionally adds 'textStorage' field.
     * 9. Collects fields to Entities
     * 10. Links Fields of type Structure to it's Entity
     * 11. Generates Entity.javaClassName and Entity.tableName
     */

    private void documentsStep1(Result result) {

        documentsArray = new DocType[documents.size()];
        int di = 0;
        for (DocType doc : documents.values()) {
            doc.index = di;
            documentsArray[di++] = doc;
        }
        Arrays.sort(documentsArray, DOCUMENT_ACCENDING_SORT_BY_NAME);

        for (int i = 0; i < documentsArray.length; i++) {

            DocType doc = documentsArray[i];

            // empty fields list, if none
            if (doc.fields == null)
                doc.fields = new LinkedHashMap<String, Field>();

            // check that ref 'subj' field presents for linkedDocument docs, and absents for others
            final Field subjField = doc.fields.get(ImplicitFields.SUBJ.name());
            if (doc.linkedDocument) {
                if (subjField != null) {
                    if (!(subjField instanceof FieldReference) && !(subjField instanceof FieldPolymorphicReference))
                        result.addMsg(YamlMessages.error_LinkedDocumentSubjOfWrongType, doc.name, subjField.name);
                    else
                        subjField.implicitFieldType = ImplicitFields.SUBJ;
                } else
                    result.addMsg(YamlMessages.error_LinkedDocumentMissingSubj, doc.name);
            } else if (subjField != null)
                result.addMsg(YamlMessages.error_SubjInNotLinkedDocument, doc.name, subjField.name);

            if (doc.states != null && doc.states.size() == 1) { // it's ok to have only new state with update specified
                State newState = doc.states.get("NEW");
                if (newState == null)
                    result.addMsg(YamlMessages.error_StatesInSimpleDocument, doc.name);
                else if (newState.view != null || newState.transitions != null)
                    result.addMsg(YamlMessages.error_StatesInSimpleDocument, doc.name);
                else
                    addOneStateDocumentStates(doc, result);
            } else if (doc.states == null) // it's ok not to have 'states:' at all
                addOneStateDocumentStates(doc, result);
            else
                addNewStateIfMissing(doc, result);

            // empty actions list, if none
            if (doc.actions == null)
                doc.actions = new LinkedHashMap<String, Action>();

            // detailed fields validation. builds Entities. adds implicit fields.
            final Entity entity = new Entity();
            entity.name = doc.name;
            entity.tableName = "doc_" + NamesUtil.wordsToUnderscoreSeparated(doc.name);
            entity.type = doc.report ? EntityType.REPORT : (doc.simple ? EntityType.SIMPLE_DOCUMENT : EntityType.ONE_STATE_DOCUMENT);
            entity.document = doc;
            doc.entities.add(entity);
            indexFieldsAtLevel(doc, entity, null, "", doc.fields, 0, result);

            doc.historyTableName = "doc_" + NamesUtil.wordsToUnderscoreSeparated(doc.name + "History");
            doc.historyEntityName = doc.name + "History";

            documentByTable.put(entity.tableName.toUpperCase(), doc);
        }
    }

    private void addOneStateDocumentStates(DocType doc, Result result) {

        State newState;
        boolean isNewStateSpecified = (doc.states != null);
        if (!isNewStateSpecified) {
            doc.states = new LinkedHashMap<String, State>();
            newState = new State();
            newState.name = ImplicitStates.NEW.toString();
            doc.states.put(newState.name.toUpperCase(), newState);
        } else
            newState = doc.states.get("NEW");

        final State persistedState = new State();
        persistedState.name = ImplicitStates.PERSISTED.toString();
        doc.states.put(persistedState.name.toUpperCase(), persistedState);

        final LinkedHashMap<String, Item> allFieldsAccess = new LinkedHashMap<String, Item>();
        final Item allFields = new Item("_all");
        allFieldsAccess.put(allFields.name.toUpperCase(), allFields);

        // - new | persisted:
        //     view: [all]
        newState.view = isNewStateSpecified ? newState.update : allFieldsAccess;
        persistedState.view = allFieldsAccess;

        if (!doc.simple) {
            // - new | persisted:
            //     update: [all]
            if (!isNewStateSpecified)
                newState.update = allFieldsAccess;
            persistedState.update = allFieldsAccess;
        }

        // - new:
        //    transitions:
        //      - create -> persisted
        newState.transitions = new LinkedHashMap<String, Transition>();
        final Transition createTransition = new Transition();
        createTransition.name = ImplicitActions.CREATE.toString();
        createTransition.endState = persistedState.name;
        newState.transitions.put(createTransition.name.toUpperCase(), createTransition);

        // - persisted:
        //     transitions:
        //       - <not service action> -> persisted
        if (doc.actions != null) {
            persistedState.transitions = new LinkedHashMap<String, Transition>();
            for (Action action : doc.actions.values())
                // Note: This happends priod building doc.actionsArray.  So Update is not marked as implicit action yet
                if (!action.service && !ImplicitActions.UPDATE.name().equalsIgnoreCase(action.name)) {
                    final Transition transition = new Transition();
                    transition.name = action.name;
                    transition.endState = persistedState.name;
                    persistedState.transitions.put(transition.name.toUpperCase(), transition);
                }
        }
    }

    private void addNewStateIfMissing(DocType doc, Result result) {
        int p = 0;
        State firstState = null;
        State newState = null;
        boolean newStateFound = false;
        if (doc.states == null)
            doc.states = new LinkedHashMap<String, State>();
        else {
            for (State state : doc.states.values()) {
                if (ImplicitStates.NEW.name().equals(state.name.toUpperCase())) {
                    newState = state;
                    if (p > 0)
                        result.addMsg(YamlMessages.error_DocumentNewStateMustComeFirst, doc.name, state.name, ImplicitStates.NEW.toString());
                    if (firstState != null)
                        break;
                } else if (firstState == null) {
                    firstState = state;
                    if (newState != null)
                        break;
                }
                p++;
            }
        }

        if (newState == null) {
            // states:
            //   - new:
            //       view: []
            //       update: []
            final LinkedHashMap<String, State> newStatesList = new LinkedHashMap<String, State>();
            newState = new State();
            newState.name = ImplicitStates.NEW.toString();
            newStatesList.put(newState.name.toUpperCase(), newState);
            newStatesList.putAll(doc.states);
            doc.states = newStatesList;
        }

        if (firstState == null) {
            final State persistedState = firstState = new State();
            persistedState.name = ImplicitStates.PERSISTED.toString();
            doc.states.put(persistedState.name.toUpperCase(), persistedState);
        }

        if (newState.transitions == null || newState.transitions.size() == 0) {
            // add default transition, if none
            //
            // states:
            //   - new:
            //       ...
            //       transitions:
            //         create -> <first persisted state>
            final Transition createTransition = new Transition();
            createTransition.name = ImplicitActions.CREATE.toString();
            createTransition.endState = firstState.name;
            newState.transitions = new LinkedHashMap<String, Transition>();
            newState.transitions.put(createTransition.name.toUpperCase(), createTransition);
        } else
            for (Transition transition : newState.transitions.values()) {
                if (!ImplicitActions.CREATE.name.equals(transition.name)) {
                    result.addMsg(YamlMessages.error_DocumentNewStateCanOnlyHasCreateTransitions, doc.name,
                            ImplicitStates.NEW.toString(), ImplicitActions.CREATE.toString());
                    break;
                }
            }
    }

    private int indexFieldsAtLevel(DocType doc, Entity entity, FieldStructure structure, String namePrefix, LinkedHashMap<String, Field> fields, int index, Result result) {
        Field fkField = null;
        switch (entity.type) {
            case SIMPLE_DOCUMENT:
            case ONE_STATE_DOCUMENT:
            case DOCUMENT:
                index = processField(doc, entity, structure, namePrefix, buildImpliciteField(doc, structure, ImplicitFields.ID, doc.implicitFields), index, result);
                index = processField(doc, entity, structure, namePrefix, buildImpliciteField(doc, structure, ImplicitFields.REV, doc.implicitFields), index, result);
                index = processField(doc, entity, structure, namePrefix, buildImpliciteField(doc, structure, ImplicitFields.TEXT, doc.implicitFields), index, result);
                if (doc.states.size() > 2) { // 2 := 'new' and first persisted state
                    index = processField(doc, entity, structure, namePrefix, buildImpliciteField(doc, structure, ImplicitFields.STATE, doc.implicitFields), index, result);
                    entity.type = EntityType.DOCUMENT;
                }
                break;
            case STRUCTURE:
                index = processField(doc, entity, structure, namePrefix, buildImpliciteField(doc, structure, ImplicitFields.ID, doc.implicitFields), index, result);
                index = processField(doc, entity, structure, namePrefix, buildImpliciteField(doc, structure, ImplicitFields.I, doc.implicitFields), index, result);
                fkField = buildImpliciteField(doc, structure, ImplicitFields.FK, doc.implicitFields);
                entity.fkField = fkField;
                index = processField(doc, entity, structure, namePrefix, fkField, index, result);
                break;
        }

        for (Field fld : fields.values()) {
            if (structure != null) {
                fld.derived |= structure.derived;
                fld.calculated |= structure.calculated;
            }
            try {
                boolean isReservedName = false;
                final String upperCasedName = fld.name.toUpperCase();
                if (fkField != null && fkField.name.toUpperCase().equals(upperCasedName))
                    isReservedName = true;
                if (!isReservedName) {
                    ImplicitFields fldName = ImplicitFields.valueOf(upperCasedName);
                    switch (entity.type) {
                        case DOCUMENT:
                            switch (fldName) {
                                case STATE:
                                    isReservedName = true;
                            }
                            // fallthru
                        case ONE_STATE_DOCUMENT:
                            switch (fldName) {
                                case ID:
                                case TEXTSTORAGE:
                                case REV:
                                case MODIFIED:
                                case CREATED:
                                case DELETED:
                                    isReservedName = true;
                            }
                            break;
                        case STRUCTURE:
                            switch (fldName) {
                                case ID:
                                case I:
                                case FK:
                                case TEXTSTORAGE:
                                    isReservedName = true;
                            }
                            break;
                    }
                }
                if (isReservedName) {
                    result.addMsg(YamlMessages.error_FieldHasReservedName, doc.name, namePrefix + fld.name);
                    continue;
                }
            } catch (IllegalArgumentException e) {
                // it's ok.  name just not in the list
            }

            if (fld.textstorage)
                entity.hasTextStorage = true;

            fld.template = (structure != null ? structure.template : ("_" + doc.name)) + "_" + fld.name;

            if (!doc.udt) { // _udt document fields was already processed in previouse steps.

                // apply UDType, if that's the case
                if (fld.type == null) {
                    final Field fldType = fieldTypes.get(fld.udtType.toUpperCase());
                    if (fldType == null) {
                        result.addMsg(YamlMessages.error_FieldHasUnknownType, doc.name, namePrefix + fld.name, fld.udtType);
                        continue;
                    }
                    if (!(fldType instanceof FieldSimple)) {
                        result.addMsg(YamlMessages.error_FieldHasNotSimpleType, doc.name, namePrefix + fld.name, fld.udtType);
                        continue;
                    }
                    fldType.mergeTo(fld);
                }

                // apply named enum type
                if (fld.type == Field.Type.ENUM)
                    if (fld.udtType != null) {
                        Field fldType = fieldTypes.get(fld.udtType.toUpperCase());
                        if (fldType == null) {
                            result.addMsg(YamlMessages.error_FieldHasUnknownType, doc.name, namePrefix + fld.name, fld.udtType);
                            continue;
                        }
                        if (!(fldType instanceof FieldEnum)) {
                            result.addMsg(YamlMessages.error_FieldNotAnEnumType, doc.name, namePrefix + fld.name, fld.udtType);
                            continue;
                        }
                        fldType.mergeTo(fld);
                    } else {
                        FieldEnum fieldEnum = (FieldEnum) fld;
                        fieldEnum.enumTypeName = doc.getClassName() + "$" + NamesUtil.turnFirstLetterInUpperCase(fld.name);
                    }

                // apply named structure type
                if (fld.type == Field.Type.STRUCTURE)
                    if (fld.udtType != null) {
                        Field fldType = fieldTypes.get(fld.udtType.toUpperCase());
                        if (fldType == null) {
                            result.addMsg(YamlMessages.error_FieldHasUnknownType, doc.name, namePrefix + fld.name, fld.udtType);
                            continue;
                        }
                        if (!(fldType instanceof FieldStructure) || fldType.type != Field.Type.STRUCTURE) {
                            result.addMsg(YamlMessages.error_FieldNotAStructureType, doc.name, namePrefix + fld.name, fld.udtType);
                            continue;
                        }
                        fldType.deepCopy().mergeTo(fld);
                    }

                // apply named subtable type
                if (fld.type == Field.Type.SUBTABLE)
                    if (fld.udtType != null) {
                        Field fldType = fieldTypes.get(fld.udtType.toUpperCase());
                        if (fldType == null) {
                            result.addMsg(YamlMessages.error_FieldHasUnknownType, doc.name, namePrefix + fld.name, fld.udtType);
                            continue;
                        }
                        if (!(fldType instanceof FieldStructure) || fldType.type != Field.Type.SUBTABLE) {
                            result.addMsg(YamlMessages.error_FieldNotASubtableType, doc.name, namePrefix + fld.name, fld.udtType);
                            continue;
                        }
                        fldType.deepCopy().mergeTo(fld);
                    }
            }

            index = processField(doc, entity, structure, namePrefix, fld, index, result);

            validateFieldType(doc, fld, result);

            if (fld.type == Field.Type.REFERS) {
                final String docName = ((FieldReference) fld).refDocument;
                final DocType document = DocflowConfig.instance.documents.get(docName.toUpperCase());
                if (document == null)
                    result.addMsg(YamlMessages.error_FieldRefersUndefinedDocument, doc.name, namePrefix + fld.name, docName);
            } else if (fld.type == Field.Type.POLYMORPHIC_REFERS) {
                final FieldPolymorphicReference fpr = (FieldPolymorphicReference) fld;
                if (fpr.refDocuments != null) {
                    fpr.refDocumentsNames = new TreeSet<String>();
                    for (int i = 0; i < fpr.refDocuments.length; i++) {
                        String docName = fpr.refDocuments[i];
                        fpr.refDocumentsNames.add(docName.toUpperCase());
                        final DocType document = DocflowConfig.instance.documents.get(docName.toUpperCase());
                        if (document == null)
                            result.addMsg(YamlMessages.error_FieldRefersUndefinedDocument, doc.name, namePrefix + fld.name, docName);
                    }
                }
            }
        }

        if (entity.type == EntityType.ONE_STATE_DOCUMENT || entity.type == EntityType.DOCUMENT) {
            index = processField(doc, entity, structure, namePrefix, buildImpliciteField(doc, structure, ImplicitFields.CREATED, doc.implicitFields), index, result);
            index = processField(doc, entity, structure, namePrefix, buildImpliciteField(doc, structure, ImplicitFields.MODIFIED, doc.implicitFields), index, result);
            index = processField(doc, entity, structure, namePrefix, buildImpliciteField(doc, structure, ImplicitFields.DELETED, doc.implicitFields), index, result);
            if (entity.hasTextStorage)
                index = processField(doc, entity, structure, namePrefix, buildImpliciteField(doc, structure, ImplicitFields.TEXTSTORAGE, doc.implicitFields), index, result);
        }

        return index;
    }

    private int processField(DocType doc, Entity entity, FieldStructure structure, String namePrefix, Field field, int index, Result result) {
        Field fld = field;
        fld.document = doc;
        fld.fullname = namePrefix + fld.name;
        fld.structure = structure;
        fld.index = index++;
        fld.endIndex = index;
        entity.fields.add(field);
        doc.fieldByFullname.put(fld.fullname.toUpperCase(), fld);
        doc.allFields.add(fld);
        if (fld instanceof FieldStructure) {
            final FieldStructure fldStructure = (FieldStructure) fld;
            entity = new Entity();
            entity.parent = structure != null ?
                    structure.entity :
                    doc.entities.get(0);
            entity.outerStructure = entity.parent.type == EntityType.EMBEDDED_STRUCTURE ?
                    entity.parent.outerStructure :
                    entity.parent;
            entity.name = entity.parent.name + "_" + NamesUtil.turnFirstLetterInUpperCase(fld.name);
            entity.structureField = fld;
            entity.tableName = "doc_" + NamesUtil.wordsToUnderscoreSeparated(entity.name);
            entity.type = doc.report ? EntityType.REPORT_STRUCTURE :
                    (fldStructure.single ? EntityType.EMBEDDED_STRUCTURE : EntityType.STRUCTURE);
            entity.document = doc;
            doc.entities.add(entity);
            fldStructure.entity = entity;
            fld.endIndex = index = indexFieldsAtLevel(doc, entity, fldStructure, fld.fullname + ".", fldStructure.fields, index, result);
        }
        return index;
    }

    private static void validateFieldType(DocType doc, Field fld, Result result) {
        if (fld.calculated) // then it's implicitly 'derived'
            fld.derived = true;
        for (int j = 0; j < fld.type.required.length; j++) {
            String attr = fld.type.required[j];
            if (!fld.accessedFields.contains(attr.toUpperCase()))
                result.addMsg(YamlMessages.error_FieldMustHaveGivenAttributeSpecified, doc.name, fld.fullname, fld.type.toString(), attr);
        }
        if (fld instanceof FieldSimple) {
            FieldSimple fieldSimple = (FieldSimple) fld;
            for (int j = 0; j < FieldSimple.typeAttrs.length; j++) {
                String attr = FieldSimple.typeAttrs[j];
                if (fld.accessedFields.contains(attr.toUpperCase())) {
                    boolean found = false;
                    for (int k = 0; k < fld.type.required.length; k++)
                        if (attr.equals(fld.type.required[k])) {
                            found = true;
                            break;
                        }
                    if (!found)
                        for (int k = 0; k < fld.type.optional.length; k++)
                            if (attr.equals(fld.type.optional[k])) {
                                found = true;
                                break;
                            }
                    if (!found) {
                        result.addMsg(YamlMessages.error_FieldMustNotHaveGivenAttributeSpecified, doc.name, fld.fullname, fld.type.toString(), attr);
                    }
                }
            }
            boolean isMaxLengthAssignedFromLength = false;
            if (fld.accessedFields.contains("LENGTH"))
                if (fld.accessedFields.contains("MAXLENGTH")) {
                    if (fieldSimple.maxLength > fieldSimple.length)
                        result.addMsg(YamlMessages.error_FieldMustHasMaxLengthAttrBiggerThenLength, doc.name, fld.fullname);
                } else {
                    // by default maxLength same as length
                    isMaxLengthAssignedFromLength = true;
                    fieldSimple.maxLength = fieldSimple.length;
                    fld.accessedFields.add("MAXLENGTH");
                }
            if (fld.accessedFields.contains("MINLENGTH") && (isMaxLengthAssignedFromLength || fld.accessedFields.contains("MAXLENGTH")))
                if (fieldSimple.minLength > fieldSimple.maxLength)
                    result.addMsg(isMaxLengthAssignedFromLength ?
                            YamlMessages.error_FieldMustHasMinLengthAttrBiggerThenLength :
                            YamlMessages.error_FieldMustHasMinLengthAttrBiggerThenMaxLength, doc.name, fld.fullname);
            if (fld.accessedFields.contains("MIN") && fld.accessedFields.contains("MAX"))
                if (fieldSimple.min > fieldSimple.max)
                    result.addMsg(YamlMessages.error_FieldMustHasMinAttrBiggerThenMax, doc.name, fld.fullname);
        }
    }


    /**
     * List of implicit actions, hardcoded in this solution.
     */
    public enum ImplicitActions {
        CREATE(0, "create", true, true, false),
        RETRIEVE(1, "retrieve", false, false, false),
        UPDATE(2, "update", true, true, false),
        DELETE(3, "delete", true, false, true),
        RECOVER(4, "recover", true, false, false);

        private final String name;
        public final int index;
        public final boolean display;
        public final boolean update;
        public final boolean other;

        private ImplicitActions(int index, String name, boolean display, boolean update, boolean other) {
            this.index = index;
            this.name = name;
            this.update = update;
            this.display = display;
            this.other = other;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * List of implicit states, hardcoded in this solution.
     */
    public enum ImplicitStates {
        NEW(0, "new"),
        PERSISTED(1, "persisted");

        public final int index;
        private final String name;

        private ImplicitStates(int index, String name) {
            this.index = index;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * List of implicit fields, hardcoded in this solution.
     */
    public enum ImplicitFields implements EnumUtil.ComparableEnum {
        ID("id"),
        REV("rev"),
        I("i"),
        STATE("state"),
        SUBJ("subj"),
        CREATED("created"),
        MODIFIED("modified"),
        DELETED("deleted"),
        TEXTSTORAGE("textStorage"),
        TEXT("text"),
        FK("fk");

        private final String name;
        private final String upperCase;

        private ImplicitFields(String name) {
            this.name = name;
            this.upperCase = name.toUpperCase();
        }

        @Override
        public String getUpperCase() {
            return upperCase;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Creates document instance of Field for given implicit field, based on hardcoded rules.  Adds new field to implicitFields list.
     *
     * @return New instance of the field.
     */
    private static Field buildImpliciteField(DocType doc, FieldStructure structure, ImplicitFields implicitFieldType, ArrayList<Field> implicitFields) {
        Field field = null;
        switch (implicitFieldType) {
            case ID:
                field = new FieldSimple();
                field.type = Field.Type.LONG;
                break;

            case REV:
                field = new FieldSimple();
                field.type = Field.Type.INT;
                break;

            case I:
                field = new FieldSimple();
                field.type = Field.Type.INT;
                break;

            case FK:
                field = new FieldSimple();
                field.type = Field.Type.REFERS;
                break;

            case CREATED:
            case MODIFIED:
                field = new FieldSimple();
                field.type = Field.Type.TIMESTAMP;
                break;

            case DELETED:
                field = new FieldSimple();
                field.type = Field.Type.BOOL;
                break;

            case STATE:
                field = new FieldSimple();
                field.type = Field.Type.STRING;
                ((FieldSimple) field).length = ((FieldSimple) field).maxLength = 100;
                break;

            case TEXTSTORAGE:
                field = new FieldSimple();
                field.type = Field.Type.TEXT;
                field.required = false;
                field.dbRequired = false;
                break;

            case TEXT:
                FieldCalculated fieldCalculated = new FieldCalculated();
                fieldCalculated.javaType = "String";
                field = fieldCalculated;
                field.type = Field.Type.CALCULATED;
                field.calculated = true;
                field.derived = true;
                break;
        }

        field.name = implicitFieldType.toString();
        field.implicitFieldType = implicitFieldType;
        field.required = true;
        field.dbRequired = true;

        field.hidden = true;
        field.template = (structure != null ? structure.template : ("_" + doc.name)) + "_" + field.name;

        implicitFields.add(field);
        return field;
    }

    private void loadRoles(Result result) {
        List<String> loadedFilesInLowerCase = new ArrayList<String>();
        for (VirtualFile vf : appPath != null ? appPath : Play.javaPath) {
            final VirtualFile dir = vf.child(PATH_ROLES);
            if (dir != null && dir.exists()) {
                final List<VirtualFile> list = dir.list();
                for (int i = 0; i < list.size(); i++) {
                    VirtualFile file = list.get(i);
                    if (file.getRealFile().isHidden() || skipDuplicatesFromDifferentPaths(loadedFilesInLowerCase, file, result))
                        continue;
                    Role role = loadOneYamlFile(file, new Role[0], result);
                    if (result.isError())
                        return;
                    checkState(role != null);
                    role.sourcePath = vf;
                    roles.put(role.name.toUpperCase(), role);
                }
            }
        }
        if (loadedFilesInLowerCase.size() == 0)
            result.addMsg(YamlMessages.error_NoRolesDefinitions);
    }

    private void loadDocuments(Result result) {
        boolean error = false;
        List<String> loadedFilesInLowerCase = new ArrayList<String>();
        for (VirtualFile vf : appPath != null ? appPath : Play.javaPath) {
            final VirtualFile dir = vf.child(PATH_DOCUMENTS);
            if (dir != null && dir.exists()) {
                final List<VirtualFile> list = dir.list();
                for (int i = 0; i < list.size(); i++) {
                    VirtualFile file = list.get(i);
                    if (file.getRealFile().isHidden() || skipDuplicatesFromDifferentPaths(loadedFilesInLowerCase, file, result))
                        continue;
                    DocType doc = loadOneYamlFile(file, new DocType[0], result);
                    if (doc == null) // it's error, but keep checking other files
                        continue;
                    doc.sourcePath = vf;
                    documents.put(doc.name.toUpperCase(), doc);
                }
            }
        }
    }

    private boolean skipDuplicatesFromDifferentPaths(List<String> loadedDocInLowerCase, VirtualFile file, Result result) {
        String fileName = file.getName();
        final int ext = fileName.lastIndexOf('.');
        if (ext > 0)
            fileName = fileName.substring(0, ext);
        fileName = fileName.toLowerCase();
        if (loadedDocInLowerCase.contains(fileName)) {
            result.addMsg(YamlMessages.error_FileSkipped, file.getRealFile().getAbsolutePath());
            return true;
        }
        loadedDocInLowerCase.add(fileName);
        return false;
    }

    @SuppressWarnings("unchecked")
    private void loadMessages(Result result) {
        final Result localResult = new Result();

        ItemBuilder itemBuilder = null;
        try {
            itemBuilder = ItemBuilder.factory.get(this.getClass().getField("messages"));
        } catch (NoSuchFieldException e) { // from getClass()
            throw new JavaExecutionException(e);
        }

        final List<VirtualFile> paths = appPath != null ? appPath : Play.javaPath;

        // reverse path iteration
        for (int i = paths.size() - 1; i >= 0; i--) {
            VirtualFile path = paths.get(i);
            final VirtualFile file = path.child(PATH_DOCFLOW);
            if (!file.exists())
                continue;

            final File[] msgFileList = file.getRealFile().listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(MESSAGES_FILE_SUFFIX);
                }
            });

            for (int j = 0; j < msgFileList.length; j++) {
                File msgFile = msgFileList[j];

                String fullFilename = msgFile.getAbsolutePath();
                final String name = msgFile.getName();
                final String fileKey = name.substring(0, name.length() - MESSAGES_FILE_SUFFIX.length()).toUpperCase() + ".";

                InputStreamReader inputStreamReader = null;
                try {
                    inputStreamReader = new InputStreamReader(new FileInputStream(msgFile));
                } catch (FileNotFoundException e) {
                    throw new UnexpectedException(e);
                }
                final YamlParser yamlParser = new YamlParser(new Yaml().parse(inputStreamReader), msgFile.getAbsolutePath());
                try {
                    final DocumentBuilder documentBuilder = new DocumentBuilder(itemBuilder);
                    final LinkedHashMap<String, Message> map = (LinkedHashMap<String, Message>) documentBuilder.build(yamlParser, localResult);
                    if (map == null)
                        continue;
                    if (messages == null) {
                        messages = new LinkedHashMap<String, Message>();
                        messagesByFiles = new LinkedHashMap<String, LinkedHashMap<String, Message>>();
                    }

                    for (Map.Entry<String, Message> entry : map.entrySet())
                        messages.put(fileKey + entry.getKey(), entry.getValue());

                    messagesByFiles.put(fullFilename, map);

                } catch (ScannerException e) {
                    localResult.addMsg(YamlMessages.error_InvalidYamlFormat, yamlParser.filename,
                            e.getProblem(), e.getProblemMark().getLine(), e.getProblemMark().getColumn());
                } finally {
                    try {
                        inputStreamReader.close();
                    } catch (IOException e) {
                        Logger.error(e, "Failed to close file.");
                    }
                }

                if (localResult.isNotOk())
                    result.append(localResult, fullFilename);
                else
                    result.addMsg(YamlMessages.debug_FileLoadedSuccessfully, fullFilename);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFieldTypes(Result result) {
        final Result localResult = new Result();

        final List<VirtualFile> paths = appPath != null ? appPath : Play.javaPath;

        // reverse path iteration
        for (int i = paths.size() - 1; i >= 0; i--) {
            VirtualFile path = paths.get(i);
            final VirtualFile file = path.child(PATH_FIELD_TYPES);
            if (!file.exists())
                continue;

            String filename = null;
            try {
                filename = file.getRealFile().getCanonicalPath();
            } catch (IOException e) {
                Logger.error(e, "Unexpected");
            }

            try {
                final ItemBuilder itemBuilder = ItemBuilder.factory.get(this.getClass().getField("fieldTypes"));
                final InputStreamReader inputStreamReader = new InputStreamReader(file.inputstream());
                final YamlParser yamlParser = new YamlParser(new Yaml().parse(inputStreamReader), file.getRealFile().getAbsolutePath());
                try {
                    final DocumentBuilder documentBuilder = new DocumentBuilder(itemBuilder);
                    final LinkedHashMap<String, Field> map = (LinkedHashMap<String, Field>) documentBuilder.build(yamlParser, localResult);
                    if (map != null) // merge.  map is null, when file is empty
                        for (Map.Entry<String, Field> entry : map.entrySet()) {
                            Field fld = entry.getValue();
                            fld.sourcePath = path;

                            fieldTypes.put(entry.getKey(), fld);
                        }
                } catch (ScannerException e) {
                    localResult.addMsg(YamlMessages.error_InvalidYamlFormat, yamlParser.filename,
                            e.getProblem(), e.getProblemMark().getLine(), e.getProblemMark().getColumn());
                } finally {
                    try {
                        inputStreamReader.close();
                    } catch (IOException e) {
                        Logger.error(e, "Failed to close file.");
                    }
                }
            } catch (NoSuchFieldException e) { // from getClass()
                Logger.error(e, "Unexpected");
            }

            if (localResult.isNotOk())
                result.append(localResult, filename);
            else
                result.addMsg(YamlMessages.debug_FileLoadedSuccessfully, filename);
        }

        if (fieldTypes.get(BuiltInEnums.USER_ROLES_ENUM.getUpperCase()) != null)
            result.addMsg(YamlMessages.error_EnumHasReservedName, BuiltInEnums.USER_ROLES_ENUM.toString());

        if (fieldTypes == null) {
            result.addMsg(YamlMessages.error_FileNotFound, PATH_FIELD_TYPES);
            return;
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends RootElement> T loadOneYamlFile(VirtualFile vf, T[] type, Result result) {
        final Yaml yaml = new Yaml();
        final Result localResult = new Result();
        final InputStreamReader inputStreamReader = new InputStreamReader(vf.inputstream());
        final YamlParser yamlParser = new YamlParser(
                yaml.parse(inputStreamReader),
                vf.getRealFile().getAbsolutePath());
        try {
            String filename = null;
            try {
                filename = vf.getRealFile().getCanonicalPath();
            } catch (IOException e) {
                checkState(false);
            }
            final RootElement root;
            try {
                root = (RootElement) new DocumentBuilder(ItemBuilder.factory.get(
                        RootElement.class)).build(yamlParser, localResult);
            } catch (ScannerException e) {
                result.addMsg(YamlMessages.error_InvalidYamlFormat, yamlParser.filename,
                        e.getProblem(), e.getProblemMark().getLine(), e.getProblemMark().getColumn());
                return null;
            }
            if (!localResult.isNotOk()) {
                final String clearFilename = FileUtil.filename(filename);
                final Class<?> componentType = type.getClass().getComponentType();
                if (root == null ||
                        !(componentType.isAssignableFrom(root.getClass())) ||
                        !clearFilename.toLowerCase().equals(Strings.nullToEmpty(root.name).toLowerCase())) {
                    RootElement re = null;
                    try {
                        re = (RootElement) componentType.newInstance();
                        re.name = clearFilename;
                    } catch (InstantiationException e) {
                        checkState(false);
                    } catch (IllegalAccessException e) {
                        checkState(false);
                    }
                    localResult.addMsg(YamlMessages.error_FileExpectedToBegingWith, RootElementCompositeKeyHandler.expectedBegging(re));
                }
            }
            if (localResult.isNotOk()) {
                result.append(localResult, filename);
                return null;
            }

            localResult.addMsg(YamlMessages.debug_FileLoadedSuccessfully, filename);
            localResult.toLogger();

            return (T) root;
        } catch (ScannerException e) {
            localResult.addMsg(YamlMessages.error_InvalidYamlFormat, yamlParser.filename,
                    e.getProblem(), e.getProblemMark().getLine(), e.getProblemMark().getColumn());
            return null;
        } finally {
            try {
                inputStreamReader.close();
            } catch (IOException e) {
                Logger.error(e, "Failed to close file.");
            }
        }
    }

    private static ConcurrentSkipListMap<String, DocType> docTypeMap = new ConcurrentSkipListMap<String, DocType>();

    /**
     * Returns document type by model class name.
     */
    public static DocType getDocumentTypeByClass(Class type) {
        final String typeName = type.getName();
        DocType res = docTypeMap.get(typeName);
        if (res == null)
            docTypeMap.putIfAbsent(typeName, res = _getDocumentTypeByClass(typeName));
        return res == DocType.NOT_A_DOCUMENT ? null : res;
    }

    private static DocType _getDocumentTypeByClass(String typeName) {
        if (typeName.startsWith(DocType.MODELS_PACKAGE)) {
            // TODO: Consider that javaassist has a property that returns source class.  This may simplify this logic.
            final int proxyPostfix = typeName.indexOf("_$$_javassist_");
            if (proxyPostfix > 0) {
                final String n = typeName.substring(DocType.MODELS_PACKAGE.length(), proxyPostfix);
                final DocType doc = DocflowConfig.instance.documents.get(n.toUpperCase());
                return doc == null ? DocType.NOT_A_DOCUMENT : doc;
            }
        }
        final Class type = Play.classloader.getClassIgnoreCase(typeName);
        if (type == null) // this is possible, since getClassIgnoreCase() knows only classes defined within the project
            return DocType.NOT_A_DOCUMENT;
        if (!Document.class.isAssignableFrom(type))
            return DocType.NOT_A_DOCUMENT;

        final Method typeMethod;
        try {
            typeMethod = type.getMethod("_type");
            final int mod = typeMethod.getModifiers();
            checkState(Modifier.isPublic(mod), typeName);
            checkState(Modifier.isStatic(mod), typeName);
            checkState(typeMethod.getReturnType() == DocType.class, typeName);
        } catch (NoSuchMethodException e) {
            checkState(false, typeName);
            return null;
        }

        try {
            return (DocType) typeMethod.invoke(null);
        } catch (IllegalAccessException e) {
            throw new JavaExecutionException(e);
        } catch (InvocationTargetException e) {
            throw new JavaExecutionException(e);
        }
    }

// Note: It's version 1 when all documents were in model package.  It's obsoleted by implementing derivation of documents
//    private static String _getDocumentTypeByClass(Class type) {
//        final String typeName = type.getName();
//        if (!typeName.startsWith(DocType.MODELS_PACKAGE))
//            return null;
//        final int proxyPostfix = typeName.indexOf("_$$_javassist_");
//        if (proxyPostfix < 0)
//            return typeName.substring(DocType.MODELS_PACKAGE.length());
//        else
//            return typeName.substring(DocType.MODELS_PACKAGE.length(), proxyPostfix);
//    }
}
