package code.docflow.jsonBinding.binders.time.type;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.model.Template;
import code.docflow.jsonBinding.binders.type.TypeBinder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.joda.time.DateTime;

import java.util.Stack;

public class DateTimeTypeBinder extends TypeBinder {

    @Override
    protected void init() {
        // nothing
    }

    @Override
    public JsonNode copyToJson(Object value, Template template, Stack<String> stack, int mode) throws Exception {
        if (value == null) return JsonNodeFactory.instance.nullNode();
        else return JsonNodeFactory.instance.numberNode(((DateTime) value).getMillis());
    }
}
