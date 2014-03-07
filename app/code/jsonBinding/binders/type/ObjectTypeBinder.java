package code.jsonBinding.binders.type;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.model.Template;
import code.jsonBinding.JsonTypeBinder;
import code.jsonBinding.RecordAccessor;
import code.models.Document;
import code.models.PersistentDocument;
import code.utils.PrimitiveType;
import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.commons.collections.MultiMap;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class ObjectTypeBinder extends TypeBinder {

    public static final String JSON_ERROR_TEMPLATE_NOT_FOUND = "template-not-found";
    public static final String JSON_ERROR_CYCLED_SIMPLE_DOCUMENT = "cycled-simple-document";

    protected ObjectTypeBinder() {
    }

    @Override
    protected void init() {
        // nothing
    }

    @Override
    public void copyToJson(Object value, Template template, JsonGenerator generator, Stack<String> stack, int mode) throws Exception {
        if (value == null)
            generator.writeNull();
        else if (value instanceof Document)
            renderReferredObjectWithRespectToTemplate((Document) value, template, generator, stack);
        else if (value instanceof Map || value instanceof MultiMap || value instanceof List) {
            TypeBinder collectionBinder = TypeBinder.factory.get(value.getClass());
            collectionBinder.copyToJson(value, template, generator, stack, mode);
        }
        else {
            PrimitiveType type = PrimitiveType.get(value.getClass());
            switch (type) {
                case BooleanType:
                    generator.writeBoolean((Boolean) value);
                    break;
                case IntegerType:
                    generator.writeNumber((Integer) value);
                    break;
                case LongType:
                    generator.writeNumber((Long) value);
                    break;
                case FloatType:
                    generator.writeNumber((Float) value);
                    break;
                case DoubleType:
                    generator.writeNumber((Double) value);
                    break;
                case DateTimeType:
                    generator.writeNumber(((DateTime) value).getMillis());
                    break;
                case NotPrimitiveOrPrimitiveWrapper:
                    JsonTypeBinder.factory.get(value.getClass()).toJson(value, template, generator, stack, mode, null, null);
                    break;
                default:
                    generator.writeString(value.toString());
            }
        }
    }

    static boolean isObjectAlreadyInStack(Stack<String> stack, Document doc) {
        String docId = doc._fullId();
        for (int i = 0; i < stack.size(); i++) {
            if (stack.get(i).equals(docId))
                return true;
        }
        return false;
    }

    public static void renderReferredObjectWithRespectToTemplate(Document doc, Template template, JsonGenerator generator, Stack<String> stack) throws IOException {

        if (stack != null && isObjectAlreadyInStack(stack, doc)) { // it's looped object
            if (doc instanceof PersistentDocument)
                generator.writeString(RecordAccessor.factory.get(doc.getClass()).getFullId((PersistentDocument) doc));
            else
                generator.writeStringField(JSON_ERROR_CYCLED_SIMPLE_DOCUMENT, doc.toString());
            return;
        }

        Template refDocTmpl = null;
        if (template != null) {
            refDocTmpl = doc._docType().templates.get(template.name.toUpperCase());
            if (refDocTmpl == null) {
                reportTemplateNotFound(doc, template.name, generator);
                return;
            }
        }

        // Rule: Classes derived from a document, are serialized as document.  As a consequence thier own fields are not serialized
        final JsonTypeBinder jsonBinder;
        if (doc instanceof Document)
            jsonBinder = ((Document) doc)._docType().jsonBinder;
        else
            jsonBinder = JsonTypeBinder.factory.get(doc.getClass());
        jsonBinder.toJson(doc, refDocTmpl, generator, stack, refDocTmpl != null ? refDocTmpl.modeMask : JsonTypeBinder.VIEW_MODE, null, null);
    }

    public static void reportTemplateNotFound(Document doc, String templateName, JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        final RecordAccessor recordAccessor = RecordAccessor.factory.get(doc.getClass());
        if (doc instanceof PersistentDocument)
            generator.writeStringField("id", recordAccessor.getFullId((PersistentDocument) doc));
        generator.writeStringField(JSON_ERROR_TEMPLATE_NOT_FOUND, templateName);
        generator.writeEndObject();
    }
}
