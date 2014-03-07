package code.jsonBinding.binders.doc;

import code.controlflow.Result;
import code.docflow.action.DocumentUpdate;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.jsonBinding.JsonTypeBinder;
import code.jsonBinding.annotations.doc.JsonSilentlySkipInaccessibleObjects;
import code.jsonBinding.binders.type.TypeBinder;
import code.types.PolymorphicRef;
import code.utils.BitArray;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Stack;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class CollectionBinder extends JsonTypeBinder.FieldAccessor {

    final TypeBinder valueBinder;
    final Template newTemplate;
    final boolean silentlySkipInaccessibleObjects;

    public CollectionBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
        valueBinder = TypeBinder.factory.get(fld);
        newTemplate = RefBinder.processJsonTemplate(fld);
        silentlySkipInaccessibleObjects = fld.isAnnotationPresent(JsonSilentlySkipInaccessibleObjects.class);
    }

    public void copyFromJson(final Object obj, final JsonNode node,
                             DocumentAccessActionsRights rights, BitArray mask, final String fldPrefix,
                             DocumentUpdate update, PolymorphicRef docId, Object outerStructure, final Result result) throws Exception {

        Preconditions.checkState(false, "Collection binder support rending to Json only!");

    }

    public void copyToJson(Object obj, Template template, JsonGenerator generator, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {

        if (silentlySkipInaccessibleObjects)
            mode |= JsonTypeBinder.SILENT_SKIP_INACCESSIBLE_DOCS;

        final Object rec = getter.invoke(obj);

        if (rec == null)
            generator.writeNullField(fldName);
        else {
            generator.writeFieldName(fldName);
            valueBinder.copyToJson(getter.invoke(obj), newTemplate != null ? newTemplate : template, generator, stack, mode);
        }
    }
}