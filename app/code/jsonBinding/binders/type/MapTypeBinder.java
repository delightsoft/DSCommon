package code.jsonBinding.binders.type;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.DocflowConfig;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.jsonBinding.JsonTypeBinder;
import code.models.Document;
import code.users.CurrentUser;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.Preconditions;
import org.joda.time.DateTime;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Stack;

public class MapTypeBinder extends TypeBinder {

    private final TypeBinder valueBinder;

    protected MapTypeBinder(Type[] parameters) {
        if (parameters != null) {
            Preconditions.checkArgument(parameters.length == 2);
            valueBinder = TypeBinder.factory.get(parameters[1]);
        } else
            valueBinder = new ObjectTypeBinder();
    }

    @Override
    protected void init() {
        // nothing
    }

    @Override
    public void copyToJson(final Object value, Template template, JsonGenerator generator, Stack<String> stack, int mode) throws Exception {
        if (value == null)
            generator.writeNull();
        else {
            final Map<?, ?> map = (Map<?, ?>) value;
            generator.writeStartObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                final Object item = entry.getValue();
                if ((mode & JsonTypeBinder.SILENT_SKIP_INACCESSIBLE_DOCS) != 0 && item instanceof Document) {
                    final DocumentAccessActionsRights rights = RightsCalculator.instance.calculate(((Document) item), CurrentUser.getInstance());
                    if (!rights.actionsMask.get(DocflowConfig.ImplicitActions.RETRIEVE.index))
                        continue;
                }
                generator.writeFieldName(key instanceof DateTime ? "" + ((DateTime) key).getMillis() : key.toString());
                valueBinder.copyToJson(item, template, generator, stack, mode);
            }
            generator.writeEndObject();
        }
    }
}