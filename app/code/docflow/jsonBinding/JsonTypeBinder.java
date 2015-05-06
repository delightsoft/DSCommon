package code.docflow.jsonBinding;

import code.docflow.DocflowConfig;
import code.docflow.action.DocumentUpdateImpl;
import code.docflow.compiler.enums.BuiltInFields;
import code.docflow.compiler.enums.BuiltInTemplates;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.controlflow.Result;
import code.docflow.docs.Document;
import code.docflow.docs.DocumentVersioned;
import code.docflow.jsonBinding.annotations.doc.JsonContains;
import code.docflow.jsonBinding.annotations.doc.JsonPartOfStructure;
import code.docflow.jsonBinding.annotations.field.*;
import code.docflow.jsonBinding.binders.doc.*;
import code.docflow.jsonBinding.binders.field.*;
import code.docflow.jsonBinding.binders.time.*;
import code.docflow.model.Action;
import code.docflow.model.DocType;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.types.DocumentRef;
import code.docflow.users.CurrentUser;
import code.docflow.utils.*;
import code.docflow.yaml.YamlMessages;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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

        public boolean nullable;

        public boolean derived;

        public void setField(code.docflow.model.Field field) {
            index = field.index;
            required = field.required;
            nullable = field.nullable;
            derived = field.derived;
        }

        protected FieldAccessor(Field fld, Method getter, Method setter, String fldName) {
            this.fld = fld;
            this.getter = getter;
            this.setter = setter;
            this.fldName = fldName;
        }

        public void copyFromJson(Object obj, JsonNode node, DocumentAccessActionsRights rights, BitArray mask, String fldPrefix, DocumentUpdateImpl update, DocumentRef docId, Object outerStructure, Result result)
                throws Exception {
            // nothing
        }

        public void copyToJson(Object obj, Template template, ObjectNode out, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
            // nothing
        }
    }

    ArrayList<FieldAccessor> fieldsAccessors = new ArrayList<FieldAccessor>();
    TreeMap<String, FieldAccessor> fieldsAccessorsIndex = new TreeMap<String, FieldAccessor>();

    public DocType docType;
    FieldAccessor[] docAccessors;

    /**
     * Sample instance, to be used to generate JSON object or structures prototypes.
     */
    public Object sample;

    /**
     * Mask that contains only fields that belong to this level of structure.  Fields located in substrcutres
     * marked false.
     */
    BitArray structureLevelMask;

    BitArray levelSystemFieldsMask;

    BitArray levelRequiredMask;

    private JsonTypeBinder(final Class<?> type) {
        this.proxyType = type;
        final String typeName = type.getName();
        // Note: different version of javassis (they linked to specific play versions) have different postfix for a classnames
        final int proxyPostfix = typeName.indexOf(Play.version.compareTo("1.3") > -1 ? "_$$_jvst" : "_$$_javassist_");
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

        docType = DocflowConfig.getDocumentTypeByClass(type);
        SubrecordAccessor subrecordAccessor = isJsonPartOfStructure ? SubrecordAccessor.factory.get(type) : null;
        recordAccessor = !isJsonPartOfStructure && isEntity ? RecordAccessor.factory.get(type) : null;

        // assign stub accessors for utility fields
        if (subrecordAccessor != null) {
            addAccessor(new StubBinder(subrecordAccessor.fldFK, BuiltInFields.FK.toString()));
            addAccessor(new StubBinder(subrecordAccessor.fldI, BuiltInFields.I.toString()));
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

                    // Special accessors for fields marked by @Id and @Version
                    if (recordAccessor != null) {
                        if (processId(fld, getter, setter, fldName, BuiltInFields.ID.toString(), false)) continue;
                        if (processRev(fld, getter, setter, fldName)) continue;
                        if (!docType.report && docType.states.size() > 2)
                            if (processState(fld, getter, setter, fldName)) continue;
                        if (processCreateModifiedDeleted(fld, getter, setter, fldName)) continue;
                    } else if (subrecordAccessor != null) {
                        if (processId(fld, getter, setter, fldName, BuiltInFields.SUBRECORD_ID.toString(), true)) continue;
                        if (EnumUtil.isEqual(BuiltInFields.I, fldName)) continue;
                        if (EnumUtil.isEqual(BuiltInFields.FK, fldName)) continue;
                    }

                    boolean nullable = !fld.isAnnotationPresent(JsonNull.class);

                    switch (PrimitiveType.get(fldType)) {
                        case StringType:
                            if (fld.getAnnotation(JsonJson.class) != null)
                                addAccessor(new JsonBinder(fld, getter, setter, fldName));
                            else if (fld.getAnnotation(JsonJsonText.class) != null)
                                addAccessor(new JsonTextBinder(fld, getter, setter, fldName));
                            else if (fld.getAnnotation(JsonResult.class) != null)
                                // Note: Output to Json only binder
                                addAccessor(new ResultBinder(fld, getter, setter, fldName));
                            else if (fld.getAnnotation(JsonPassword.class) != null) {
                                PasswordBinder passwordBinder = new PasswordBinder(fld, getter, setter, fldName);
                                passwordBinder.nullable = nullable;
                                addAccessor(passwordBinder);
                            } else {
                                StringBinder stringBinder = new StringBinder(fld, getter, setter, fldName);
                                stringBinder.nullable = nullable;
                                addAccessor(stringBinder);
                            }
                            break;
                        case booleanType:
                            addAccessor(new BooleanBinder(fld, getter, setter, fldName));
                            break;
                        case BooleanType:
                            BooleanBinder boolBinder = new BooleanBinder(fld, getter, setter, fldName);
                            boolBinder.nullable = nullable;
                            addAccessor(boolBinder);
                            break;
                        case intType:
                            addAccessor(new IntegerBinder(fld, getter, setter, fldName));
                            break;
                        case IntegerType:
                            IntegerBinder intBinder = new IntegerBinder(fld, getter, setter, fldName);
                            intBinder.nullable = nullable;
                            addAccessor(intBinder);
                            break;
                        case longType:
                            addAccessor(new LongBinder(fld, getter, setter, fldName));
                            break;
                        case LongType:
                            LongBinder longBinder = new LongBinder(fld, getter, setter, fldName);
                            longBinder.nullable = nullable;
                            addAccessor(longBinder);
                            break;
                        case doubleType:
                            addAccessor(new DoubleBinder(fld, getter, setter, fldName));
                            break;
                        case DoubleType:
                            DoubleBinder doubleBinder = new DoubleBinder(fld, getter, setter, fldName);
                            doubleBinder.nullable = nullable;
                            addAccessor(doubleBinder);
                            break;
                        case DateType:
                            DateBinder dateBinder = new DateBinder(fld, getter, setter, fldName);
                            dateBinder.nullable = nullable;
                            addAccessor(dateBinder);
                            break;
                        case TimeType:
                            TimeBinder timeBinder = new TimeBinder(fld, getter, setter, fldName);
                            timeBinder.nullable = nullable;
                            addAccessor(timeBinder);
                            break;
                        case DateTimeType:
                            DateTimeBinder dateTimeBinder = new DateTimeBinder(fld, getter, setter, fldName);
                            dateTimeBinder.nullable = nullable;
                            addAccessor(dateTimeBinder);
                            break;
                        case LocalDateTimeType:
                            LocalDateTimeBinder localDateTimeBinder = new LocalDateTimeBinder(fld, getter, setter, fldName);
                            localDateTimeBinder.nullable = nullable;
                            addAccessor(localDateTimeBinder);
                            break;
                        case PeriodType:
                            PeriodBinder period = new PeriodBinder(fld, getter, setter, fldName);
                            period.nullable = nullable;
                            addAccessor(period);
                            break;
                        case IntervalType:
                            IntervalBinder intervalBinder = new IntervalBinder(fld, getter, setter, fldName);
                            intervalBinder.nullable = nullable;
                            addAccessor(intervalBinder);
                            break;
                        case DurationType:
                            DurationBinder duration = new DurationBinder(fld, getter, setter, fldName);
                            duration.nullable = nullable;
                            addAccessor(duration);
                            break;
                        case UUIDType:
                            UUIDBinder uuidBinder = new UUIDBinder(fld, getter, setter, fldName);
                            uuidBinder.nullable = nullable;
                            addAccessor(uuidBinder);
                            break;
                        case EnumType:
                            EnumBinder enumBinder = new EnumBinder(fld, getter, setter, fldName);
                            enumBinder.nullable = nullable;
                            addAccessor(enumBinder);
                            break;
                        case NotPrimitiveOrPrimitiveWrapper:
                            // Related entites

                            final boolean isJsonContains = fld.getAnnotation(JsonContains.class) != null;

                            if (isJsonContains) {
                                SubtableBinder subtableBinder = new SubtableBinder(fld, getter, setter, fldName);
                                subtableBinder.nullable = nullable;
                                addAccessor(subtableBinder);
                                break;
                            }

                            if (Document.class.isAssignableFrom(fldType)) {
                                RefBinderStrict refBinder = new RefBinderStrict(fld, getter, setter, fldName);
                                refBinder.nullable = nullable;
                                addAccessor(refBinder);
                                break;
                            }

                            if (DocumentRef.class.isAssignableFrom(fldType)) {
                                RefBinderPolymorphic polyRefBinder = new RefBinderPolymorphic(fld, getter, setter, fldName);
                                polyRefBinder.nullable = nullable;
                                addAccessor(polyRefBinder);
                                break;
                            }

                            if (JsonNode.class.isAssignableFrom(fldType)) {
                                JsonNodeBinder jsonNodeBinder = new JsonNodeBinder(fld, getter, setter, fldName);
                                jsonNodeBinder.nullable = nullable;
                                addAccessor(jsonNodeBinder);
                                break;
                            }

                            if (List.class.isAssignableFrom(fldType) || Map.class.isAssignableFrom(fldType) || Multimap.class.isAssignableFrom(fldType)) {
                                // Note: Output to Json only binder
                                addAccessor(new CollectionBinder(fld, getter, setter, fldName));
                                break;
                            }

                            StructureBinder structureBinder = new StructureBinder(fld, getter, setter, fldName);
                            structureBinder.nullable = nullable;
                            addAccessor(structureBinder);
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
            throw new UnexpectedException(String.format("Failed on field '%s'.", fld.getName()), e);
        }

        if (docType == null)
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
                if (isEntity)
                    JPA.em().detach(sample);
            } catch (InstantiationException e) {
                throw new UnexpectedException(e);
            } catch (IllegalAccessException e) {
                throw new UnexpectedException(e);
            }
    }

    public void linkDocumentFieldsToFieldsAccessors(Result result) {
        docAccessors = new FieldAccessor[docType.allFields.size()];
        structureLevelMask = new BitArray(docType.allFields.size());
        structureLevelMask.inverse(); // make all true
        levelSystemFieldsMask = new BitArray(docType.allFields.size());

        final code.docflow.model.Field idFld = docType.fieldByFullname.get(BuiltInFields.ID.name());
        if (idFld != null)
            levelSystemFieldsMask.set(idFld.index, true);

        final code.docflow.model.Field revFld = docType.fieldByFullname.get(BuiltInFields.REV.name());
        if (revFld != null)
            levelSystemFieldsMask.set(revFld.index, true);

        final code.docflow.model.Field subjFld = docType.fieldByFullname.get(BuiltInFields.SUBJ.name());
        if (subjFld != null)
            levelSystemFieldsMask.set(subjFld.index, true);

        linkDocumentFieldsToFieldsAccessors(this, "", result);

        // find all fields what do not have accessors
        for (int i = 0; i < docAccessors.length; i++) {
            FieldAccessor docAccessor = docAccessors[i];
            if (docAccessor == null)
                result.addMsg(YamlMessages.error_DocumentModelFieldNotFoundInClass, docType.name, docType.allFields.get(i).fullname, type.getName());
        }
    }

    private void linkDocumentFieldsToFieldsAccessors(JsonTypeBinder structureBinder, String namePrefix, Result result) {
        structureBinder.levelRequiredMask = new BitArray(docType.allFields.size());
        final ArrayList<FieldAccessor> accessors = structureBinder.fieldsAccessors;
        for (int i = 0; i < accessors.size(); i++) {
            FieldAccessor fieldAccessor = accessors.get(i);
            if (fieldAccessor.required)
                structureBinder.levelRequiredMask.set(i, true);
            final String fn = namePrefix + fieldAccessor.fldName;
            final code.docflow.model.Field field = docType.fieldByFullname.get(fn.toUpperCase());
            if (field == null) {
                result.addMsg(YamlMessages.error_DocumentClassFieldNotDefinedInModel, docType.name, fn, type.getName(), fieldAccessor.fldName);
                continue;
            }

            docAccessors[field.index] = fieldAccessor;
            fieldAccessor.setField(field);

            // it's a substructure

            JsonTypeBinder typeBinder = fieldAccessor instanceof SubtableBinder ? ((SubtableBinder) fieldAccessor).typeBinder : null;

            if (typeBinder == null) {
                typeBinder = fieldAccessor instanceof StructureBinder ? ((StructureBinder) fieldAccessor).typeBinder : null;
            }

            if (typeBinder != null) {
                typeBinder.docType = docType;
                typeBinder.docAccessors = docAccessors;
                typeBinder.levelSystemFieldsMask = new BitArray(docType.allFields.size());

                final code.docflow.model.Field iFld = docType.fieldByFullname.get((namePrefix + fieldAccessor.fldName + "." + BuiltInFields.I.name()).toUpperCase());
                if (iFld != null)
                    typeBinder.levelSystemFieldsMask.set(iFld.index, true);

                final BitArray m = new BitArray(docType.allFields.size());
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
            throw new UnexpectedException(e);
        } catch (SecurityException e) {
            throw new UnexpectedException(e);
        }
    }

    private Method getGetterMethod(String fldName, Class fldType) {
        final String name = "get" + fldName.substring(0, 1).toUpperCase() + fldName.substring(1);
        try {
            return proxyType.getMethod(name);
        } catch (NoSuchMethodException e) {
            throw new UnexpectedException(e);
        } catch (SecurityException e) {
            throw new UnexpectedException(e);
        }
    }

    private boolean processId(final Field fld, Method getter, Method setter, final String fldName, final String fixedFieldName, final boolean skipInHistory) {
        if (EnumUtil.isEqual(BuiltInFields.ID, fldName)) {
            addAccessor(new FieldAccessor(fld, getter, setter, fldName) {
                public void copyToJson(Object obj, Template template, ObjectNode out, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
                    if (!skipInHistory || (template != null && EnumUtil.isEqual(BuiltInTemplates.HISTORY, template.name)))
                        if (obj instanceof Document)
                            out.put(fixedFieldName, ((Document) obj)._fullId());
                        else
                            out.put(fixedFieldName, (Long) getter.invoke(obj));
                }
            });
            return true;
        }
        return false;
    }

    private boolean processRev(final Field fld, Method getter, Method setter, final String fldName) {
        if (EnumUtil.isEqual(BuiltInFields.REV, fldName)) {
            addAccessor(new FieldAccessor(fld, getter, setter, fldName) {
                public void copyToJson(Object obj, Template template, ObjectNode out, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
                    if (((Document) obj)._isPersisted())
                        out.put(fldName, (Integer) getter.invoke(obj));
                }
            });
            return true;
        }
        return false;
    }

    private boolean processState(final Field fld, Method getter, Method setter, final String fldName) {
        if (EnumUtil.isEqual(BuiltInFields.STATE, fldName)) {
            addAccessor(new StateBinder(fld, getter, setter, fldName));
            return true;
        }
        return false;
    }

    private boolean processCreateModifiedDeleted(final Field fld, Method getter, Method setter, final String fldName) {
        if (EnumUtil.isEqual(BuiltInFields.CREATED, fldName) || EnumUtil.isEqual(BuiltInFields.MODIFIED, fldName)) {
            addAccessor(new FieldAccessor(fld, getter, setter, fldName) {
                public void copyToJson(Object obj, Template template, ObjectNode out, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
                    if (((Document) obj)._isPersisted()) {
                        final DateTime v = (DateTime) getter.invoke(obj);
                        out.put(fldName, v.getMillis());
                    }
                }
            });
            return true;
        }
        if (EnumUtil.isEqual(BuiltInFields.DELETED, fldName)) {
            addAccessor(new FieldAccessor(fld, getter, setter, fldName) {
                public void copyToJson(Object obj, Template template, ObjectNode out, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
                    if (((Document) obj)._isPersisted()) {
                        final Boolean val = (Boolean) getter.invoke(obj);
                        if (val)
                            out.put(fldName, val);
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
     * @param outerStructure
     */
    public void fromJson(final Object obj, final ObjectNode objectNode, DocumentAccessActionsRights rights,
                         BitArray mask, final String fldPrefix,
                         DocumentUpdateImpl update, DocumentRef docId, Object outerStructure, final Result result) {

        checkNotNull(obj);
        checkNotNull(objectNode);
        checkArgument(rights == null || docType != null, "Rights are only applicable to IDocument interface implementations.");

        if (outerStructure == null)
            outerStructure = obj;

        if (docType != null) {
            if (rights == null)
                rights = RightsCalculator.instance.calculate((Document) obj, CurrentUser.getInstance());
            fromJsonWithRightsManagement(obj, objectNode, rights, mask, fldPrefix, update, docId, isEmbeddable ? outerStructure : obj, result);
        } else
            noRightsManagmentFromJson(obj, objectNode, fldPrefix, update, docId, isEmbeddable ? outerStructure : obj, result);
    }

    private void fromJsonWithRightsManagement(Object obj, ObjectNode objectNode, DocumentAccessActionsRights rights,
                                              BitArray mask, String fldPrefix,
                                              DocumentUpdateImpl update, DocumentRef docId, Object outerStructure, Result result) {
        final String fp = (fldPrefix == null) ? "" : fldPrefix;

        if (mask == null)
            mask = rights.updateMask;
        else
            mask.intersect(rights.updateMask);

        BitArray levelUpdateMask = mask.copy();
        levelUpdateMask.intersect(structureLevelMask);

        final BitArray presentFields = levelRequiredMask.copy();
        presentFields.intersect(mask);

        final boolean inActionScope = CurrentUser.getInstance().inActionScope;

        final Iterator<String> keysIter = objectNode.fieldNames();
        while (keysIter.hasNext()) {
            String key = keysIter.next();
            final FieldAccessor fieldAccessor = fieldsAccessorsIndex.get(key);
            if (fieldAccessor == null) {
                result.addMsg(DocflowMessages.error_ValidationFieldUnknown_1, fp + key);
                continue;
            }
            if (levelSystemFieldsMask.get(fieldAccessor.index))
                continue;
            if (!inActionScope && !levelUpdateMask.get(fieldAccessor.index)) {
                result.addMsg(DocflowMessages.error_ValidationFieldUnexpected_1, fp + key);
                continue;
            }
            presentFields.set(fieldAccessor.index, false);
            try {
                fieldAccessor.copyFromJson(obj, objectNode.get(key), rights, mask, fp, update, docId, outerStructure, result);
            } catch (Exception e) {
                throw new UnexpectedException(String.format("Failed on field '%s'", fieldAccessor.fldName), e);
            }
        }
        final BitArray.EnumTrueValues en = presentFields.getEnumTrueValues();
        int i;
        while ((i = en.next()) != -1) {
            final FieldAccessor fieldAccessor = docAccessors[i];
            result.addMsg(DocflowMessages.error_ValidationFieldRequired_1, fp + fieldAccessor.fldName);
        }
    }

    private void noRightsManagmentFromJson(Object obj, ObjectNode objectNode, String fldPrefix,
                                           DocumentUpdateImpl update, DocumentRef docId, Object outerStructure, Result result) {
        final String fp = (fldPrefix == null) ? "" : fldPrefix;
        for (int i = 0; i < fieldsAccessors.size(); i++) {
            FieldAccessor accessor = fieldsAccessors.get(i);
            final JsonNode node = objectNode.get(accessor.fldName);
            if (node != null)
                try {
                    accessor.copyFromJson(obj, node, null, null, fp, update, docId, outerStructure, result);
                } catch (Exception e) {
                    throw new UnexpectedException(String.format("Failed on field '%s'", accessor.fldName), e);
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
            generator.writeTree(toJson(obj));
            generator.flush();
            return sw.toString();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    public final String toJsonString(final Object obj, String templateName, DocumentAccessActionsRights rights) {
        try {
            final StringWriter sw = new StringWriter();
            final JsonGenerator generator = JsonBinding.factory.createGenerator(sw);
            Document eb = (Document) obj;
            Template tmpl = Strings.isNullOrEmpty(templateName) ? null : eb._docType().templates.get(templateName.toUpperCase());
            generator.writeTree(toJson(obj, tmpl, null, null));
            generator.flush();
            return sw.toString();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    public final String toJsonString(final Object obj, Template template, DocumentAccessActionsRights rights) {
        try {
            final StringWriter sw = new StringWriter();
            final JsonGenerator generator = JsonBinding.factory.createGenerator(sw);
            generator.writeTree(toJson(obj, template, null, null));
            generator.flush();
            return sw.toString();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    public static class TemplateName extends Template {
        public TemplateName(String templateName) {
            this.name = templateName;
            this.modeMask = VIEW_MODE;
        }
    }

    public final JsonNode toJson(final Object obj) {
        return toJson(obj, BuiltInTemplates.LIST.toString());
    }

    public final JsonNode toJson(final Object obj, String templateName) {
        return toJson(obj, templateName, null, null);
    }

    public final JsonNode toJson(final Object obj, String templateName, DocumentAccessActionsRights rights, BitArray mask) {
        if (obj instanceof Document) {
            Document eb = (Document) obj;
            Template tmpl = Strings.isNullOrEmpty(templateName) ? null : eb._docType().templates.get(templateName.toUpperCase());
            return toJson(eb, tmpl, rights, mask);
        } else
            return toJson(obj, Strings.isNullOrEmpty(templateName) ? null : new TemplateName(templateName), rights, mask);
    }

    public final JsonNode toJson(final Object obj, Template template, final DocumentAccessActionsRights rights, BitArray mask) {
        return toJson(obj, template, null, template != null ? template.modeMask : VIEW_MODE, rights, mask);
    }

    public final JsonNode toJson(final Object obj, String templateName, Stack<String> loopDetectionStack, int mode, DocumentAccessActionsRights rights, BitArray mask) {
        if (obj instanceof Document) {
            Document eb = (Document) obj;
            Template tmpl = Strings.isNullOrEmpty(templateName) ? null : eb._docType().templates.get(templateName.toUpperCase());
            return toJson(obj, tmpl, loopDetectionStack, mode, rights, mask);
        } else
            return toJson(obj, Strings.isNullOrEmpty(templateName) ? null : new TemplateName(templateName), loopDetectionStack, mode, rights, mask);
    }

    public final JsonNode toJson(final Object obj, Template template, Stack<String> loopDetectionStack, int mode, DocumentAccessActionsRights rights, BitArray mask) {

        checkNotNull(obj);
        checkArgument(rights == null || docType != null, "Rights are only applicable to IDocument interface implementations.");

        if (docType != null)
            checkArgument(template == null || template.document == docType);

        // process ID template in very special way
        if (obj instanceof DocumentVersioned && template != null && EnumUtil.isEqual(BuiltInTemplates.ID, template.name)) {
            final Document doc = (Document) obj;
            if (doc._isPersisted()) {
                return JsonNodeFactory.instance.textNode(doc._fullId());
            } else
                return JsonNodeFactory.instance.nullNode();
        }

        final ObjectNode out = JsonNodeFactory.instance.objectNode();
        if (obj instanceof Document) {

            if (rights == null)
                rights = RightsCalculator.instance.calculate((Document) obj, CurrentUser.getInstance());

            if (rights == null || !rights.actionsMask.get(CrudActions.RETRIEVE.index)) {
                out.put("__no_access", ((Document) obj)._fullId());
                return out;
            }

            if (docType.calculateMethod != null) {
                final BitArray calculatedMask = docType.calculatedFieldsMask.copy();
                calculatedMask.intersect(rights.viewMask);
                if (template != null)
                    calculatedMask.intersect(template.fieldsMask);
                ((Document) obj).calculate(calculatedMask, rights);
            }
        }

        boolean toLoopDetectionStack = obj instanceof Document;
        if (toLoopDetectionStack) {
            if (loopDetectionStack == null)
                loopDetectionStack = new Stack<String>();
            loopDetectionStack.add(((Document) obj)._fullId());
        }

        if (mode != 0 && docType != null)
            toJsonObjectWithMode(obj, template, mode, out, loopDetectionStack, rights, mask);
        else if (docType != null) {
            if (rights == null)
                rights = RightsCalculator.instance.calculate((Document) obj, CurrentUser.getInstance());
            final BitArray viewMask = rights.viewMask.copy();
            if (mask != null)
                viewMask.intersect(mask);
            toJsonWithRightsManagement(obj, template, mode, out, loopDetectionStack, rights, viewMask);
        } else
            noRightsManagmentBinding(obj, template, mode, out, loopDetectionStack);

        if (toLoopDetectionStack)
            loopDetectionStack.pop();

        return out;
    }

    /**
     * Separate updatable fields to _u property in Json.
     */
    public static final int GENERATE__U = 1 << 0;
    /**
     * Output actions to $a property in Json.
     */
    public static final int GENERATE__A = 1 << 1;
    /**
     * Lists (structure) elements templates.
     */
    public static final int GENERATE__N = 1 << 2;
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
    public static final int EDIT_MODE = GENERATE__A | GENERATE__U | GENERATE__N;

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
                                r |= GENERATE__A;
                                break;
                            case 'U':
                            case 'u':
                                r |= GENERATE__U;
                                break;
                            case 'N':
                            case 'n':
                                r |= GENERATE__N;
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

    private final void toJsonObjectWithMode(final Object obj, Template template, int mode, final ObjectNode out, Stack<String> stack, DocumentAccessActionsRights rights, BitArray mask) {
        checkNotNull(docType);

        boolean isNewState = (mode & SAMPLE_OBJECT) != 0;
        if (!isNewState && obj instanceof Document)
            try {
                isNewState = !((Document) obj)._isPersisted();
            } catch (Exception e) {
                throw new UnexpectedException(e);
            }

        final BitArray viewMask = rights.viewMask.copy(); // view fields without editable fields
        final BitArray updateMask = rights.updateMask.copy(); // editable fields only

        if ((mode & GENERATE__U) == 0 || (!rights.actionsMask.get(CrudActions.UPDATE.index) && !isNewState)) { // view only
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

        // view fields
        toJsonWithRightsManagement(obj, template, mode & ~_U_FIELD, out, stack, rights, viewMask);

        // editable fields
        if ((mode & GENERATE__U) != 0) {
            final ObjectNode uNode = JsonNodeFactory.instance.objectNode();
            // Note: View fields could also be a part of updatable subtable or structure - so I pass intact mask
            toJsonWithRightsManagement(obj, template, mode | _U_FIELD, uNode, stack, rights, mask);
            out.put("_u", uNode);
        }

        // allowed actions
        if ((mode & GENERATE__A) != 0 && obj instanceof Document) {
            final ObjectNode aNode = JsonNodeFactory.instance.objectNode();
            final BitArray.EnumTrueValues actionsEnum = rights.actionsMask.getEnumTrueValues();
            int actionIndex;
            while ((actionIndex = actionsEnum.next()) != -1) {
                if (isNewState) {
                    if (actionIndex == CrudActions.UPDATE.index)
                        continue;
                    if (actionIndex == CrudActions.DELETE.index)
                        continue;
                } else {
                    if (actionIndex == CrudActions.CREATE.index)
                        continue;
                    if (actionIndex == CrudActions.UPDATE.index && updateMask.isEmpty())
                        continue;
                }
                final Action action = docType.actionsArray[actionIndex];
                if (action.display)
                    aNode.put(action.name, true);
            }
            out.put("_a", aNode);
        }

        // elements templates
        if ((mode & GENERATE__N) != 0) {
            boolean nStarted = false;
            BitArray nmask = updateMask;
            nmask.intersect(structureLevelMask);
            final BitArray.EnumTrueValues en = nmask.getEnumTrueValues();
            ObjectNode nNode = null;
            int i;
            while ((i = en.next()) != -1) {
                final FieldAccessor accessor = docAccessors[i];
                try {
                    if (accessor instanceof SubtableBinder) {
                        final SubtableBinder subtableBinder = (SubtableBinder) accessor;
                        if (!nStarted) {
                            nStarted = true;
                            nNode = JsonNodeFactory.instance.objectNode();
                        }
                        final JsonTypeBinder binder = subtableBinder.typeBinder;
                        final ObjectNode tmplNode = JsonNodeFactory.instance.objectNode();
                        binder.toJsonObjectWithMode(binder.sample, template, (mode & ~(GENERATE__A | GENERATE__N)) | SAMPLE_OBJECT, tmplNode, stack, rights, null);
                        nNode.put(subtableBinder.fldName, tmplNode);
                    }
                } catch (Exception e) {
                    throw new UnexpectedException(String.format("Failed on field '%s'", accessor.fldName), e);
                }
            }
            if (nNode != null)
                out.put("_n", nNode);
        }
    }

    private void toJsonWithRightsManagement(Object obj, Template template, int mode, ObjectNode out, Stack<String> stack, DocumentAccessActionsRights rights, BitArray mask) {

        final BitArray levelMask = (mask != null) ? mask.copy() : structureLevelMask.copy();
        if (mask != null)
            levelMask.intersect(structureLevelMask);
        if (template != null)
            levelMask.intersect(template.fieldsMask);
        if ((mode & _U_FIELD) != 0)
            levelMask.intersect(rights.updateMask);

        final BitArray.EnumTrueValues en = levelMask.getEnumTrueValues();
        int i;
        while ((i = en.next()) != -1) {
            final FieldAccessor accessor = docAccessors[i];
            try {
                accessor.copyToJson(obj, template != null ? template.templateByField[i] : null, out, stack, mode, rights, mask);
            } catch (Exception e) {
                throw new UnexpectedException(String.format("Failed on field '%s'", accessor.fldName), e);
            }
        }
    }

    private void noRightsManagmentBinding(Object obj, Template template, int mode, ObjectNode out, Stack<String> stack) {
        List<FieldAccessor> accessors = fieldsAccessors;
        for (int i = 0; i < accessors.size(); i++) {
            FieldAccessor accessor = accessors.get(i);
            try {
                accessor.copyToJson(obj, template, out, stack, mode, null, null);
            } catch (Exception e) {
                throw new UnexpectedException(String.format("Failed on field '%s'", accessor.fldName), e);
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
