package code.docflow.yaml.builders;

import code.controlflow.Result;
import code.docflow.yaml.YamlParser;
import org.yaml.snakeyaml.events.Event;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class DocumentBuilder extends ItemBuilder {

    ItemBuilder itemBuilder;

    public DocumentBuilder(ItemBuilder itemBuilder) {
        checkNotNull(itemBuilder, "itemBuilder");
        this.itemBuilder = itemBuilder;
    }

    @Override
    protected void init() {
        // nothing
    }

    @Override
    public String getTypeTitle() {
        return "[yaml-document]";
    }

    @Override
    public Object build(YamlParser parser, Result result) {
        int s = 0;
        Object res = null;
        loop:
        while (parser.hasNext()) {
            Event ev = parser.next();
            switch (s) {
                case 0:
                    if (!ev.is(Event.ID.StreamStart))
                        break;
                    s = 1;
                    continue;
                case 1:
                    if (ev.is(Event.ID.StreamEnd))
                        break loop; // empty doc
                    if (!ev.is(Event.ID.DocumentStart))
                        break;
                    s = 2;
                    // fall through
                case 2:
                    res = itemBuilder.build(parser, result);
                    s = 3;
                    continue;
                case 3:
                    if (!ev.is(Event.ID.DocumentEnd))
                        break;
                    s = 4;
                    continue;
                case 4:
                    if (ev.is(Event.ID.StreamEnd))
                        break loop;
            }
        }
        return res;
    }
}
