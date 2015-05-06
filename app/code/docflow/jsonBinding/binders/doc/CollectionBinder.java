package code.docflow.jsonBinding.binders.doc;

import code.docflow.action.DocumentUpdateImpl;
import code.docflow.controlflow.Result;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.jsonBinding.annotations.doc.JsonSilentlySkipInaccessibleObjects;
import code.docflow.jsonBinding.binders.type.TypeBinder;
import code.docflow.types.DocumentRef;
import code.docflow.utils.BitArray;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
        newTemplate = RefBinderBase.processJsonTemplate(fld);
        silentlySkipInaccessibleObjects = fld.isAnnotationPresent(JsonSilentlySkipInaccessibleObjects.class);
    }

    public void copyFromJson(final Object obj, final JsonNode node,
                             DocumentAccessActionsRights rights, BitArray mask, final String fldPrefix,
                             DocumentUpdateImpl update, DocumentRef docId, Object outerStructure, final Result result) throws Exception {

        Preconditions.checkState(false, "Collection binder support rending to Json only!");

    }

    public void copyToJson(Object obj, Template template, ObjectNode out, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {

        if (silentlySkipInaccessibleObjects)
            mode |= JsonTypeBinder.SILENT_SKIP_INACCESSIBLE_DOCS;

        final Object rec = getter.invoke(obj);

        if (rec == null) out.putNull(fldName);
        else out.put(fldName, valueBinder.copyToJson(getter.invoke(obj), newTemplate != null ? newTemplate : template, stack, mode));
    }
}