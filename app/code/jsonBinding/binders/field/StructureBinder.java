package code.jsonBinding.binders.field;

import code.controlflow.Result;
import code.docflow.action.DocumentUpdate;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.jsonBinding.JsonTypeBinder;
import code.jsonBinding.annotations.doc.JsonSilentlySkipInaccessibleObjects;
import code.jsonBinding.binders.doc.RefBinder;
import code.types.PolymorphicRef;
import code.utils.BitArray;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import docflow.DocflowMessages;
import play.exceptions.JavaExecutionException;
import play.exceptions.UnexpectedException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Stack;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class StructureBinder extends JsonTypeBinder.FieldAccessor {

    public final JsonTypeBinder typeBinder;
    final Class<?> type;
    final Object emptyInstance;
    final Template newTemplate;
    final boolean silentlySkipInaccessibleObjects;

    public StructureBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
        this.type = fld.getType();
        try {
            emptyInstance = this.type.newInstance();
        } catch (InstantiationException e) {
            throw new JavaExecutionException(e);
        } catch (IllegalAccessException e) {
            throw new JavaExecutionException(e);
        }
        this.typeBinder = JsonTypeBinder.factory.get(fld.getType());
        newTemplate = RefBinder.processJsonTemplate(fld);
        silentlySkipInaccessibleObjects = fld.isAnnotationPresent(JsonSilentlySkipInaccessibleObjects.class);
    }

    public void copyFromJson(final Object obj, final JsonNode node,
                             DocumentAccessActionsRights rights, BitArray mask, final String fldPrefix,
                             DocumentUpdate update, PolymorphicRef docId, Object outerStructure, final Result result) throws Exception {
        if (node.isNull()) {
            if (getter.invoke(obj) != null) {
                setter.invoke(obj, null);
                if (update != null && update.changesGenerator != null)
                    update.changesGenerator.writeNullField(fldName);
            }
            return;
        }

        if (!node.isObject())
            result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());

        Object v = getter.invoke(obj);
        if (v == null) {
            try {
                v = type.newInstance();
            } catch (InstantiationException e) {
                throw new JavaExecutionException(String.format("Failed instantiate '%1$s' for field '%2$s' in class '%3$s'.",
                        type.getName(), fld.getName(), fld.getDeclaringClass().getName()), e);
            } catch (IllegalAccessException e) {
                throw new JavaExecutionException(String.format("Failed instantiate '%1$s' for field '%2$s' in class '%3$s'.",
                        type.getName(), fld.getName(), fld.getDeclaringClass().getName()), e);
            }
            setter.invoke(obj, v);
        }
        typeBinder.fromJson(v, node, rights, mask, fldPrefix + fldName + ".", update, null, outerStructure, result);
    }

    public void copyToJson(Object obj, Template template, JsonGenerator generator, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {

        if (silentlySkipInaccessibleObjects)
            mode |= JsonTypeBinder.SILENT_SKIP_INACCESSIBLE_DOCS;

        generator.writeFieldName(fldName);
        final Object v = getter.invoke(obj);
        if (v == null)
            if ((mode & JsonTypeBinder.GENERATE__U) != 0) // in case of null, we still expect empty structure in UI logic
                typeBinder.toJson(emptyInstance, template, generator, stack, mode, rights, mask);
            else
                generator.writeNull();
        else
            typeBinder.toJson(v, newTemplate != null ? newTemplate : template, generator, stack, mode, rights, mask);
    }
}
