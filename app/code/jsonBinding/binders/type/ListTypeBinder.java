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
    protected void init() {
        // nothing
    }

    @Override
    public void copyToJson(final Object value, Template template, JsonGenerator generator, Stack<String> stack, int mode) throws Exception {
        if (value == null)
            generator.writeNull();
        else {
            final List<Object> list = (List<Object>) value;
            generator.writeStartArray();
            for (int i = 0; i < list.size(); i++) {
                final Object item = list.get(i);
                if ((mode & JsonTypeBinder.SILENT_SKIP_INACCESSIBLE_DOCS) != 0 && item instanceof Document) {
                    final DocumentAccessActionsRights rights = RightsCalculator.instance.calculate(((Document) item), CurrentUser.getInstance());
                    if (!rights.actionsMask.get(DocflowConfig.ImplicitActions.RETRIEVE.index))
                        continue;
                }
                valueBinder.copyToJson(item, template, generator, stack, mode);
            }
            generator.writeEndArray();
        }
    }
}