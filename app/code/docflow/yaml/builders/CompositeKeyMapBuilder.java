package code.docflow.yaml.builders;

import code.controlflow.Result;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;
import code.docflow.yaml.converters.MapConverter;
import com.google.common.base.Strings;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ScalarEvent;

import java.util.LinkedHashMap;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class CompositeKeyMapBuilder extends ItemBuilder {

    CompositeKeyObjectBuilder elementBuilder;
    String keyTypeTitle;
    MapConverter mapConverter;

    public CompositeKeyMapBuilder(CompositeKeyObjectBuilder elementBuilder, Class keyType, MapConverter mapConverter) {
        checkNotNull(elementBuilder);
        this.elementBuilder = elementBuilder;
        this.keyTypeTitle = ItemBuilder.factory.get(keyType, true).getTypeTitle();
        this.mapConverter = mapConverter;
    }

    @Override
    protected void init() {
        // nothing
    }

    @Override
    public String getTypeTitle() {
        return "map<" +
                keyTypeTitle +
                "," +
                elementBuilder.getTypeTitle() +
                ">";
    }

    @Override
    public Object build(YamlParser parser, Result result) {
        LinkedHashMap map = new LinkedHashMap();
        final Result localResult = new Result();

        Event ev = parser.next();
        if (ev.is(Event.ID.Scalar)) { // empty map, if value is nothing
            if (!Strings.isNullOrEmpty(((ScalarEvent) ev).getValue())) {
                parser.savePosition();
                parser.skipThisValue();
                result.setCode(Result.WrongValue);
                return null;
            }
            return mapConverter == null ? map : mapConverter.convert(map);
        }

        if (!parser.isExpectedBeginning(Event.ID.SequenceStart, result))
            return null;

        while (true) {
            localResult.clear();
            Object el = elementBuilder.build(parser, localResult);
            if (localResult.isNotOk())
                result.append(localResult);
                if (el != null) {
                final Object key = elementBuilder.compositeKeyHandler.key(el);
                if (map.containsKey(key)) {
                    result.addMsg(YamlMessages.error_DuplicatedMapKey, parser.getSavedFilePosition(), key);
                } else
                    map.put(key, el);
            }
            ev = parser.next();
            if (ev.is(Event.ID.SequenceEnd))
                return mapConverter == null ? map : mapConverter.convert(map);
            parser.fallback();
        }
    }
}
