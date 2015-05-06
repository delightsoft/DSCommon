package code.docflow;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.action.ActionParams;
import code.docflow.action.DocumentUpdate;
import code.docflow.action.Transaction;
import code.docflow.api.*;
import code.docflow.controlflow.DocflowJob;
import code.docflow.controlflow.Result;
import code.docflow.docs.DocumentPersistent;
import code.docflow.docs.DocumentVersioned;
import code.docflow.model.Action;
import code.docflow.model.DocType;
import code.docflow.queries.CounterBuilder;
import code.docflow.users.CurrentUser;
import code.docflow.utils.HttpUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import models.DocflowFile;
import play.mvc.results.RenderText;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedDeque;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class Docflow {

    /**
     * If docflow.action(...) returns such object that means that Actions&lt;docType&gt;.&lt;action&gt;(...) either not implemented or returns void.
     */
    public static final Object VOID = new Object();

    public static <T extends DocumentPersistent> T create(final DocType docType, final ObjectNode update, final Result result) {
        if (!DocflowJob.isWithinScope() || Transaction.instance().isWithinScope()) {
            final Result localResult = new Result();
            T res = (T) DocflowApiCreate._create(docType, null, update, null, null, localResult);
            result.append(localResult);
            return res;
        } else
            return Transaction.scope(result, new Transaction.Delegate<T>() {
                @Override
                public T body(int attempt, Result result) {
                    T res = (T) DocflowApiCreate._create(docType, null, update, null, null, result);
                    return res;
                }
            });
    }

    public static <T extends DocumentPersistent> T create(final DocType docType, final ActionParams params, final ObjectNode update, final Result result) {
        if (!DocflowJob.isWithinScope() || Transaction.instance().isWithinScope()) {
            final Result localResult = new Result();
            T res = (T) DocflowApiCreate._create(docType, params, update, null, null, localResult);
            result.append(localResult);
            return res;
        } else
            return Transaction.scope(result, new Transaction.Delegate<T>() {
                @Override
                public T body(int attempt, Result result) {
                    T res = (T) DocflowApiCreate._create(docType, params, update, null, null, result);
                    return res;
                }
            });
    }

    public static <T extends DocumentPersistent> T update(final DocumentPersistent doc, final ObjectNode update, final Result result) {
        checkNotNull(doc, "doc");
        checkNotNull(update, "update");
        checkNotNull(result, "result");
        if (!DocflowJob.isWithinScope() || Transaction.instance().isWithinScope()) {
            final Result localResult = new Result();
            T res = (T) DocflowApiUpdate._update(doc, update, null, null, null, localResult);
            result.append(localResult);
            return res;
        } else
            return Transaction.scope(result, new Transaction.Delegate<T>() {
                @Override
                public T body(int attempt, Result result) {
                    T res = (T) DocflowApiUpdate._update(doc, update, null, null, null, result);
                    return res;
                }
            });
    }

    public static void action(final DocType docType, final String actionName, final Result result) {
        checkNotNull(docType, "docType");
        checkArgument(!Strings.isNullOrEmpty(actionName), "actionName");
        final Action action = docType.actions.get(actionName.toUpperCase());
        checkArgument(action.service, "Action '%s': Not a service action called against document model (docType).", action.name);
        checkNotNull(result, "result");
        if (!DocflowJob.isWithinScope() || Transaction.instance().isWithinScope()) {
            final Result localResult = new Result();
            DocflowApiAction._action(null, action, null, null, null, null, localResult);
            result.append(localResult);
        } else
            Transaction.scope(result, new Transaction.Delegate<Object>() {
                @Override
                public Object body(int attempt, Result result) {
                    DocflowApiAction._action(null, action, null, null, null, null, result);
                    return null;
                }
            });
    }

    public static void action(final DocType docType, final String actionName, final ActionParams params, final Result result) {
        checkNotNull(docType, "docType");
        checkArgument(!Strings.isNullOrEmpty(actionName), "actionName");
        final Action action = docType.actions.get(actionName.toUpperCase());
        checkArgument(action.service, "Action '%s': Not a service action called against document model (docType).", action.name);
        if (action.paramsClass == null)
            checkArgument(params == null, "params are not expected");
        else if (params != null)
            checkArgument(action.paramsClass.isAssignableFrom(params.getClass()), "params must be instance of %s", action.paramsClassName);
        checkNotNull(result, "result");

        if (!DocflowJob.isWithinScope() || Transaction.instance().isWithinScope()) {
            final Result localResult = new Result();
            DocflowApiAction._action(null, action, params, null, null, null, localResult);
            result.append(localResult);
        } else
            Transaction.scope(result, new Transaction.Delegate<Object>() {
                @Override
                public Object body(int attempt, Result result) {
                    DocflowApiAction._action(null, action, params, null, null, null, result);
                    return null;
                }
            });
    }

    public static <T> T action(final DocumentPersistent doc, final String actionName, final Result result) {
        checkArgument(!Strings.isNullOrEmpty(actionName), "actionName");
        final Action action = doc._docType().actions.get(actionName.toUpperCase());
        checkArgument(!action.service, "Action '%s': Service action called against document instance.", action.name);
        checkNotNull(result, "result");
        if (!DocflowJob.isWithinScope() || Transaction.instance().isWithinScope()) {
            final Result localResult = new Result();
            T res = (T) DocflowApiAction._action(doc, action, null, null, null, null, localResult);
            result.append(localResult);
            return res;
        } else
            return Transaction.scope(result, new Transaction.Delegate<T>() {
                @Override
                public T body(int attempt, Result result) {
                    T res = (T) DocflowApiAction._action(doc, action, null, null, null, null, result);
                    return res;
                }
            });
    }

    public static <T> T action(final DocumentPersistent doc, final String actionName, final ActionParams params, final Result result) {
        checkArgument(!Strings.isNullOrEmpty(actionName), "actionName");
        final Action action = doc._docType().actions.get(actionName.toUpperCase());
        checkArgument(!action.service, "Action '%s': Service action called against document instance.", action.name);
        if (action.paramsClass == null)
            checkArgument(params == null, "params are not expected");
        else if (params != null)
            checkArgument(action.paramsClass.isAssignableFrom(params.getClass()), "params must be instance of %s", action.paramsClassName);
        checkNotNull(result, "result");

        if (!DocflowJob.isWithinScope() || Transaction.instance().isWithinScope()) {
            final Result localResult = new Result();
            T res = (T) DocflowApiAction._action(doc, action, params, null, null, null, localResult);
            result.append(localResult);
            return res;
        } else
            return Transaction.scope(result, new Transaction.Delegate<T>() {
                @Override
                public T body(int attempt, Result result) {
                    T res = (T) DocflowApiAction._action(doc, action, params, null, null, null, result);
                    return res;
                }
            });
    }

    public static <T extends DocumentVersioned> T delete(final T doc, final Result result) {
        return delete(doc, true, result);
    }

    public static <T extends DocumentVersioned> T recover(final T doc, final Result result) {
        return delete(doc, false, result);
    }

    public static <T extends DocumentVersioned> T delete(final DocumentVersioned doc, final boolean delete, final Result result) {
        checkNotNull(doc, "doc");

        if (!DocflowJob.isWithinScope() || Transaction.instance().isWithinScope()) {
            final Result localResult = new Result();
            T res = (T) DocflowApiDelete._delete(doc, delete, localResult);
            result.append(localResult);
            return res;
        } else
            return Transaction.scope(result, new Transaction.Delegate<T>() {
                @Override
                public T body(int attempt, Result result) {
                    T res = (T) DocflowApiDelete._delete(doc, delete, result);
                    return res;
                }
            });
    }

    public static DocflowFile persistFile(final File file, final String fileTitle, final Result result) {
        checkNotNull(file, "file");
        checkArgument(file.exists(), "file must exist");
        checkArgument(!Strings.isNullOrEmpty(fileTitle), "fileTitle");
        checkNotNull(result, "result");

        if (!DocflowJob.isWithinScope() || Transaction.instance().isWithinScope()) {
            final Result localResult = new Result();
            DocflowFile res = DocflowApiFile._persistFile(file, fileTitle, localResult);
            result.append(localResult);
            return res;
        } else
            return Transaction.scope(result, new Transaction.Delegate<DocflowFile>() {
                @Override
                public DocflowFile body(int attempt, Result result) {
                    DocflowFile res = DocflowApiFile._persistFile(file, fileTitle, result);
                    return res;
                }
            });
    }

    public static DocflowFile persistFile(final File file, final Result result) {
        checkNotNull(file, "file");
        checkNotNull(result, "result");

        if (!DocflowJob.isWithinScope() || Transaction.instance().isWithinScope()) {
            final Result localResult = new Result();
            DocflowFile res = DocflowApiFile._persistFile(file, null, localResult);
            result.append(localResult);
            return res;
        } else
            return Transaction.scope(result, new Transaction.Delegate<DocflowFile>() {
                @Override
                public DocflowFile body(int attempt, Result result) {
                    DocflowFile res = DocflowApiFile._persistFile(file, null, result);
                    return res;
                }
            });
    }

    public static File getFile(DocflowFile docflowFile, Result result) {
        checkNotNull(docflowFile, "docflowFile");
        checkNotNull(result, "result");

        return DocflowApiFile._getFile(docflowFile, result);
    }

    public interface Subscriber {
        public void notification(DocumentUpdate event);
    }

    final static ConcurrentLinkedDeque<Subscriber> subscribers = new ConcurrentLinkedDeque<Subscriber>();

    public static void addSubscriber(Subscriber subscriber) {
        subscribers.add(subscriber);
    }

    public static void removeSubscriber(Subscriber subscriber) {
        subscribers.remove(subscriber);
    }

    public static boolean _anySubscriber() {
        return subscribers.size() > 0;
    }

    public static void _dispatch(DocumentUpdate update) {
        for (Subscriber subscriber : subscribers)
            subscriber.notification(update);
    }

    public interface Delegate {
        public void body(int attempt, Result result);
    }

    public static void httpScope(final Delegate delegate) {
        HttpUtil.returnErrors();
        final Result result = new Result();
        CurrentUser.inActionScope(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                Transaction.scope(result, true, new Transaction.Delegate<Object>() {
                    @Override
                    public Object body(int attempt, Result result) {
                        delegate.body(attempt, result);
                        return null;
                    }
                });
                return null;
            }
        });
        if (result.isError()) {
            result.toLogger("Docflow.httpScope");
            throw new RenderText(String.format("Failed: %s", result.toString()));
        }
    }

    public static void httpReadOnlyScope(final Delegate delegate) {
        HttpUtil.returnErrors();
        final Result result = new Result();
        CurrentUser.inActionScope(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                Transaction.readOnlylScope(result, true, new Transaction.Delegate<Object>() {
                    @Override
                    public Object body(int attempt, Result result) {
                        delegate.body(attempt, result);
                        return null;
                    }
                });
                return null;
            }
        });
        if (result.isError()) {
            result.toLogger("Docflow.httpScope");
            throw new RenderText(String.format("Failed: %s", result.toString()));
        }
    }

    public static CounterBuilder counter(final String listQuery) {
        return CounterBuilder.buildFromUrl(listQuery);
    }
}
