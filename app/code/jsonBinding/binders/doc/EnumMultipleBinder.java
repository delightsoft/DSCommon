package code.jsonBinding.binders.doc;

import code.controlflow.Result;
import code.docflow.action.DocumentUpdate;
import code.docflow.model.FieldEnum;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.jsonBinding.JsonTypeBinder;
import code.types.PolymorphicRef;
import code.utils.BitArray;
import code.utils.NamesUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Objects;
import docflow.DocflowMessages;
import play.Logger;
import play.exceptions.UnexpectedException;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

/**
 * Binds values defined as enum with annotation @DocflowEnumMultiple, which comes to
 * DB as comma separated string, and the world as Json true-value flags structure (map).
 */
public final class EnumMultipleBinder extends JsonTypeBinder.FieldAccessor {
    public boolean required;
    public final Class fldType;
    public FieldEnum docflowField;

    public EnumMultipleBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
        this.fldType = fld.getType();
        if (fldType != String.class)
            throw new UnexpectedException("Field with annotation @JsonEnumMultiple must be String.");
    }

    @Override
    public void setField(code.docflow.model.Field field) {
        docflowField = (FieldEnum) field;
    }

    public void copyFromJson(Object obj, JsonNode node, DocumentAccessActionsRights rights, BitArray mask, String fldPrefix, DocumentUpdate update, PolymorphicRef docId, Object outerStructure, Result result) throws Exception {

        if (node.isNull()) {
            if (required)
                result.addMsg(DocflowMessages.error_ValidationFieldRequired_1, fldPrefix + fldName);
            else
                setValue(obj, null, update != null ? update.changesGenerator : null);
            return;
        }

        if (!node.isObject()) {
            result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
            return;
        }

        TreeSet<String> values = new TreeSet<String>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> next = fields.next();
            String key = next.getKey();
            JsonNode flag = next.getValue();
            if (!flag.isBoolean()) {
                result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName + "." + key, flag.asText());
                continue;
            }
            // TODO: Turn enum into three-values solution
            Enum anEnum = docflowField.values.get(NamesUtil.wordsToUpperUnderscoreSeparated(key));
            if (anEnum == null) {
                result.addMsg(DocflowMessages.error_ValidationFieldIncorrectEnumValue_2, fldPrefix + fldName, key);
                continue;
            }
            if (flag.booleanValue())
                values.add(anEnum.toString());
        }

        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (sb.length() > 0)
                sb.append(",");
            sb.append(value);
        }
        String v = sb.toString();

        setValue(obj, v, update != null ? update.changesGenerator : null);
    }

    private void setValue(Object obj, String value, JsonGenerator changes) throws IllegalAccessException, InvocationTargetException, IOException {
        final Object t = getter.invoke(obj);
        if (!Objects.equal(value, t)) {
            setter.invoke(obj, value);
            if (changes != null)
                if (value == null)
                    changes.writeNullField(fldName);
                else
                    changes.writeStringField(fldName, value.toString());
        }
    }

    public void copyToJson(Object obj, Template template, JsonGenerator generator, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {
        final String v = (String) getter.invoke(obj);
        if (v == null) {
            generator.writeNullField(fldName);
            return;
        }

        generator.writeFieldName(fldName);
        generator.writeStartObject();

        if ((mode & JsonTypeBinder._U_FIELD) != 0) {
            generator.writeFieldName("_u");
            generator.writeStartObject();
        }

        // TODO: Replace regex.split by own or 3rd party simple split function
        String[] split = v.split(",");
        if (split.length > 1 || split[0].length() > 0) // one empty string array happends when regex splits an empty string
            for (int i = 0; i < split.length; i++) {
                Enum anEnum = docflowField.values.get(split[i].toUpperCase().toUpperCase());
                if (anEnum == null) {
                    Logger.warn("Document '%s': Field '%s': DB record contains obsolete enum value '%s'.",
                            fldType.getDeclaringClass().getSimpleName(), fldName, split[i]);
                    continue;
                }
                generator.writeBooleanField(anEnum.toString(), true);
            }

        if ((mode & JsonTypeBinder._U_FIELD) != 0)
            generator.writeEndObject();

        generator.writeEndObject();
    }
}
