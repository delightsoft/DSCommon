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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;
import org.joda.time.DateTime;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.Stack;

public class MultiMapTypeBinder extends TypeBinder {

    private final TypeBinder valueBinder;

    protected MultiMapTypeBinder(Type[] parameters) {
        if (parameters != null) {
            Preconditions.checkArgument(parameters.length == 2);
            valueBinder = TypeBinder.factory.get(parameters[1]);
        } else
            valueBinder = new ObjectTypeBinder();
    }

    @Override
    public JsonNode copyToJson(final Object value, Template template, Stack<String> stack, int mode) throws Exception {

        if (value == null) return JsonNodeFactory.instance.nullNode();

        final ObjectNode res = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<Object, ?> pair : ((Multimap<Object, ?>) value).entries()) {
            final ArrayNode nodesArray = JsonNodeFactory.instance.arrayNode();
            final Object key = pair.getKey();
            for (Object item : (Collection<?>) pair.getValue()) {
                if ((mode & JsonTypeBinder.SILENT_SKIP_INACCESSIBLE_DOCS) != 0 && item instanceof Document) {
                    final DocumentAccessActionsRights rights = RightsCalculator.instance.calculate(((Document) item), CurrentUser.getInstance());
                    if (!rights.actionsMask.get(CrudActions.RETRIEVE.index))
                        continue;
                }
                nodesArray.add(valueBinder.copyToJson(item, template, stack, mode));
            }
            res.put(key instanceof DateTime ? "" + ((DateTime) key).getMillis() : key.toString(), nodesArray);
        }
        return res;
    }
}