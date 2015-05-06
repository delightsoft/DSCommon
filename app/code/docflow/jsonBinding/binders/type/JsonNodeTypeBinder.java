package code.docflow.jsonBinding.binders.type;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.compiler.enums.CrudActions;
import code.docflow.docs.Document;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.users.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import org.joda.time.DateTime;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Stack;

public class JsonNodeTypeBinder extends TypeBinder {
    @Override
    public JsonNode copyToJson(final Object value, Template template, Stack<String> stack, int mode) throws Exception {
        if (value == null) return JsonNodeFactory.instance.nullNode();
        return (JsonNode) value;
   }
}