package code.jsonBinding;

import code.controlflow.Result;
import code.docflow.messages.GeneralMessages;
import code.models.HistoryBase;
import code.models.PersistentDocument;
import code.utils.TypeBuildersFactory;
import play.Play;
import play.exceptions.JavaExecutionException;
import play.exceptions.UnexpectedException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class HistoryAccessor extends RecordAccessorCommon {

    private Class doc;

    private Method tDocumentSetter;

    public static final TypeBuildersFactory<HistoryAccessor> factory = new TypeBuildersFactory<HistoryAccessor>() {
        public HistoryAccessor newInstance(TypeDescription typeDesc) {
            checkState(typeDesc.parameters == null);
            String name = typeDesc.type.getName() + "History";
            final Class historyClass = Play.classloader.getClassIgnoreCase(name);
            checkNotNull(historyClass, name);
            return new HistoryAccessor(typeDesc.type,  historyClass);
        }
    };

    protected HistoryAccessor(final Class<?> doc, final Class<?> type) {
        super(type);
        this.doc = doc;
    }

    @Override
    protected void init() {
        final Result result = new Result();

        super.init(result);

        if (!HistoryBase.class.isAssignableFrom(type)) {
            result.addMsg(GeneralMessages.ClassMustBeDerivedFromHistoryBase, type.getName());
        }

        try {
            tDocumentSetter = type.getDeclaredMethod("setDocument", doc);
        } catch (NoSuchMethodException e) {
            result.addMsg(GeneralMessages.MethodNotSpecifiedInClass, type.getName(), "findById(Object id)");
        }

        if (result.isError())
            throw new UnexpectedException(result.combineMessages());
    }


    public HistoryBase findById(long id) {
        return (HistoryBase) super.findById(id);
    }

    public HistoryBase newRecord(PersistentDocument doc) {
        final HistoryBase hr = (HistoryBase) super.newRecord();
        try {
            tDocumentSetter.invoke(hr, doc);
        } catch (IllegalAccessException e) {
            throw new JavaExecutionException(e);
        } catch (InvocationTargetException e) {
            throw new JavaExecutionException(e);
        }
        return hr;
    }
}
