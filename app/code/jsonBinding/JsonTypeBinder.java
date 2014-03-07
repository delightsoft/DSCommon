package code.jsonBinding;

import code.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.action.DocumentUpdate;
import code.docflow.model.Action;
import code.docflow.model.DocType;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.yaml.YamlMessages;
import code.jsonBinding.annotations.doc.JsonContains;
import code.jsonBinding.annotations.doc.JsonEnumMultiple;
import code.jsonBinding.annotations.doc.JsonPartOfStructure;
import code.jsonBinding.annotations.field.JsonAccessor;
import code.jsonBinding.annotations.field.JsonExclude;
import code.jsonBinding.annotations.field.JsonHead;
import code.jsonBinding.annotations.field.JsonPassword;
import code.jsonBinding.binders.doc.*;
import code.jsonBinding.binders.field.*;
import code.models.Document;
import code.models.PersistentDocument;
import code.types.PolymorphicRef;
import code.users.CurrentUser;
import code.utils.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import com.google.common.collect.Multimap;
import docflow.DocflowMessages;
import org.joda.time.DateTime;
import play.Play;
import play.db.jpa.JPA;
import play.db.jpa.JPABase;
import play.exceptions.JavaExecutionException;
import play.exceptions.UnexpectedException;

import javax.persistence.Embeddable;
import javax.persistence.Entity;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.*;
import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class JsonTypeBinder extends Builder {

    public static TypeBuildersFactory<JsonTypeBinder> factory = new TypeBuildersFactory<JsonTypeBinder>() {
        public JsonTypeBinder newInstance(TypeDescription typeDesc) {
            checkArgument(typeDesc.parameters == null);
            return new JsonTypeBinder(typeDesc.type);
        }
    };

    public final Class<?> type;
    public final Class<?> proxyType;
    public final boolean isEmbeddable;
    public RecordAccessor recordAccessor;

    public static abstract class FieldAccessor {
        public final Field fld;
        public final Method getter;
        public final Method setter;
        public final String fldName;

        public int index;

        public boolean required;

        public void setField(code.docflow.model.Field field) {
            index = field.index;
            required = field.required;
        }

        protected FieldAccessor(Field fld, Method getter, Method setter, String fldName) {
            this.fld = fld;
            this.getter = getter;
            this.setter = setter;
            this.fldName = fldName;
        }

        public void copyFromJson(Object obj, JsonNode node, DocumentAccessActionsRights rights, BitArray mask, String fldPrefix, DocumentUpdate update, PolymorphicRef docId, Object outerStructure, Result result)
                throws Exception {
            // nothing
        }

        public void copyToJson(Object obj, Template template, JsonGenerator generator, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
            // nothing
        }

        public boolean isEmpty(Object obj) throws Exception {
            return getter.invoke(obj) == null;
        }
    }

    ArrayList<FieldAccessor> fieldsAccessors = new ArrayList<FieldAccessor>();
    TreeMap<String, FieldAccessor> fieldsAccessorsIndex = new TreeMap<String, FieldAccessor>();

    public DocType doc;
    FieldAccessor[] docAccessors;

    /**
     * Sample instance, to be used to generate JSON object or structures prototypes.
     */
    public Object sample;

    /**
     * Mask that covers only field that belong to this level of structure.  Fields located in substrcutres
     * marked false.
     */
    BitArray structureLevelMask;

    private JsonTypeBinder(final Class<?> type) {
        this.proxyType = type;
        final String typeName = type.getName();
        final int proxyPostfix = typeName.indexOf("_$$_javassist_");
        if (proxyPostfix < 0)
            this.type = type;
        else
            this.type = Play.classloader.getClassIgnoreCase(typeName.substring(0, proxyPostfix));
        this.isEmbeddable = type.isAnnotationPresent(Embeddable.class);
    }

    private void addAccessor(FieldAccessor fieldAccessor) {
        fieldsAccessors.add(fieldAccessor);
        fieldsAccessorsIndex.put(fieldAccessor.fldName, fieldAccessor);
    }

    @Override
    protected void init() {

        final boolean isEntity = type.isAnnotationPresent(Entity.class);
        final boolean isJsonPartOfStructure = type.isAnnotationPresent(JsonPartOfStructure.class);

        doc = DocflowConfig.getDocumentTypeByClass(type);
        SubrecordAccessor subrecordAccessor = isJsonPartOfStructure ? SubrecordAccessor.factory.get(type) : null;
        recordAccessor = !isJsonPartOfStructure && isEntity ? RecordAccessor.factory.get(type) : null;

        // assign stub accessors for utility fields
        if (subrecordAccessor != null) {
            addAccessor(new StubBinder(subrecordAccessor.fldFK, DocflowConfig.ImplicitFields.FK.toString()));
            addAccessor(new StubBinder(subrecordAccessor.fldI, DocflowConfig.ImplicitFields.I.toString()));
        }

        final boolean isJPABaseBased = JPABase.class.isAssignableFrom(type);

        Field fld = null;
        try {
            final Field[] fl = type.getFields();
            for (int i = 0; i < fl.length; i++) {
                fld = fl[i];
                final int m = fld.getModifiers();

                if (!Modifier.isStatic(m) && Modifier.isPublic(m)) {

                    final String fldName = fld.getName(); // Later, could added support for renaming field by mean of annotations

                    if (fld.isAnnotationPresent(JsonExclude.class))
                        continue;

                    if (isJPABaseBased && fld.getDeclaringClass().equals(JPABase.class))
                        continue; // Skip JPABase.willBeSaved field

                    final Class fldType = fld.getType();
                    final Method getter = getGetterMethod(fldName, fldType);
                    final Method setter = getSetterMethod(fldName, fldType);

                    final JsonAccessor jsonAccessorAnnotation = fld.getAnnotation(JsonAccessor.class);
                    if (jsonAccessorAnnotation != null) {
                        final Class accessorType = jsonAccessorAnnotation.value();
                        try {
                            final Constructor ctr = accessorType.getConstructor(Field.class, Method.class, Method.class, String.class);
                            addAccessor((FieldAccessor) ctr.newInstance(fld, getter, setter, fldName));
                        } catch (Exception e) {
                            throw new JavaExecutionException(String.format("Failed to instantiate field accessor of class '%1$s' for class '%2$s'.",
                                    accessorType.getName(), type.getName()), e);
                        }
                        continue;
                    }

                    if (fld.getAnnotation(JsonEnumMultiple.class) != null) {
                        addAccessor(new EnumMultipleBinder(fld, getter, setter, fldName));
                        continue;
                    }

                    // Special accessors for fields marked by @Id and @Version
                    if (recordAccessor != null) {
                        if (processId(fld, getter, setter, fldName)) continue;
                        if (processRev(fld, getter, setter, fldName)) continue;
                        if (!doc.report && doc.states.size() > 2)
                            if (processState(fld, getter, setter, fldName)) continue;
                        if (processCreateModifiedDeleted(fld, getter, setter, fldName)) continue;
                    } else if (subrecordAccessor != null) {
                        if (processId(fld, getter, setter, fldName)) continue;
                        if (fldName.equals(DocflowConfig.ImplicitFields.I.toString())) continue;
                        if (fldName.equals(DocflowConfig.ImplicitFields.FK.toString())) continue;
                    }

                    switch (PrimitiveType.get(fldType)) {
                        case StringType:
                            final JsonPassword jsonPasswordAnnotation = fld.getAnnotation(JsonPassword.class);
                            if (jsonPasswordAnnotation != null)
                                addAccessor(new PasswordBinder(fld, getter, setter, fldName));
                            else
                                addAccessor(new StringBinder(fld, getter, setter, fldName));
                            break;
                        case booleanType:
                            addAccessor(new BooleanBinder(fld, getter, setter, fldName));
                            break;
                        case BooleanType:
                            addAccessor(new BooleanOrNullBinder(fld, getter, setter, fldName));
                            break;
                        case intType:
                            addAccessor(new IntegerBinder(fld, getter, setter, fldName));
                            break;
                        case IntegerType:
                            addAccessor(new IntegerOrNullBinder(fld, getter, setter, fldName));
                            break;
                        case longType:
                            addAccessor(new LongBinder(fld, getter, setter, fldName));
                            break;
                        case LongType:
                            addAccessor(new LongOrNullBinder(fld, getter, setter, fldName));
                            break;
                        case doubleType:
                            addAccessor(new DoubleBinder(fld, getter, setter, fldName));
                            break;
                        case DoubleType:
                            addAccessor(new DoubleOrNullBinder(fld, getter, setter, fldName));
                            break;
                        case DateTimeType:
                            addAccessor(new DateTimeBinder(fld, getter, setter, fldName));
                            break;
                        case EnumType:
                            addAccessor(new EnumBinder(fld, getter, setter, fldName));
                            break;
                        case NotPrimitiveOrPrimitiveWrapper:
                            // Related entites

                            final boolean isJsonContains = fld.getAnnotation(JsonContains.class) != null;

                            if (isJsonContains) {
                                addAccessor(new SubtableBinder(fld, getter, setter, fldName));
                                break;
                            }

                            if (Document.class.isAssignableFrom(fldType)) {
                                addAccessor(new RefBinder(fld, getter, setter, fldName));
                                break;
                            }

                            if (PolymorphicRef.class.isAssignableFrom(fldType)) {
                                addAccessor(new PolymorphicRefBinder(fld, getter, setter, fldName));
                                break;
                            }

                            if (List.class.isAssignableFrom(fldType) || Map.class.isAssignableFrom(fldType) || Multimap.class.isAssignableFrom(fldType)) {
                                addAccessor(new CollectionBinder(fld, getter, setter, fldName));
                                break;
                            }

                            addAccessor(new StructureBinder(fld, getter, setter, fldName));
                            break;

                        default:
                            throw new UnexpectedException(String.format(
                                    "Binding for type '%2$s' (field '%1$s' in class '%3$s') is not supported.  Consider " +
                                            "changing type, extending JsonBinding or excluding field from JsonBinding.",
                                    fldName, fldType.getName(), type.getName()));
                    }
                }
            }
        } catch (Exception e) {
            throw new JavaExecutionException(String.format("Failed on field '%s'.", fld.getName()), e);
        }

        if (doc == null)
            // sort with respect to @JsonHead annotation
            Collections.sort(fieldsAccessors, sortWithRespectToJsonHeadAnnotation);
        // else Json Binding sequence will be controlled by rights managements rules.  Initialization in method linkDocumentFieldsToFieldsAccessors().

        if (type != proxyType) {
            final Result result = new Result();
            linkDocumentFieldsToFieldsAccessors(result);
            if (result.isError())
                throw new UnexpectedException(result.toString());
        } else
            try {
                sample = type.newInstance();
                if (isJPABaseBased)
                    JPA.em().detach(sample);
            } catch (InstantiationException e) {
                throw new JavaExecutionException(e);
            } catch (IllegalAccessException e) {
                throw new JavaExecutionException(e);
            }
    }

    public void linkDocumentFieldsToFieldsAccessors(Result result) {
        docAccessors = new FieldAccessor[doc.allFields.size()];
        // TODO: Turn to use FieldStructure.mask and levelMask
        structureLevelMask = new BitArray(doc.allFields.size());
        structureLevelMask.inverse(); // make all true

        linkDocumentFieldsToFieldsAccessors(this, "", result);

        // find all fields what do not have accessors
        for (int i = 0; i < docAccessors.length; i++) {
            FieldAccessor docAccessor = docAccessors[i];
            if (docAccessor == null)
                result.addMsg(YamlMessages.error_DocumentModelFieldNotFoundInClass, doc.name, doc.allFields.get(i).fullname, type.getName());
        }
    }

    private void linkDocumentFieldsToFieldsAccessors(JsonTypeBinder structureBinder, String namePrefix, Result result) {

        final ArrayList<FieldAccessor> accessors = structureBinder.fieldsAccessors;
        for (int i = 0; i < accessors.size(); i++) {
            FieldAccessor fieldAccessor = accessors.get(i);
            final String fn = namePrefix + fieldAccessor.fldName;
            final code.docflow.model.Field field = doc.fieldByFullname.get(fn.toUpperCase());
            if (field == null) {
                result.addMsg(YamlMessages.error_DocumentClassFieldNotDefinedInModel, doc.name, fn, type.getName(), fieldAccessor.fldName);
                continue;
            }

            docAccessors[field.index] = fieldAccessor;
            fieldAccessor.setField(field);

            // it's a substructure

            JsonTypeBinder typeBinder = fieldAccessor instanceof SubtableBinder ? ((SubtableBinder) fieldAccessor).typeBinder : null;

            if (typeBinder == null)
                typeBinder = fieldAccessor instanceof StructureBinder ? ((StructureBinder) fieldAccessor).typeBinder : null;

            if (typeBinder != null) {
                typeBinder.doc = doc;
                typeBinder.docAccessors = docAccessors;

                final BitArray m = new BitArray(doc.allFields.size());
                for (int j = field.index + 1; j < field.endIndex; j++)
                    m.set(j, true);
                typeBinder.structureLevelMask = m;
                structureBinder.structureLevelMask.subtract(m);

                linkDocumentFieldsToFieldsAccessors(typeBinder, namePrefix + fieldAccessor.fldName + ".", result);
            }
        }
    }

    private Comparator<FieldAccessor> sortWithRespectToJsonHeadAnnotation = new Comparator<FieldAccessor>() {
        @Override
        public int compare(FieldAccessor o1, FieldAccessor o2) {
            final boolean o1isHead = o1.fld.isAnnotationPresent(JsonHead.class);
            final Class<?> o1DeclClass = o1.fld.getDeclaringClass();
            final boolean o2isHead = o2.fld.isAnnotationPresent(JsonHead.class);
            final Class<?> o2DeclClass = o2.fld.getDeclaringClass();

            if (o1DeclClass == o2DeclClass)
                return o1isHead ? (o2isHead ? 0 : -1) : (o2isHead ? 1 : 0);

            if (o1DeclClass.isAssignableFrom(o2DeclClass))
                return o1isHead ? -1 : 1;

            return o2isHead ? 1 : -1;
        }
    };

    private Method getSetterMethod(String fldName, Class fldType) {
        final String name = "set" + fldName.substring(0, 1).toUpperCase() + fldName.substring(1);
        try {
            return proxyType.getMethod(name, fldType);
        } catch (NoSuchMethodException e) {
            throw new JavaExecutionException(e);
        } catch (SecurityException e) {
            throw new JavaExecutionException(e);
        }
    }

    private Method getGetterMethod(String fldName, Class fldType) {
        final String name = "get" + fldName.substring(0, 1).toUpperCase() + fldName.substring(1);
        try {
            return proxyType.getMethod(name);
        } catch (NoSuchMethodException e) {
            throw new JavaExecutionException(e);
        } catch (SecurityException e) {
            throw new JavaExecutionException(e);
        }
    }

    private boolean processId(final Field fld, Method getter, Method setter, final String fldName) {
        if (EnumUtil.isEqual(DocflowConfig.ImplicitFields.ID, fldName)) {
            addAccessor(new FieldAccessor(fld, getter, setter, fldName) {
                public void copyToJson(Object obj, Template template, JsonGenerator generator, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
                    if (((JPABase) obj).isPersistent())
                        if (obj instanceof Document)
                            generator.writeStringField(fldName, doc.name + "@" + getter.invoke(obj));
                        else
                            generator.writeNumberField(fldName, (Long) getter.invoke(obj));
                    else if (obj instanceof Document)
                        generator.writeStringField(fldName, doc.name);
                }
            });
            return true;
        }
        return false;
    }

    private boolean processRev(final Field fld, Method getter, Method setter, final String fldName) {
        // TODO: Turn to EnumUtil.isEqual
        if (fldName.equals(DocflowConfig.ImplicitFields.REV.toString())) {
            addAccessor(new FieldAccessor(fld, getter, setter, fldName) {
                public void copyToJson(Object obj, Template template, JsonGenerator generator, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
                    if (((JPABase) obj).isPersistent())
                        generator.writeNumberField(fldName, (Integer) getter.invoke(obj));
                }
            });
            return true;
        }
        return false;
    }

    private boolean processState(final Field fld, Method getter, Method setter, final String fldName) {
        if (fldName.equals(DocflowConfig.ImplicitFields.STATE.toString())) {
            addAccessor(new StateBinder(fld, getter, setter, fldName));
            return true;
        }
        return false;
    }

    private boolean processCreateModifiedDeleted(final Field fld, Method getter, Method setter, final String fldName) {
        if (fldName.equals(DocflowConfig.ImplicitFields.CREATED.toString()) || fldName.equals(DocflowConfig.ImplicitFields.MODIFIED.toString())) {
            addAccessor(new FieldAccessor(fld, getter, setter, fldName) {
                public void copyToJson(Object obj, Template template, JsonGenerator generator, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
                    if (((JPABase) obj).isPersistent()) {
                        final DateTime v = (DateTime) getter.invoke(obj);
                        generator.writeNumberField(fldName, v.getMillis());
                    }
                }
            });
            return true;
        }
        if (fldName.equals(DocflowConfig.ImplicitFields.DELETED.toString())) {
            addAccessor(new FieldAccessor(fld, getter, setter, fldName) {
                public void copyToJson(Object obj, Template template, JsonGenerator generator, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
                    if (((JPABase) obj).isPersistent()) {
                        final Boolean val = (Boolean) getter.invoke(obj);
                        if (val)
                            generator.writeBooleanField(fldName, val);
                    }
                }
            });
            return true;
        }
        return false;
    }

    /**
     * @param fldPrefix      - Prefix to be used in result error messages to specify inner structures. I2.e. prefix
     *                       'customer.' will allow to report that field 'customer.id' got wrong value.
     * @param update
     * @param docId
     * @param outerStructure
     */
    public void fromJson(final Object obj, final String json, DocumentAccessActionsRights rights,
                         BitArray fieldsMask, final String fldPrefix, DocumentUpdate update,
                         PolymorphicRef docId, Object outerStructure, final Result result) {
        fromJson(obj, JsonBinding.toJsonNode(json), rights, fieldsMask, fldPrefix, update, docId, outerStructure, result);
    }

    /**
     * @param fldPrefix      - Prefix to be used in result error messages to specify inner structures. I2.e. prefix
     *                       'customer.' will allow to report that field 'customer.id' got wrong value.
     * @param update
     * @param outerStructure
     */
    public void fromJson(final Object obj, final JsonNode jsonNode, DocumentAccessActionsRights rights,
                         BitArray mask,
                         final String fldPrefix, DocumentUpdate update, PolymorphicRef docId, Object outerStructure, final Result result) {

        checkNotNull(obj);
        checkNotNull(jsonNode);
        checkArgument(rights == null || doc != null, "Rights are only applicable to IDocument interface implementations.");

        if (outerStructure == null)
            outerStructure = obj;

        if (doc != null) {
            if (rights == null)
                rights = RightsCalculator.instance.calculate((Document) obj, CurrentUser.getInstance());
            fromJsonWithRightsManagement(obj, jsonNode, rights, mask, fldPrefix, update, docId, isEmbeddable ? outerStructure : obj, result);
        } else
            noRightsManagmentFromJson(obj, jsonNode, fldPrefix, update, docId, isEmbeddable ? outerStructure : obj, result);
    }

    private void fromJsonWithRightsManagement(Object obj, JsonNode jsonNode, DocumentAccessActionsRights rights,
                                              BitArray mask, String fldPrefix,
                                              DocumentUpdate update, PolymorphicRef docId, Object outerStructure, Result result) {
        final String fp = (fldPrefix == null) ? "" : fldPrefix;

        if (mask == null)
            mask = rights.updateMask;
        else
            mask.intersect(rights.updateMask);

        BitArray levelUpdateMask = mask.copy();
        levelUpdateMask.intersect(structureLevelMask);

        final BitArray.EnumTrueValues en = levelUpdateMask.getEnumTrueValues();
        int i;
        while ((i = en.next()) != -1) {
            final FieldAccessor accessor = docAccessors[i];
            if (rights.updateMask.get(i))
                try {
                    final JsonNode node = jsonNode.get(accessor.fldName);
                    if (node != null)
                        accessor.copyFromJson(obj, node, rights, mask, fp, update, docId, outerStructure, result);
                } catch (Exception e) {
                    throw new JavaExecutionException(String.format("Failed on field '%s'", accessor.fldName), e);
                }
            try {
                if (accessor.required && accessor.isEmpty(obj))
                    result.addMsg(DocflowMessages.error_ValidationFieldRequired_1, fp + accessor.fldName);
            } catch (Exception e) {
                throw new JavaExecutionException(String.format("Failed on field '%s'", accessor.fldName), e);
            }
        }
    }

    private void noRightsManagmentFromJson(Object obj, JsonNode jsonNode, String fldPrefix,
                                           DocumentUpdate update, PolymorphicRef docId, Object outerStructure, Result result) {
        final String fp = (fldPrefix == null) ? "" : fldPrefix;
        for (int i = 0; i < fieldsAccessors.size(); i++) {
            FieldAccessor accessor = fieldsAccessors.get(i);
            final JsonNode node = jsonNode.get(accessor.fldName);
            if (node != null)
                try {
                    accessor.copyFromJson(obj, node, null, null, fp, update, docId, outerStructure, result);
                } catch (Exception e) {
                    throw new JavaExecutionException(String.format("Failed on field '%s'", accessor.fldName), e);
                }
        }
    }

    public final String toJsonString(final Object obj) {
        return toJsonString(obj, null);
    }

    public final String toJsonString(final Object obj, DocumentAccessActionsRights rights) {
        try {
            final StringWriter sw = new StringWriter();
            final JsonGenerator generator = JsonBinding.factory.createGenerator(sw);
            toJson(obj, generator, null);
            generator.flush();
            return sw.toString();
        } catch (IOException e) {
            throw new JavaExecutionException(e);
        }
    }

    public final String toJsonString(final Object obj, String templateName, DocumentAccessActionsRights rights) {
        try {
            final StringWriter sw = new StringWriter();
            final JsonGenerator generator = JsonBinding.factory.createGenerator(sw);
            Document eb = (Document) obj;
            Template tmpl = Strings.isNullOrEmpty(templateName) ? null : eb._docType().templates.get(templateName.toUpperCase());
            toJson(obj, tmpl, generator, null, null);
            generator.flush();
            return sw.toString();
        } catch (IOException e) {
            throw new JavaExecutionException(e);
        }
    }

    public final String toJsonString(final Object obj, Template template, DocumentAccessActionsRights rights) {
        try {
            final StringWriter sw = new StringWriter();
            final JsonGenerator generator = JsonBinding.factory.createGenerator(sw);
            toJson(obj, template, generator, null, null);
            generator.flush();
            return sw.toString();
        } catch (IOException e) {
            throw new JavaExecutionException(e);
        }
    }

    public static class TemplateName extends Template {
        public TemplateName(String templateName) {
            this.name = templateName;
            this.modeMask = VIEW_MODE;
        }
    }

    public final void toJson(final Object obj, String templateName, final JsonGenerator generator, DocumentAccessActionsRights rights, BitArray mask) {
        if (obj instanceof Document) {
            Document eb = (Document) obj;
            Template tmpl = Strings.isNullOrEmpty(templateName) ? null : eb._docType().templates.get(templateName.toUpperCase());
            toJson(eb, tmpl, generator, rights, mask);
        } else
            toJson(obj, Strings.isNullOrEmpty(templateName) ? null : new TemplateName(templateName), generator, rights, mask);
    }

    public final void toJson(final Object obj, Template template, final JsonGenerator generator, DocumentAccessActionsRights rights, BitArray mask) {
        toJson(obj, template, generator, null, template != null ? template.modeMask : VIEW_MODE, rights, mask);
    }

    public final void toJson(final Object obj, final JsonGenerator generator, BitArray mask) {
        toJson(obj, (Template) null, generator, null, VIEW_MODE, null, mask);
    }

    public final void toJson(final Object obj, String templateName, final JsonGenerator generator, Stack<String> loopDetectionStack, int mode, DocumentAccessActionsRights rights, BitArray mask) {
        if (obj instanceof Document) {
            Document eb = (Document) obj;
            Template tmpl = Strings.isNullOrEmpty(templateName) ? null : eb._docType().templates.get(templateName.toUpperCase());
            toJson(obj, tmpl, generator, loopDetectionStack, mode, rights, mask);
        } else
            toJson(obj, Strings.isNullOrEmpty(templateName) ? null : new TemplateName(templateName), generator, loopDetectionStack, mode, rights, mask);
    }

    public final void toJson(final Object obj, Template template, final JsonGenerator generator, Stack<String> loopDetectionStack, int mode, DocumentAccessActionsRights rights, BitArray mask) {

        checkNotNull(obj);
        checkNotNull(generator);
        checkArgument(rights == null || doc != null, "Rights are only applicable to IDocument interface implementations.");

        if (doc != null)
            checkArgument(template == null || template.document == doc);
// TODO: template might be template of
//        else
//            checkArgument(template == null || template instanceof TemplateName);

        // process ID template in very special way
        if (obj instanceof PersistentDocument && template != null && EnumUtil.isEqual(DocflowConfig.BuiltInTemplates.ID, template.name)) {
            final JPABase d = (JPABase) obj;
            try {
                if (d.isPersistent())
                    generator.writeString(doc.name + "@" + recordAccessor.getId(d));
                else
                    generator.writeNull();
            } catch (IOException e) {
                throw new JavaExecutionException(e);
            }
            return;
        }

        if (obj instanceof Document) {

            if (rights == null)
                rights = RightsCalculator.instance.calculate((Document) obj, CurrentUser.getInstance());

            if (rights == null || !rights.actionsMask.get(DocflowConfig.ImplicitActions.RETRIEVE.index)) {
                try {
                    generator.writeString(String.format("User has no access to doc: %s", obj.toString()));
                } catch (IOException e) {
                    throw new JavaExecutionException(e);
                }
                return;
            }

            if (doc.calculateMethod != null) {
                final BitArray calculatedMask = doc.calculatedFieldsMask.copy();
                calculatedMask.intersect(rights.viewMask);
                if (template != null)
                    calculatedMask.intersect(template.fieldsMask);
                if (!calculatedMask.isEmpty())
                    try {
                        doc.calculateMethod.invoke(null, obj, calculatedMask, rights);
                    } catch (IllegalAccessException e) {
                        throw new JavaExecutionException(e);
                    } catch (InvocationTargetException e) {
                        throw new JavaExecutionException(e.getCause());
                    }
            }
        }

        boolean toLoopDetectionStack = obj instanceof Document;
        if (toLoopDetectionStack) {
            if (loopDetectionStack == null)
                loopDetectionStack = new Stack<String>();
            loopDetectionStack.add(((Document) obj)._fullId());
        }

        if (mode != 0 && doc != null)
            toJsonObjectWithMode(obj, template, mode, generator, loopDetectionStack, rights, mask);
        else
            try {
                generator.writeStartObject();
                if (doc != null) {
                    if (rights == null)
                        rights = RightsCalculator.instance.calculate((Document) obj, CurrentUser.getInstance());
                    final BitArray viewMask = rights.viewMask.copy();
                    if (mask != null)
                        viewMask.intersect(mask);
                    toJsonWithRightsManagement(obj, template, mode, generator, loopDetectionStack, rights, viewMask);
                } else
                    noRightsManagmentBinding(obj, template, mode, generator, loopDetectionStack);
                generator.writeEndObject();
            } catch (IOException e) {
                throw new JavaExecutionException(e);
            }

        if (toLoopDetectionStack)
            loopDetectionStack.pop();
    }

    /**
     * Separate updatable fields to _u property in Json.
     */
    public static final int GENERATE__U = 1 << 0;
    /**
     * Output actions to $a property in Json.
     */
    public static final int GENERATE_$A = 1 << 1;
    /**
     * Output relations to $r property in Json.
     */
    public static final int GENERATE_$R = 1 << 2;
    /**
     * Lists (structure) elements templates.
     */
    public static final int GENERATE_$N = 1 << 3;
    public static final int _U_FIELD = 1 << 31;

    /**
     * Generate light version of object.  Inclulde all implicit fields.
     */
    public static final int GENERATE_FULL_JSON = 1 << 30;

    /**
     * Generate light version of object.  Exclude implicit fields as default.
     */
    public static final int GENERATE_LIGHT_JSON = 1 << 29;

    /**
     * Do not report that user has no access to given document.  Just skip this element in Json.  It's might be usefull for a quick reporting code
     */
    public static final int SILENT_SKIP_INACCESSIBLE_DOCS = 1 << 28;

    /**
     * Tells that object is JsonTypeBinder sample object, which is disconnected from Hibernate and should be considered as Document in new state.
     */
    public static final int SAMPLE_OBJECT = 1 << 27;

    public static final int VIEW_MODE = 0;
    public static final int EDIT_MODE = GENERATE_$A | GENERATE_$R | GENERATE__U | GENERATE_$N;

    public static int processMParam(String value) {
        int r = VIEW_MODE;
        if (!Strings.isNullOrEmpty(value))
            loopByChar:
                    for (int i = 0; i < value.length(); i++)
                        switch (value.charAt(i)) {
                            case '*':
                                r = EDIT_MODE;
                                break loopByChar;
                            case 'A':
                            case 'a':
                                r |= GENERATE_$A;
                                break;
                            case 'R':
                            case 'r':
                                r |= GENERATE_$R;
                                break;
                            case 'U':
                            case 'u':
                                r |= GENERATE__U;
                                break;
                            case 'N':
                            case 'n':
                                r |= GENERATE_$N;
                                break;
                            case 'L':
                            case 'l':
                                r |= GENERATE_LIGHT_JSON;
                                break;
                            case 'F':
                            case 'f':
                                r |= GENERATE_FULL_JSON;
                                break;
                        }
        return r;
    }

    private final void toJsonObjectWithMode(final Object obj, Template template, int mode, final JsonGenerator generator, Stack<String> stack, DocumentAccessActionsRights rights, BitArray mask) {
        checkNotNull(doc);

        boolean isNewState = (mode & SAMPLE_OBJECT) != 0;
        if (!isNewState && obj instanceof Document)
            try {
                isNewState = !((JPABase) obj).isPersistent();
            } catch (Exception e) {
                throw new JavaExecutionException(e);
            }

        final BitArray viewMask = rights.viewMask.copy(); // view fields without editable fields
        final BitArray updateMask = rights.updateMask.copy(); // editable fields only

        if ((mode & GENERATE__U) == 0 || (!rights.actionsMask.get(DocflowConfig.ImplicitActions.UPDATE.index) && !isNewState)) { // view only
            if (mask != null)
                viewMask.intersect(mask);
            if (template != null)
                viewMask.intersect(template.fieldsMask);
            updateMask.clear();

        } else { // editable
            viewMask.subtract(rights.updateMask);
            if (mask != null) {
                viewMask.intersect(mask);
                updateMask.intersect(mask);
            }
            if (template != null) {
                viewMask.intersect(template.fieldsMask);
                updateMask.intersect(template.fieldsMask);
            }
        }

        try {
            generator.writeStartObject();

            // view fields
            toJsonWithRightsManagement(obj, template, mode, generator, stack, rights, viewMask);

            // editable fields
            if ((mode & GENERATE__U) != 0) {
                generator.writeFieldName("_u");
                generator.writeStartObject();
                toJsonWithRightsManagement(obj, template, mode | _U_FIELD, generator, stack, rights, updateMask);
                generator.writeEndObject();
            }

            // allowed actions
            if ((mode & GENERATE_$A) != 0 && obj instanceof Document) {
                generator.writeFieldName("$a");
                generator.writeStartObject();
                final BitArray.EnumTrueValues actionsEnum = rights.actionsMask.getEnumTrueValues();
                int actionIndex;
                while ((actionIndex = actionsEnum.next()) != -1) {
                    if (isNewState) {
                        if (actionIndex == DocflowConfig.ImplicitActions.UPDATE.index)
                            continue;
                        if (actionIndex == DocflowConfig.ImplicitActions.DELETE.index)
                            continue;
                    } else {
                        if (actionIndex == DocflowConfig.ImplicitActions.CREATE.index)
                            continue;
                        if (actionIndex == DocflowConfig.ImplicitActions.UPDATE.index && updateMask.isEmpty())
                            continue;
                    }
                    final Action action = doc.actionsArray[actionIndex];
                    if (action.display)
                        generator.writeBooleanField(action.name, true);
                }
                generator.writeEndObject();
            }

            // elements templates
            if ((mode & GENERATE_$N) != 0) {
                boolean nStarted = false;
                BitArray nmask = updateMask;
                nmask.intersect(structureLevelMask);
                final BitArray.EnumTrueValues en = nmask.getEnumTrueValues();
                int i;
                while ((i = en.next()) != -1) {
                    final FieldAccessor accessor = docAccessors[i];
                    try {
                        if (accessor instanceof SubtableBinder) {
                            final SubtableBinder subtableBinder = (SubtableBinder) accessor;
                            if (!nStarted) {
                                nStarted = true;
                                generator.writeFieldName("$n");
                                generator.writeStartObject();
                            }
                            final JsonTypeBinder binder = subtableBinder.typeBinder;
                            generator.writeFieldName(subtableBinder.fldName);
                            binder.toJsonObjectWithMode(binder.sample, template, (mode & ~(GENERATE_$A | GENERATE_$R | GENERATE_$N)) | SAMPLE_OBJECT, generator, stack, rights, null);
                        }
                    } catch (Exception e) {
                        throw new JavaExecutionException(String.format("Failed on field '%s'", accessor.fldName), e);
                    }
                }
                if (nStarted)
                    generator.writeEndObject();
            }

            generator.writeEndObject();
        } catch (IOException e) {
            throw new JavaExecutionException(e);
        }
    }

    private void toJsonWithRightsManagement(Object obj, Template template, int mode, JsonGenerator generator, Stack<String> stack, DocumentAccessActionsRights rights, BitArray mask) {

        final BitArray levelMask = (mask != null) ? mask.copy() : structureLevelMask.copy();
        if (mask != null)
            levelMask.intersect(structureLevelMask);
        if (template != null)
            levelMask.intersect(template.fieldsMask);

        final BitArray.EnumTrueValues en = levelMask.getEnumTrueValues();
        int i;
        while ((i = en.next()) != -1) {
            final FieldAccessor accessor = docAccessors[i];
            try {
                accessor.copyToJson(obj, template != null ? template.templateByField[i] : null, generator, stack, mode, rights, mask);
            } catch (Exception e) {
                throw new JavaExecutionException(String.format("Failed on field '%s'", accessor.fldName), e);
            }
        }
    }

    private void noRightsManagmentBinding(Object obj, Template template, int mode, JsonGenerator generator, Stack<String> stack) {
        List<FieldAccessor> accessors = fieldsAccessors;
        for (int i = 0; i < accessors.size(); i++) {
            FieldAccessor accessor = accessors.get(i);
            try {
                accessor.copyToJson(obj, template, generator, stack, mode, null, null);
            } catch (Exception e) {
                throw new JavaExecutionException(String.format("Failed on field '%s'", accessor.fldName), e);
            }
        }
    }

    private static boolean isObjectAlreadyInStack(Stack<String> stack, Document doc) {
        String docId = doc._fullId();
        for (int i = 0; i < stack.size(); i++) {
            if (stack.get(i).equals(docId))
                return true;
        }
        return false;
    }
}
