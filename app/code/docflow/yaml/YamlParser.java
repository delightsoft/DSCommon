package code.docflow.yaml;

import code.docflow.controlflow.Result;
import org.apache.commons.lang.NotImplementedException;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ScalarEvent;

import java.util.Iterator;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class YamlParser implements Iterator<Event> {
    private Event fallback;
    private final Iterator<Event> iterator;
    private Event last;
    private Event saved;

    public String filename;

    public YamlParser(Iterable<Event> parser) {
        this.iterator = parser.iterator();
    }

    public YamlParser(Iterable<Event> parser, String filename) {
        this.iterator = parser.iterator();
        this.filename = filename;
    }

    public void savePosition() {
        saved = last;
    }

    public void fallback() {
        checkState(fallback == null);
        fallback = last;
    }

    public boolean isFallback() {
        return fallback != null;
    }

    public FilePosition getSavedFilePosition() {
        checkNotNull(saved);
        return new FilePosition(filename, saved.getStartMark().getLine() + 1);
    }

    public String getSavedValue() {
        checkNotNull(saved);
        return saved.is(Event.ID.Scalar) ?
                ((ScalarEvent) saved).getValue() :
                saved.getStartMark().get_snippet();
    }

    public boolean isExpectedBeginning(Event.ID eventId, Result result) {
        savePosition();
        if (last.is(eventId))
            return true;
        if ((last.is(Event.ID.MappingEnd) || last.is(Event.ID.SequenceEnd) || last.is(Event.ID.DocumentEnd))) {
            fallback();
            return false;
        }
        result.setCode(Result.WrongValue);
        skipThisValue();
        return false;
    }

    /**
     * Skips next value within yaml.
     */
    public void skipNextValue() {
        next();
        skipThisValue();
    }

    /**
     * Skips value within yaml, without processing it.
     */
    public void skipThisValue() {
        checkNotNull(last);
        if (last.is(Event.ID.MappingStart))
            skipMapping();
        else if (last.is(Event.ID.SequenceStart))
            skipSequence();
    }

    /**
     * Skip current mapping.
     */
    public void skipMapping() {
        while (hasNext()) {
            final Event ev = next();
            if (ev.is(Event.ID.MappingEnd))
                return;
            if (ev.is(Event.ID.MappingStart))
                skipMapping();
            else if (ev.is(Event.ID.SequenceStart))
                skipSequence();
        }
    }

    /**
     * Skip current sequence.
     */
    public void skipSequence() {
        while (hasNext()) {
            final Event ev = next();
            if (ev.is(Event.ID.SequenceEnd))
                return;
            if (ev.is(Event.ID.MappingStart))
                skipMapping();
            else if (ev.is(Event.ID.SequenceStart))
                skipSequence();
        }
    }

    @Override
    public boolean hasNext() {
        return fallback != null || iterator.hasNext();
    }

    @Override
    public Event next() {
        if (fallback != null) {
            final Event t = fallback;
            fallback = null;
            return t;
        }
        return (last = iterator.next());
    }

    @Override
    public void remove() {
        throw new NotImplementedException("remove() is not supported");
    }

}
