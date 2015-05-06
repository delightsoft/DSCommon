package code.docflow.jsonBinding.binders.type;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.model.Template;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.jsonBinding.RecordAccessor;
import code.docflow.docs.Document;
import code.docflow.docs.DocumentVersioned;
import code.docflow.utils.PrimitiveType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.collections.MultiMap;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class ObjectTypeBinder extends TypeBinder {

    public static final String JSON_ERROR_TEMPLATE_NOT_FOUND = "error-template-not-found";
    public static final String JSON_ERROR_CYCLED_DOCUMENT = "error-cycled-simple-document";

    @Override
    public JsonNode copyToJson(Object value, Template template, Stack<String> stack, int mode) throws Exception {

        if (value == null) return JsonNodeFactory.instance.nullNode();

        if (value instanceof Document)
            return renderReferredObjectWithRespectToTemplate((Document) value, template, stack);

        if (value instanceof Map || value instanceof MultiMap || value instanceof List)
            return TypeBinder.factory.get(value.getClass()).copyToJson(value, template, stack, mode);

        PrimitiveType type = PrimitiveType.get(value.getClass());
        switch (type) {
            case BooleanType:
                return JsonNodeFactory.instance.booleanNode((Boolean) value);
            case IntegerType:
                return JsonNodeFactory.instance.numberNode((Integer) value);
            case LongType:
                return JsonNodeFactory.instance.numberNode((Long) value);
            case FloatType:
                return JsonNodeFactory.instance.numberNode((Float) value);
            case DoubleType:
                return JsonNodeFactory.instance.numberNode((Double) value);
            case DateTimeType:
                return JsonNodeFactory.instance.numberNode(((DateTime) value).getMillis());
            case NotPrimitiveOrPrimitiveWrapper:
                return JsonTypeBinder.factory.get(value.getClass()).toJson(value, template, stack, mode, null, null);


        }
        return JsonNodeFactory.instance.textNode(value.toString());
    }

    static boolean isObjectAlreadyInStack(Stack<String> stack, Document doc) {
        String docId = doc._fullId();
        for (int i = 0; i < stack.size(); i++) {
            if (stack.get(i).equals(docId))
                return true;
        }
        return false;
    }

    public static JsonNode renderReferredObjectWithRespectToTemplate(Document doc, Template template, Stack<String> stack) throws IOException {

        if (stack != null && isObjectAlreadyInStack(stack, doc)) { // it's looped object
            if (doc instanceof DocumentVersioned)
                return JsonNodeFactory.instance.textNode(doc._fullId());
            else {
                final ObjectNode errNode = JsonNodeFactory.instance.objectNode();
                errNode.put(JSON_ERROR_CYCLED_DOCUMENT, doc.toString());
                return errNode;
            }
        }

        Template refDocTmpl = null;
        if (template != null) {
            refDocTmpl = doc._docType().templates.get(template.name.toUpperCase());
            if (refDocTmpl == null)
                return reportTemplateNotFound(doc, template.name);
        }

        // Rule: Classes derived from a document, are serialized as document.  As a consequence thier own fields are not serialized
        final JsonTypeBinder jsonBinder;
        if (doc instanceof Document)
            jsonBinder = ((Document) doc)._docType().jsonBinder;
        else
            jsonBinder = JsonTypeBinder.factory.get(doc.getClass());
        return jsonBinder.toJson(doc, refDocTmpl, stack, refDocTmpl != null ? refDocTmpl.modeMask : JsonTypeBinder.VIEW_MODE, null, null);
    }

    public static JsonNode reportTemplateNotFound(Document doc, String templateName) throws IOException {
        final ObjectNode errNode = JsonNodeFactory.instance.objectNode();
        final RecordAccessor recordAccessor = RecordAccessor.factory.get(doc.getClass());
        if (doc instanceof DocumentVersioned)
            errNode.put("id", doc._fullId());
        errNode.put(JSON_ERROR_TEMPLATE_NOT_FOUND, templateName);
        return errNode;
    }
}
