package code.docflow.jsonBinding;

import code.docflow.controlflow.Result;
import code.docflow.docs.DocumentHistory;
import code.docflow.docs.DocumentVersioned;
import code.docflow.utils.TypeBuildersFactory;
import play.Play;
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

        if (!DocumentHistory.class.isAssignableFrom(type)) {
            result.addMsg(Messages.ClassMustBeDerivedFromHistoryBase, type.getName());
        }

        try {
            tDocumentSetter = type.getDeclaredMethod("setDocument", doc);
        } catch (NoSuchMethodException e) {
            result.addMsg(Messages.MethodNotSpecifiedInClass, type.getName(), "findById(Object id)");
        }

        if (result.isError())
            throw new UnexpectedException(result.combineMessages());
    }


    public DocumentHistory findById(long id) {
        return (DocumentHistory) super.findById(id);
    }

    public DocumentHistory newRecord(DocumentVersioned doc) {
        final DocumentHistory hr = (DocumentHistory) super.newRecord();
        try {
            tDocumentSetter.invoke(hr, doc);
        } catch (IllegalAccessException e) {
            throw new UnexpectedException(e);
        } catch (InvocationTargetException e) {
            throw new UnexpectedException(e);
        }
        return hr;
    }
}
