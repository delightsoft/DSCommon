package code.docflow.compiler.preconditions;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.docs.Document;
import play.exceptions.UnexpectedException;

import java.lang.reflect.Method;

public final class PreconditionFailedResult {

    public static final Method METHOD;

    static {
        try {
            METHOD = PreconditionFailedResult.class.getMethod("isFailed", Document.class);
        } catch (NoSuchMethodException e) {
            throw new UnexpectedException(e);
        }
    }

    public static boolean isFailed(Document doc) {
        return true;
        // TODO: Remove after 2014-11-15 if no use.  Appeared that result is in form of String, and it's hard to get to known is it Ok or Error
//        final RecordAccessor recordAccessor = RecordAccessor.factory.get(doc.getClass());
//
//        if (recordAccessor.fldResultGetter == null)
//            throw new UnexpectedException(String.format("Missing field 'result' in type '%s'", doc.getClass().getName()));
//        try {
//            final Result result = (Result) recordAccessor.fldResultGetter.invoke(doc);
//            return result.isError();
//        } catch (IllegalAccessException e) {
//            throw new UnexpectedException(e);
//        } catch (InvocationTargetException e) {
//            throw new UnexpectedException(e.getCause());
//        }
    }
}
