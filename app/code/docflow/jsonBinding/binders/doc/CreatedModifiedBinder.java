package code.docflow.jsonBinding.binders.doc;

import code.docflow.action.DocumentUpdateImpl;
import code.docflow.controlflow.Result;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.types.DocumentRef;
import code.docflow.utils.BitArray;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Stack;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class CreatedModifiedBinder extends JsonTypeBinder.FieldAccessor {
    static final DateTimeFormatter dateTimeFormatter = ISODateTimeFormat.localDateParser();
    static final LocalTime MIDNIGHT = new LocalTime(0, 0);

    public CreatedModifiedBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);
    }

    public void copyFromJson(final Object obj, final JsonNode node,
                             DocumentAccessActionsRights rights, BitArray mask, final String fldPrefix,
                             DocumentUpdateImpl update, DocumentRef docId, Object outerStructure, final Result result) throws Exception {
    }

    public void copyToJson(final Object obj, Template template, final ObjectNode out, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask)
            throws Exception {
        final DateTime v = (DateTime) getter.invoke(obj);
        out.put(fldName, v.toLocalDateTime().toString(dateTimeFormatter));
    }
}
