package code.docflow.yaml.builders;

import code.controlflow.Result;
import code.docflow.yaml.CompositeKeyHandler;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ScalarEvent;

import java.util.HashSet;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class CompositeKeyObjectBuilder extends ObjectBuilder {

    CompositeKeyHandler compositeKeyHandler;

    public CompositeKeyObjectBuilder(Class type, CompositeKeyHandler compositeKeyHandler) {
        super(type);
        checkNotNull(compositeKeyHandler, "compositeKeyHandler");
        this.compositeKeyHandler = compositeKeyHandler;
    }

    @Override
    public Object build(YamlParser parser, Result result) {
        checkState(parser.hasNext());
        final HashSet<String> accessedFields = new HashSet<String>();
        Event ev = parser.next();
        if (ev.is(Event.ID.MappingStart)) { // composite key object with its properties in standard way
            // Example:
            //  document(operation):
            //    type: something
            //    val: 12
            ev = parser.next();
            parser.savePosition();
            Object res = compositeKeyHandler.parse(((ScalarEvent) ev).getValue(), accessedFields, type, parser, result);
            if (res == null || result.isError())
                parser.skipNextValue();
            else {
                final ObjectBuilder resBuilder = (res.getClass() != type) ? (ObjectBuilder) factory.get(res.getClass(), true) : this;
                resBuilder.build(parser, res, accessedFields, result);
            }
            ev = parser.next();
            if (!ev.is(Event.ID.MappingEnd)) {
                parser.savePosition();
                result.addMsg(YamlMessages.error_InvalidStructure, parser.getSavedFilePosition(), parser.getSavedValue());
                parser.skipMapping();
                return null;
            }
            return res;
        }
        // Otherwise, its simple composite key object
        // Example:
        //  document(operation)
        if (!parser.isExpectedBeginning(Event.ID.Scalar, result))
            return null;
        return compositeKeyHandler.parse(((ScalarEvent) ev).getValue(), accessedFields, type, parser, result);
    }
}
