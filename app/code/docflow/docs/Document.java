package code.docflow.docs;

import code.docflow.model.DocType;
import code.docflow.model.State;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.types.DocumentRef;
import code.docflow.users.CurrentUser;
import code.docflow.utils.BitArray;
import play.db.jpa.GenericModel;
import play.exceptions.UnexpectedException;

import javax.persistence.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
                _state = type.statesArray[_isPersisted() ? 1 : 0]; // 0 - it's new; 1 - first and one state
                checkNotNull(_state);
            }
        }
        return _state;
    }

    public void _updateState(State newStateName) {
        _state = null;
    }

    public boolean _isPersisted() {
        return false;
    }

    public abstract String _fullId();

    public abstract DocumentRef _ref();

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

    @Transient
    protected BitArray _calculatedFields;

    public void calculate(final int[] fields) {
        checkNotNull(fields, "fields");
        final DocumentAccessActionsRights rights = RightsCalculator.instance.calculate(this, CurrentUser.getInstance());
        calculate(fields, rights);
    }

    public void calculate(final int[] fields, final DocumentAccessActionsRights rights) {
        checkNotNull(fields, "fields");
        checkNotNull(rights, "rights");
        final BitArray fieldsMask = rights.updateMask.copy();
        fieldsMask.clear();
        for (int v : fields)
            fieldsMask.set(v, true);
        calculate(fieldsMask, rights);
    }

    public void calculate(final BitArray fieldsMask) {
        checkNotNull(fieldsMask, "fieldsMaks");
        final DocumentAccessActionsRights rights = RightsCalculator.instance.calculate(this, CurrentUser.getInstance());
        calculate(fieldsMask, rights);
    }

    public void calculate(BitArray fieldsMask, final DocumentAccessActionsRights rights) {
        checkNotNull(fieldsMask, "fieldsMaks");
        checkNotNull(rights, "rights");
        // Calculate only fields that was not calculated before
        BitArray t = fieldsMask;
        fieldsMask = t.copy();
        if (_calculatedFields != null) {
            fieldsMask.subtract(_calculatedFields);
            _calculatedFields.add(t);
        }
        else
            _calculatedFields = fieldsMask;

        if (!fieldsMask.isEmpty()) {
            Method calculateMethod = _docType().calculateMethod;
            checkNotNull(calculateMethod, "Missing Query%1$s.calculate(...) method", _docType().name);
            try {
                calculateMethod.invoke(null, this, fieldsMask, rights);
            } catch (IllegalAccessException e) {
                throw new UnexpectedException(e);
            } catch (InvocationTargetException e) {
                throw new UnexpectedException(e.getCause());
            }
        }
    }
}