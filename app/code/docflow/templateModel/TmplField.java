package code.docflow.templateModel;

import code.docflow.DocflowConfig;
import code.docflow.compiler.enums.BuiltInTypes;
import code.docflow.model.*;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.yaml.compositeKeyHandlers.FieldCompositeKeyHandler;
import code.docflow.utils.BitArray;
import code.docflow.utils.NamesUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import play.exceptions.TemplateExecutionException;
import play.exceptions.TemplateNotFoundException;
import play.templates.BaseTemplate;
import play.templates.GroovyTemplate;
import play.templates.TagContext;
import play.templates.TemplateLoader;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class TmplField {

    public static final String FIELD_ROOT = "field.";
    public static final String STRUCT_ROOT = "struct.";
    public static final String SUBTABLE_ROOT = "subtable.";
    public static final String ENUM_ROOT = "enum.";
    public static final String TYPE_ROOT = "type.";
    public static final String FIELD_LINK = "." + FIELD_ROOT;
    public static final String ENUM_LINK = "." + ENUM_ROOT;

    public static String typeRoot(BuiltInTypes fieldType) {
        switch (fieldType) {
            case STRUCTURE:
                return STRUCT_ROOT;
            case SUBTABLE:
                return SUBTABLE_ROOT;
            case ENUM:
                return ENUM_ROOT;
        }
        return TYPE_ROOT;
    }

    /**
     * Document this field belongs to.
     */
    TmplDocument document;

    /**
     * Owner template.
     */
    TmplTemplate ownerTemplate;

    /**
     * Owner action. Not null, if field is part of action params.
     */
    TmplAction ownerAction;

    /**
     * Title in form for localization.
     */
    String title;

    /**
     * HTML form element id.
     */
    String id;

    /**
     * Simple name of the field.
     */
    String name;

    /**
     * Full name of the field.
     */
    String fullname;

    /**
     * Template name for field.
     */
    String template;

    /**
     * True, if user can view this field.
     */
    String view;

    /**
     * True, if user can update this field.
     */
    String update;

    /**
     * Type of field.
     */
    String type;

    /**
     * True, if this is udt type defined field.
     */
    boolean udtType;

    /**
     * DocType of tags field.
     */
    String tagsDocType;

    /**
     * Basic type of the field.  Like 'enum', 'structure', 'refers', 'int' etc.  Note: This filed
     * is only for internal use of TmplField code.  It's not visible from groovy scripts.
     */
    String basicType;

    boolean hidden;

    boolean local;

    boolean derived;

    Boolean required;
    Double min;
    Double max;
    Integer minLength;
    Integer maxLength;
    String pattern;

    ImmutableList<TmplField> fields;
    ImmutableMap<String, TmplField> fieldByName;

    ImmutableList<TmplEnumValue> enumValues;
    ImmutableMap<String, TmplEnumValue> enumValueByName;

    ImmutableList<String> tagsSequence;

    String classAttr;

    boolean second;

    boolean info;

    boolean single;

    @SuppressWarnings("unchecked")
    public static TmplField buildFor(TmplDocument tmplDoc, TmplTemplate ownerTemplate, TmplAction action, String rootTitle, Template template, Field fld,
                                     final DocumentAccessActionsRights rights, final BitArray mask,
                                     boolean readonly, boolean view, boolean update) {

        checkNotNull(fld);

        final TmplField res = new TmplField();

        res.document = tmplDoc;
        res.ownerTemplate = ownerTemplate;
        res.ownerAction = action;

        res.name = fld.name;

        res.fullname = fld.fullname;

        res.derived = fld.derived;

        res.hidden = fld.hidden;

        res.local = fld.local;

        if (template != null)
            res.template = template.templateNameByField[fld.index];

        if (action == null) // it's template field
            res.id = tmplDoc.name + "_" + fld.fullname.replace('.', '_');
        else // it's action parameter
            res.id = fld.fullname.replace('.', '_');

        res.required = fld.required;

        res.basicType = fld.type.toString();

        res.second = fld.second;

        res.info = fld.info;

        if (view)
            res.view = "item." + fld.name;

        if (fld.udtType != null) {
            res.type = fld.udtType;
            res.udtType = fld.udtTypeRef != null;
        } else if (fld.type == BuiltInTypes.REFERS || fld.type == BuiltInTypes.FILE) {
            res.type = "_" + ((FieldReference) fld).refDocument;
            if (update)
                res.update = "item." + res.name;
        } else
            res.type = fld.type.toString();

        if (fld.udtTypeRoot && rootTitle == null)
            res.title = typeRoot(fld.type) + fld.name;
        else if (action == null) // it's template field
            res.title = (rootTitle != null ? rootTitle : tmplDoc.title) + FIELD_LINK + fld.name;
        else // it's action parameter
            res.title = (rootTitle != null ? rootTitle : action.title) + TmplAction.ACTION_PARAM_LINK + fld.name;

        if (fld.type == BuiltInTypes.TAGS) {
            FieldStructure fieldStructure = (FieldStructure) fld;
            final FieldReference tag = (FieldReference) fieldStructure.fields.get(FieldCompositeKeyHandler.FIELD_TAG);
            res.tagsDocType = tag.refDocument;
        }

        if (fld.type == BuiltInTypes.STRUCTURE || fld.type == BuiltInTypes.SUBTABLE) {
            FieldStructure fieldStructure = (FieldStructure) fld;

            String newRootTitle = fieldStructure.udtTypeRef != null ? typeRoot(fld.type) + fieldStructure.udtType : res.title;

            if (update)
                res.update = "item." + res.name + "._u";

            final ImmutableList.Builder<TmplField> fldListBuilder = ImmutableList.builder();
            for (Field field : fieldStructure.fields.values()) {
                if (field.hidden || !mask.get(field.index))
                    continue;
                final TmplField tf = TmplField.buildFor(tmplDoc, ownerTemplate, null, newRootTitle, template, field, rights, mask,
                        readonly,
                        rights.viewMask.get(field.index) || (readonly && rights.updateMask.get(field.index)),
                        !readonly && rights.updateMask.get(field.index));
                fldListBuilder.add(tf);
            }
            ImmutableList<TmplField> fields = res.fields = fldListBuilder.build();

            final ImmutableMap.Builder<String, TmplField> fldMapBuilder = ImmutableMap.builder();
            for (int i = 0; i < fields.size(); i++) {
                TmplField tmplField = fields.get(i);
                fldMapBuilder.put(tmplField.name.toUpperCase(), tmplField);
            }
            res.fieldByName = fldMapBuilder.build();

            res.single = fieldStructure.single;

        } else if (fld.type == BuiltInTypes.ENUM) {
            FieldEnum fieldEnum = (FieldEnum) fld;

            String newRootTitle = fieldEnum.udtTypeRef != null ? ENUM_ROOT + fieldEnum.udtType : res.title;

            if (update)
                res.update = "item._u." + res.name;

            final ImmutableList.Builder<TmplEnumValue> enumValuesBuilder = ImmutableList.builder();

            for (FieldEnumItem anEnum : fieldEnum.strValues.values()) {
                final TmplEnumValue ev = TmplEnumValue.buildFor(tmplDoc, newRootTitle, fieldEnum, anEnum);
                enumValuesBuilder.add(ev);
            }
            ImmutableList<TmplEnumValue> enumValues = res.enumValues = enumValuesBuilder.build();

            final ImmutableMap.Builder<String, TmplEnumValue> fldMapBuilder = ImmutableMap.builder();
            for (int i = 0; i < enumValues.size(); i++) {
                TmplEnumValue tmplField = enumValues.get(i);
                fldMapBuilder.put(tmplField.name.toUpperCase(), tmplField);
            }
            res.enumValueByName = fldMapBuilder.build();
        } else if (fld instanceof FieldSimple) {

            final FieldSimple fs = (FieldSimple) fld;

            if (update)
                res.update = "item._u." + res.name;

            if (fs.accessedFields != null) {
                if (fs.accessedFields.contains("MIN"))
                    res.min = fs.min;
                if (fs.accessedFields.contains("MAX"))
                    res.max = fs.max;
                if (fs.accessedFields.contains("MINLENGTH"))
                    res.minLength = fs.minLength;
                if (fs.accessedFields.contains("MAXLENGTH"))
                    res.maxLength = fs.maxLength;
                if (fs.accessedFields.contains("PATTERN"))
                    res.pattern = fs.pattern;
            }
        }

        res.tagsSequence = buildTagsSequence(fld);

        buildClassAttr(res);

        tmplDoc.fieldTitle.put(res.fullname.toUpperCase(), res.title);

        return res;
    }

    /**
     * Implements same logic as Field.smartTag().
     */
    private static ImmutableList<String> buildTagsSequence(Field fld) {
        final ImmutableList.Builder<String> tagSequenceBuilder = ImmutableList.builder();
        int s = fld.type == BuiltInTypes.REFERS ? -1 : 0;
        String currentType = fld.template;
        loop:
        for (; ; ) {
            tagSequenceBuilder.add(currentType);
            switch (s) {
                case -1:
                    currentType = "_" + ((FieldReference) fld).refDocument;
                    s = -2;
                    continue;
                case -2:
                    break;
                case 0:
                    if (fld.implicitFieldType != null) {
                        currentType = "__" + fld.implicitFieldType.toString();
                        s = 1;
                        continue;
                    }
                    // fallthrough
                case 1:
                    if (fld.udtType == null)
                        break;
                    currentType = fld.udtType;
                    s = 2;
                    continue;
                case 2:
                    Field t = DocflowConfig.instance.fieldTypes.get(currentType.toUpperCase());
                    if (t == null || t.udtType == null)
                        break;
                    currentType = t.udtType;
                    continue;
                case 3:
                    break loop;
            }
            s = 3;
            currentType = fld.type.toString();
        }
        return tagSequenceBuilder.build();
    }

    // TODO: Obsolete: _self columns should not be used in new layout, so delete this after 2/1/2015
    public static TmplField buildSelfFieldFor(TmplModel root, TmplTemplate ownerTemplate, TmplDocument document) {

        final TmplField res = new TmplField();
        res.document = document;
        res.ownerTemplate = ownerTemplate;
        res.title = document.title;
        res.name = "";
        res.id = document.name;

        res.basicType = BuiltInTypes.REFERS.toString();
        res.type = "_" + document.name;

        res.view = "item";
        res.update = null;

        final ImmutableList.Builder<String> tagsSequenceBuilder = ImmutableList.builder();
        tagsSequenceBuilder.add(res.type);
        tagsSequenceBuilder.add("document-tag-missing");
        res.tagsSequence = tagsSequenceBuilder.build();

        buildClassAttr(res);

        return res;
    }

    private static void buildClassAttr(TmplField res) {
        StringBuilder classesList = new StringBuilder();
        if (res.name.length() > 0) // it's empty string, for _self field
            classesList.append("df-field-").append(NamesUtil.wordsToDashSeparated(res.name));
        boolean skipFieldName = true;
        for (String typeName : res.tagsSequence)
            if (skipFieldName)
                skipFieldName = false;
            else
                classesList.append(" df-type-").append(NamesUtil.wordsToDashSeparated(typeName));
        res.classAttr = classesList.toString();
    }

    public TmplDocument getDocument() {
        return document;
    }

    public TmplTemplate getOwnerTemplate() {
        return ownerTemplate;
    }

    public TmplAction getOwnerAction() {
        return ownerAction;
    }

    public TmplField getSibling(String name) {
        return ownerTemplate.getFieldByName(name);
    }

    public String getTitle() {
        return title;
    }

    public String getName() {
        return name;
    }

    public String getFullname() {
        return fullname;
    }

    public String getTemplate() {
        return template;
    }

    public String getId() {
        return id;
    }

    public String getView() {
        return view;
    }

    public String getUpdate() {
        return update;
    }

    public String getBasicType() {
        return basicType;
    }

    public String getType() {
        return type;
    }

    public boolean getUdtType() {
        return udtType;
    }

    public String getTagsDocType() {
        return tagsDocType;
    }

    public boolean getDerived() {
        return derived;
    }

    public Boolean getRequired() {
        return required;
    }

    public Double getMin() {
        return min;
    }

    public Double getMax() {
        return max;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public String getPattern() {
        return pattern;
    }

    public ImmutableList<TmplField> getFields() {
        return fields;
    }

    public TmplField getFieldByName(String name) {
        return fieldByName.get(name.toUpperCase());
    }

    public String getClassAttr() {
        return classAttr;
    }

    public Boolean getSecond() {
        return second;
    }

    public Boolean getInfo() {
        return info;
    }

    public ImmutableList<TmplEnumValue> getEnumValues() {
        return enumValues;
    }

    public TmplEnumValue getEnumValueByName(String name) {
        return enumValueByName.get(name.toUpperCase());
    }

    public boolean getSingle() {
        return single;
    }

    public ImmutableList<String> getTagsSequence() {
        return tagsSequence;
    }

    public void smartTag(String namespace, GroovyTemplate.ExecutableTemplate template, boolean debug) {
        smartTag(namespace, template, debug, null);
    }

    /**
     * Runs tag for this field, based on field type, applying hierarchical type search.
     */
    @SuppressWarnings("unchecked")
    public void smartTag(String namespace, GroovyTemplate.ExecutableTemplate template, boolean debug, Map<String, Object> params) {

        final String path = namespace.replace(".", "/");

        int lastTemplate = tagsSequence.size() - 1;
        for (int i = 0; i < lastTemplate; i++) {
            String currentType = tagsSequence.get(i);
            try {
                Map<String, Object> newArgs = new HashMap<String, Object>();
                newArgs.put("_path", path);
                newArgs.put("_type", currentType);
                newArgs.put("_debug", debug);
                renderTemplate(newArgs, path, currentType, template, params);
                return;
            } catch (TemplateNotFoundException e) {
                if (!e.getMessage().endsWith(currentType + ".html")) // NotFoundException while rendering found tag
                    throw new TemplateExecutionException(template.template, 0,
                            String.format("Document '%1$s': Field '%2$s': Failed render tag '%3$s'. Reason: '%4$s'",
                                    document.name, this.name, namespace + "." + type, e.getMessage()),
                            new Exception());
                // otherwise keep trying
            }
        }

        // try last one. if not, then report by missingTag tag
        String currentType = tagsSequence.get(lastTemplate);
        try {
            Map<String, Object> newArgs = new HashMap<String, Object>();
            newArgs.put("_path", path);
            newArgs.put("_type", currentType);
            newArgs.put("_debug", debug);
            renderTemplate(newArgs, path, currentType, template, params);
            return;
        } catch (TemplateNotFoundException e) {
            throw new TemplateExecutionException(template.template, 0,
                    String.format((e.getMessage().endsWith(currentType + ".html") ?
                                    "Document '%1$s': Field '%2$s': Tag '%3$s' not found" : // Tag itself not found
                                    "Document '%1$s': Field '%2$s': Failed render tag '%3$s'. Reason: '%4$s'"), // NotFoundException while rendering found tag
                            document.name, this.name, namespace + "." + type, e.getMessage()),
                    new Exception());
        }
    }

    public void debugSmartTag(String namespace, GroovyTemplate.ExecutableTemplate template) {
        debugSmartTag(namespace, template, null);
    }

    /**
     * Runs tag for this field, based on field type, applying hierarchical type search.
     */
    @SuppressWarnings("unchecked")
    public void debugSmartTag(String namespace, GroovyTemplate.ExecutableTemplate template, Map<String, Object> params) {

        final String path = namespace.replace(".", "/");

        int lastTemplate = tagsSequence.size() - 1;
        for (int i = 0; i < lastTemplate; i++) {
            try {
                String currentType = tagsSequence.get(i);

                TemplateLoader.load("tags/" + path + "/" + currentType + ".html"); // check template

                Map<String, Object> newArgs = new HashMap<String, Object>();
                newArgs.put("_path", path);
                newArgs.put("_type", currentType);
                newArgs.put("_debug", true);
                final int p = namespace.indexOf('.');

                renderTagSelectionSequence(namespace, template, params, newArgs);
                return;
            } catch (TemplateNotFoundException e) {
                // nothing - keep trying
            }
        }

        // try last one. if not, then report by missingTag tag
        String currentType = tagsSequence.get(lastTemplate);
        try {

            TemplateLoader.load("tags/" + path + "/" + currentType + ".html");  // check template

            Map<String, Object> newArgs = new HashMap<String, Object>();
            newArgs.put("_path", path);
            newArgs.put("_type", currentType);
            newArgs.put("_debug", true);
            renderTagSelectionSequence(namespace, template, params, newArgs);
            return;

        } catch (TemplateNotFoundException e) {
            Map<String, Object> newArgs = new HashMap<String, Object>();
            newArgs.put("_path", path);
            newArgs.put("_type", null);
            newArgs.put("_debug", true);
            renderTagSelectionSequence(namespace, template, params, newArgs);
        }
    }

    /**
     * Renders selection sequence. As first choice uses templateBase taken from namespace, otherwise goes to ngApp as default
     */
    private void renderTagSelectionSequence(String namespace, GroovyTemplate.ExecutableTemplate template, Map<String, Object> params, Map<String, Object> newArgs) {
        int p = namespace.indexOf('.');
        if (p > 0) {
            String templateBase = namespace.substring(0, p);
            if (!templateBase.equals("ngApp")) {
                try {
                    renderTemplate(newArgs, templateBase + "/dbg", "tag-selection-sequence", template, params);
                    return;
                } catch (TemplateNotFoundException e) {
                    // fallthru
                }
            }
        }
        renderTemplate(newArgs, "ngApp/dbg", "tag-selection-sequence", template, params);
    }

    private void renderTemplate(Map<String, Object> args, String path, String type,
                                GroovyTemplate.ExecutableTemplate template,
                                Map<String, Object> params) {
        final String tagFile = "tags/" + path + "/" + type + ".html";
        BaseTemplate t = (BaseTemplate) TemplateLoader.load(tagFile);
        TreeMap<String, Object> newArgs = new TreeMap<String, Object>(args);
        newArgs.putAll(template.getBinding().getVariables());
        newArgs.put("_isInclude", true);
        newArgs.put("_arg", this);
        if (params != null)
            for (Map.Entry<String, Object> entry : params.entrySet())
                newArgs.put("_" + entry.getKey( ), entry.getValue());
        TagContext.enterTag(path + "/" + type);
        t.render(newArgs);
        TagContext.exitTag();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (required != null && required)
            sb.append("required");
        if (min != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("min:").append(min);
        }
        if (max != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("max:").append(max);
        }
        if (minLength != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("minLength:").append(minLength);
        }
        if (maxLength != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("maxLength:").append(maxLength);
        }
        if (pattern != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("pattern:").append(pattern);
        }
        return type + " " + document.name + "." + fullname + (sb.length() > 0 ? " " + sb.toString() : "");
    }
}
