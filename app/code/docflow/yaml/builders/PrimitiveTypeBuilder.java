package code.docflow.yaml.builders;

import code.controlflow.Result;
import code.docflow.yaml.YamlParser;
import code.docflow.yaml.converters.PrimitiveTypeConverter;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ScalarEvent;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class PrimitiveTypeBuilder extends ItemBuilder {

    public PrimitiveTypeConverter converter;

    protected PrimitiveTypeBuilder(PrimitiveTypeConverter converter) {
        checkNotNull(converter, "converter");
        this.converter = converter;
    }

    @Override
    public String getTypeTitle() {
        return converter.getTypeTitle();
    }

    @Override
    public Object build(YamlParser parser, Result result) {
        checkState(parser.hasNext());
        Event ev = parser.next();
        if (!parser.isExpectedBeginning(Event.ID.Scalar, result))
            return null;
        final String value = ((ScalarEvent) ev).getValue();
        try {
            return converter.convert(value);
        } catch (PrimitiveTypeConverter.InvalidValueException e) {
            result.setCode(Result.WrongValue);
            return null;
        }
    }
}
