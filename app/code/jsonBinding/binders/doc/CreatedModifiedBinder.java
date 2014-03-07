package code.jsonBinding.binders.doc;

import code.controlflow.Result;
import code.docflow.action.DocumentUpdate;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.jsonBinding.JsonTypeBinder;
import code.types.PolymorphicRef;
import code.utils.BitArray;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
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
                             DocumentUpdate update, PolymorphicRef docId, Object outerStructure, final Result result) throws Exception {
    }

    public void copyToJson(final Object obj, Template template, final JsonGenerator generator, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask)
            throws Exception {
        final DateTime v = (DateTime) getter.invoke(obj);
        generator.writeStringField(fldName, v.toLocalDateTime().toString(dateTimeFormatter));
    }
}
