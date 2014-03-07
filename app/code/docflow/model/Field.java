package code.docflow.model;

import code.docflow.DocflowConfig;
import code.docflow.collections.Item;
import code.docflow.yaml.annotations.NotYamlField;
import code.docflow.yaml.annotations.WithCompositeKeyHandler;
import code.docflow.yaml.compositeKeyHandlers.FieldCompositeKeyHandler;
import com.google.common.base.Objects;
import play.exceptions.TemplateExecutionException;
import play.exceptions.TemplateNotFoundException;
import play.templates.BaseTemplate;
import play.templates.GroovyTemplate;
import play.templates.TemplateLoader;
import play.vfs.VirtualFile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
@WithCompositeKeyHandler(FieldCompositeKeyHandler.class)
public abstract class Field extends Item {

    public enum Type {
        STRING(new String[]{"length"}, new String[]{"maxLength", "minLength", "pattern"}),
        DOUBLE(null, new String[]{"min", "max"}),
        INT(null, new String[]{"min", "max"}),
        LONG(null, new String[]{"min", "max"}),
        BOOL(null, null),
        ENUM(null, null),
        REFERS(null, new String[]{"refDocument"}),
        POLYMORPHIC_REFERS(null, new String[]{"refDocuments"}),
        STRUCTURE(null, null),
        SUBTABLE(null, null),
        TAGS(null, null),
        DATE(null, null),
        TEXT(null, null),
        TIMESTAMP(null, null),
        PASSWORD(null, null),
        CALCULATED(null, null);

        public final String[] required;
        public final String[] optional;
        private final String[] empty = new String[0];

        private Type(String[] required, String[] optional) {
            this.required = required == null ? empty : required;
            this.optional = optional == null ? empty : optional;
        }

        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    @NotYamlField
    public DocType document;

    public Type type;

    /**
     * Field template for UI.
     */
    @NotYamlField
    public String template;

    /**
     * User defined type - UDT.  Type defined in fieldTypes.yaml.  But, of course, can be just an error type name.
     */
    public String udtType;

    public boolean required;

    public boolean hidden;

    /**
     * When true, this Field considered as field contained calculated, in some way, value.  So, it cannot be updated
     * by user directly.
     */
    public boolean derived;

    /**
     * Can only by true, when 'derived' is true as well. When true, this Field calculated by code every
     * time when data comes to user.  Otherwise, derived field are stored in db and being update
     * by other actions.
     */
    public boolean calculated;

    /**
     * True, if field suppose to an info block.  Used in form template to place info block in upper-right corner.
     */
    public boolean info;

    /**
     * True, if columns should be shown in right column on wide screen.
     */
    public boolean second;

    /**
     * True for fields that will be stored as JSON in 'textStorage' field.
     */
    public boolean textstorage;

    @NotYamlField
    public boolean dbRequired;

    @NotYamlField
    public String fullname;

    @NotYamlField
    public DocflowConfig.ImplicitFields implicitFieldType;

    @NotYamlField
    public Field udtTypeRef;

    @NotYamlField
    public boolean udtTypeRoot;

    /**
     * Map of field names accessed while yaml loading.
     */
    @NotYamlField
    public HashSet<String> accessedFields;

    /**
     * If not NULL, it's structure field belongs to.
     */
    @NotYamlField
    public FieldStructure structure;

    @NotYamlField
    public int index;

    @NotYamlField
    public int endIndex;

    @NotYamlField
    public VirtualFile sourcePath;

    public abstract Field deepCopy();

    protected void deepCopy(Field fld) {
        super.deepCopy(fld);

        fld.type = type;
        fld.udtTypeRef = udtTypeRef;

        fld.udtType = udtType;
        fld.template = template;
        fld.required = required;
        fld.hidden = hidden;
        fld.derived = derived;
        fld.calculated = calculated;
        fld.textstorage = textstorage;
        fld.accessedFields = new HashSet<String>(accessedFields);
    }

    public void mergeTo(Field field) {
        field.type = type;
        field.udtTypeRef = field;
        // skip udtTypeRoot
        // skip udtType
    }

    @Override
    protected Objects.ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add("index", index)
                .add("fullname", fullname);
    }

    public void smartTag(String namespace, GroovyTemplate.ExecutableTemplate template) {
        smartTag(namespace, template, null);
    }

    /**
    * Runs tag for this field, based on field type, applying hierarchical type search.  This method is only
    * to be used in code generation tasks.  For UI rendering TmplField.smartTag ought to be used.
    */
    @SuppressWarnings("unchecked")
    public void smartTag(String namespace, GroovyTemplate.ExecutableTemplate template, Map<String, Object> params) {
        final String path = namespace.replace(".", "/");
        int s = 0;
        String currentType = this.template;
        for (;;) {
            try {
                final String tagFile = "tags/" + path + "/" + currentType + ".txt";
                BaseTemplate t = (BaseTemplate) TemplateLoader.load(tagFile);
                Map<String, Object> newArgs = new HashMap<String, Object>();
                newArgs.putAll(template.getBinding().getVariables());
                newArgs.put("_isInclude", true);
                newArgs.put("_arg", this);
                if (params != null)
                    for (Map.Entry<String, Object> entry : params.entrySet())
                        newArgs.put("_" + entry.getKey(), entry.getValue());
                t.render(newArgs);
                return;

            } catch (TemplateNotFoundException e) {
                if (s == 2)
                    throw new TemplateExecutionException(template.template, 0,
                            String.format("Document '%1$s': Field '%2$s': Not found tag '%3$s' to render field type.",
                                    document.name, this.fullname, namespace + '.' + this.type),
                            new Exception());
            }
            switch (s) {
                case 0:
                    if (udtType == null)
                        break;
                    currentType = udtType;
                    s = 1;
                    continue;
                case 1:
                    Field t = DocflowConfig.instance.fieldTypes.get(currentType.toUpperCase());
                    if (t == null || t.udtType == null)
                        break;
                    currentType = t.udtType;
                    continue;
            }
            s = 2;
            currentType = type.toString();
        }
    }
}
