package code.docflow.yaml.builders;

import code.docflow.controlflow.Result;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;
import code.docflow.yaml.converters.SequenceConverter;
import org.yaml.snakeyaml.events.Event;

import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class SequenceBuilder extends ItemBuilder {

    ItemBuilder elementBuilder;
    SequenceConverter sequenceConverter;

    public SequenceBuilder(ItemBuilder elementBuilder, SequenceConverter arrayConverter) {
        checkNotNull(elementBuilder, "elementBuilder");
        this.elementBuilder = elementBuilder;
        this.sequenceConverter = arrayConverter;
    }

    @Override
    public String getTypeTitle() {
        return elementBuilder.getTypeTitle() + "[]";
    }

    @Override
    public Object build(YamlParser parser, Result result) {
        ArrayList al = new ArrayList();
        final Result localResult = new Result();
        int s = 0;

        parser.next();
        if (!parser.isExpectedBeginning(Event.ID.SequenceStart, result))
            return null;

        while (true) {
            localResult.clear();
            Object el = elementBuilder.build(parser, localResult);
            if (parser.isFallback()) {
                final Event ev = parser.next();
                if (ev.is(Event.ID.SequenceEnd))
                    return (sequenceConverter == null) ? al : sequenceConverter.convert(al);
                throw new UnsupportedOperationException(String.format("Unexpected event: %s", ev.getClass().getName()));
            }
            if (localResult.isNotOk())
                if (localResult.getCode() != Result.WrongValue)
                    result.append(localResult);
                else
                    result.addMsg(YamlMessages.error_WrongValueForType,
                            parser.getSavedFilePosition(),
                            elementBuilder.getTypeTitle(),
                            parser.getSavedValue());
            else
                al.add(el);
        }
    }
}
