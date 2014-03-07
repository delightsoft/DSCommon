package code.models;

import code.docflow.model.DocType;
import code.docflow.model.State;
import play.db.jpa.GenericModel;

import javax.persistence.*;

import java.util.concurrent.ConcurrentLinkedQueue;

import static com.google.gdata.util.common.base.Preconditions.checkNotNull;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
@MappedSuperclass
public abstract class Document extends GenericModel implements Comparable<Document> {

    @Transient
    protected State _state;

    @Transient
    public abstract DocType _docType();

    @Transient
    public State _state() {
        if (_state == null) {
            final DocType type = _docType();
            if (type != null && type.statesArray != null && type.statesArray.length > 0) {
                _state = type.statesArray[this.isPersistent() ? 1 : 0]; // 0 - it's new; 1 - first and one state
                checkNotNull(_state);
            }
        }
        return _state;
    }

    public void _updateState(State newStateName) {
        _state = null;
    }

    public abstract String _fullId();

    @Override
    public String toString() {
        return _fullId();
    }

    @Override
    public int compareTo(Document o) {
        return _fullId().compareTo(o._fullId());
    }

    protected interface ResetForTest {
        void reset();
    }

    @Transient
    protected static ConcurrentLinkedQueue<ResetForTest> _resetQueue = new ConcurrentLinkedQueue<ResetForTest>();

    public static void _resetForTest() {
        for (ResetForTest resetForTest : _resetQueue)
            resetForTest.reset();
        _resetQueue.clear();
    }
}