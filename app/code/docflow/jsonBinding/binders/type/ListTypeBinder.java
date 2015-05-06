package code.docflow.jsonBinding.binders.type;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.compiler.enums.CrudActions;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.docs.Document;
import code.docflow.users.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.base.Preconditions;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Stack;

public class ListTypeBinder extends TypeBinder {

    private final TypeBinder valueBinder;

    protected ListTypeBinder(Type[] parameters) {
        if (parameters != null) {
            Preconditions.checkArgument(parameters.length == 1);
            valueBinder = TypeBinder.factory.get(parameters[0]);
        } else
            valueBinder = new ObjectTypeBinder();
    }

    @Override
    public JsonNode copyToJson(final Object value, Template template, Stack<String> stack, int mode) throws Exception {

        if (value == null) return JsonNodeFactory.instance.nullNode();

        final ArrayNode res = JsonNodeFactory.instance.arrayNode();
        final List<Object> list = (List<Object>) value;
        for (int i = 0; i < list.size(); i++) {
            final Object item = list.get(i);
            if ((mode & JsonTypeBinder.SILENT_SKIP_INACCESSIBLE_DOCS) != 0 && item instanceof Document) {
                final DocumentAccessActionsRights rights = RightsCalculator.instance.calculate(((Document) item), CurrentUser.getInstance());
                if (!rights.actionsMask.get(CrudActions.RETRIEVE.index))
                    continue;
            }
            res.add(valueBinder.copyToJson(item, template, stack, mode));
        }
        return res;
    }
}