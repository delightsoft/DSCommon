package code.docflow.jsonBinding.binders.type;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.model.Template;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.Stack;

public class BooleanTypeBinder extends TypeBinder {
    @Override
    public com.fasterxml.jackson.databind.JsonNode copyToJson(Object value, Template template, Stack<String> stack, int mode) throws Exception {
        if (value == null) return JsonNodeFactory.instance.nullNode();
        else return JsonNodeFactory.instance.booleanNode((Boolean) value);
    }
}
