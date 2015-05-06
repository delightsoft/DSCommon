package code.docflow.jsonBinding.binders.type;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.model.Template;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.Stack;

public class UUIDTypeBinder extends TypeBinder {
    @Override
    public JsonNode copyToJson(Object value, Template template, Stack<String> stack, int mode) throws Exception {
        return JsonNodeFactory.instance.textNode(value.toString());
    }
}
