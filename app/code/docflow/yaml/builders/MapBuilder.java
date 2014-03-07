package code.docflow.yaml.builders;

import code.controlflow.Result;
import code.docflow.yaml.YamlMessages;
import code.docflow.yaml.YamlParser;
import code.docflow.yaml.converters.MapConverter;
import code.docflow.yaml.converters.PrimitiveTypeConverter;
import com.google.common.base.Strings;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ScalarEvent;

import java.util.LinkedHashMap;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class MapBuilder extends ItemBuilder {

    ItemBuilder elementBuilder;
    PrimitiveTypeConverter keyConvertor;
    MapConverter mapConverter;

    public MapBuilder(ItemBuilder elementBuilder, PrimitiveTypeConverter keyConvertor, MapConverter mapConverter) {
        checkNotNull(elementBuilder);
        this.elementBuilder = elementBuilder;
        this.keyConvertor = keyConvertor;
        this.mapConverter = mapConverter;
    }

    @Override
    public String getTypeTitle() {
        return "map<" +
                ((keyConvertor != null) ? keyConvertor.getTypeTitle() : PrimitiveTypeConverter.STRING_CONVERTER.getTypeTitle()) +
                "," +
                elementBuilder.getTypeTitle() +
                ">";
    }

    @Override
    public Object build(YamlParser parser, Result result) {
        LinkedHashMap map = new LinkedHashMap();
        final Result localResult = new Result();

        Event ev = parser.next();
        if (ev.is(Event.ID.Scalar))
            if (Strings.isNullOrEmpty(((ScalarEvent) ev).getValue().trim()))
                return null;
        if (!parser.isExpectedBeginning(Event.ID.MappingStart, result))
            return null;

        while (parser.hasNext()) {
            ev = parser.next();
            if (ev.is(Event.ID.MappingEnd))
                return mapConverter == null ? map : mapConverter.convert(map);
            Object key = ((ScalarEvent) ev).getValue();
            parser.savePosition();
            if (keyConvertor != null) {
                try {
                    key = keyConvertor.convert((String) key);
                } catch (PrimitiveTypeConverter.InvalidValueException e) {
                    result.addMsg(YamlMessages.error_CannotConvertKey,
                            parser.getSavedFilePosition(), keyConvertor.getTypeTitle(), key);
                    parser.skipNextValue();
                    continue;
                }
            }
            localResult.clear();
            Object el = elementBuilder.build(parser, localResult);
            if (localResult.isNotOk())
                if (localResult.getCode() != Result.WrongValue)
                    result.append(localResult);
                else
                    result.addMsg(YamlMessages.error_WrongValueForType,
                            parser.getSavedFilePosition(),
                            elementBuilder.getTypeTitle(),
                            parser.getSavedValue());
            else {
                if (map.containsKey(key)) {
                    result.addMsg(YamlMessages.error_DuplicatedMapKey, parser.getSavedFilePosition(), key);
                    parser.skipNextValue();
                } else
                    map.put(key, el);
            }
        }
        return null;
    }
}
