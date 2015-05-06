package code.docflow.action;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.Docflow;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.controlflow.Result;
import code.docflow.docs.DocumentPersistent;
import code.docflow.docs.DocumentVersioned;
import code.docflow.users.CurrentUser;
import docflow.DocflowMessages;
import models.DocflowFile;
import play.Logger;
import play.db.jpa.GenericModel;
import play.db.jpa.JPAPlugin;
import play.exceptions.JPAException;
import play.exceptions.PlayException;
import play.exceptions.UnexpectedException;

import javax.persistence.OptimisticLockException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.Callable;

import static com.google.common.base.Preconditions.*;

/**
 * Tracks current source for actions.
 */
public final class Transaction {

    Stack<DocumentUpdateImpl> stack = new Stack<DocumentUpdateImpl>();
    Map<Integer, DocumentUpdateImpl> map = new HashMap<Integer, DocumentUpdateImpl>();

    public int updatesCount;
    public int actionsCount;

    public ArrayList<DocumentUpdateImpl> rootUpdates = new ArrayList<DocumentUpdateImpl>();

    private final static ThreadLocal<Transaction> instance = new ThreadLocal<Transaction>() {
        @Override
        protected Transaction initialValue() {
            return new Transaction();
        }
    };

    private Result outerResult;
    private Result localResult;
    private boolean withinContextFinilization;
    private play.mvc.results.Result playResult;

    public boolean isWithinScope() {
        return localResult != null;
    }

    public static Transaction instance() {
        return instance.get();
    }

    public static class Failed extends PlayException {

        Result _result;

        public Failed(final Result result, final Throwable cause) {
            super("Error Message in Result", cause);
            _result = result;
        }

        public Failed(final Result result) {
            _result = result;
        }

        @Override
        public String getErrorTitle() {
            return "Error Message in Result";
        }

        @Override
        public String getErrorDescription() {
            return _result.toHtml();
        }
    }

    public void setPreCreatedFoundEqualDocumentResult() {
        stack.peek().preCreatedFoundEqualDocument = true;
    }

    public void backRefOnSave(DocumentPersistent doc, DocumentUpdateImpl.BackReference backReference) {
        checkNotNull(doc, "doc");
        checkNotNull(backReference, "backReference");

        DocumentUpdateImpl documentUpdate = map.get(System.identityHashCode(doc));
        checkNotNull(documentUpdate);

        if (documentUpdate.backReferences == null)
            documentUpdate.backReferences = new ArrayList<DocumentUpdateImpl.BackReference>();
        documentUpdate.backReferences.add(backReference);
    }

    public DocumentUpdateImpl push(DocumentPersistent doc, String action, ActionParams params, Result result) {
        checkNotNull(doc);
        checkNotNull(result);
        DocumentUpdateImpl documentUpdate = map.get(System.identityHashCode(doc));
        if (documentUpdate != null) {
            if (action != null && documentUpdate.wasAction) {
                result.setCode(Result.ActionSkipped);
                return null;
            }
            stack.push(null); // since it's duplicated documentUpdate
        } else {
            documentUpdate = new DocumentUpdateImpl(doc, action, params);
            if (stack.size() == 0) {
                rootUpdates.add(documentUpdate);
                updatesCount = 0;
                actionsCount = 0;
            } else {
                final DocumentUpdateImpl prevContext = stack.peek();
                if (doc instanceof DocumentVersioned && !(prevContext.doc instanceof DocumentVersioned)) {
                    result.addMsg(DocflowMessages.error_ProhibitedToUpdateVersionedDocumentFromPersistedDocument_2, prevContext.doc, doc);
                    return null;
                }
                if (prevContext.consequentUpdates == null)
                    prevContext.consequentUpdates = new ArrayList<DocumentUpdateImpl>();
                prevContext.consequentUpdates.add(documentUpdate);
            }
            stack.push(documentUpdate);
            map.put(System.identityHashCode(doc), documentUpdate);
        }

        if (action != null) {
            documentUpdate.action = action;
            documentUpdate.wasAction = true;
        } else {
            if (documentUpdate.action == null)
                documentUpdate.action = CrudActions.UPDATE.toString();
        }

        return documentUpdate;
    }

    public DocumentUpdateImpl pop(Result result) {
        final DocumentUpdateImpl documentUpdate = stack.pop();
        if (documentUpdate != null) {
            if (documentUpdate.wasAction)
                actionsCount++;
            if (documentUpdate.wasUpdate)
                updatesCount++;
        }
        if (stack.size() == 0) {
            map.clear();
            if (!result.isError())
                documentUpdate.saveHistoryAndLinkLinkedDocs();
        }
        return documentUpdate;
    }

    public interface Delegate<T> {
        public T body(int attempt, Result result);
    }

    public static final int MAX_ATTEMPTS = 10;
    public static final int LOG_AFTER_ATTEMPT = 5;

    public static class OptimisticLockCallStack extends Exception {
        public OptimisticLockCallStack(int attempt) {
            super(String.format("Attempt %d to perform operation", attempt));
        }
    }

    /**
     * Scope of transaction.  Transaction gets rollback on any Error or Exception while any {@link code.docflow.Docflow} method.
     * If any Hibernate save() operation failes with {@link javax.persistence.OptimisticLockException}, scope will
     * rollback transaction, and take another attempt to perform whole operation up to {@link #MAX_ATTEMPTS} times.
     * <p>Part of information can saved by calling {@link code.docflow.action.Transaction#commit(code.docflow.controlflow.Result)}.
     */
    public static <T> T scope(Result result, Delegate<T> delegate) {
        return _scope(result, MAX_ATTEMPTS, false, false, delegate);
    }

    /**
     * Scope of transaction.  Transaction gets rollback on any Error or Exception while any {@link code.docflow.Docflow} method.
     * If any Hibernate save() operation failes with {@link javax.persistence.OptimisticLockException}, scope will
     * rollback transaction, and take another attempt to perform whole operation up to {@link #MAX_ATTEMPTS} times.
     * <p>Part of information can saved by calling {@link code.docflow.action.Transaction#commit(code.docflow.controlflow.Result)}.
     */
    public static <T> T readOnlylScope(Result result, Delegate<T> delegate) {
        return _scope(result, MAX_ATTEMPTS, false, true, delegate);
    }

    /**
     * Scope of transaction.  Transaction gets rollback on any Error or Exception while any {@link code.docflow.Docflow} method.
     * If any Hibernate save() operation failes with {@link javax.persistence.OptimisticLockException}, scope will
     * rollback transaction, and take another attempt to perform whole operation up to {@link #MAX_ATTEMPTS} times.
     * <p>Part of information can saved by calling {@link code.docflow.action.Transaction#commit(code.docflow.controlflow.Result)}.
     */
    public static <T> T scope(Result result, boolean stopOnError, Delegate<T> delegate) {
        return _scope(result, MAX_ATTEMPTS, stopOnError, false, delegate);
    }

    /**
     * Scope of transaction.  Transaction gets rollback on any Error or Exception while any {@link code.docflow.Docflow} method.
     * If any Hibernate save() operation failes with {@link javax.persistence.OptimisticLockException}, scope will
     * rollback transaction, and take another attempt to perform whole operation up to {@link #MAX_ATTEMPTS} times.
     * <p>Part of information can saved by calling {@link code.docflow.action.Transaction#commit(code.docflow.controlflow.Result)}.
     */
    public static <T> T readOnlylScope(Result result, boolean stopOnError, Delegate<T> delegate) {
        return _scope(result, MAX_ATTEMPTS, stopOnError, true, delegate);
    }

    /**
     * Like {@link #scope(code.docflow.controlflow.Result, code.docflow.action.Transaction.Delegate) scope(...)}, but do
     * not try to rerun delegate's body in case of {@link javax.persistence.OptimisticLockException}.
     */
    public static <T> T dataImport(final Result result, final Delegate<T> delegate) {
        return CurrentUser.systemUserScope(true, new Callable<T>() {
            @Override
            public T call() throws Exception {
                return _scope(result, 0, true, false, delegate);
            }
        });
    }

    private static <T> T _scope(Result result, int maxAttempts, boolean stopOnError, boolean readOnly, Delegate<T> delegate) {
        final Transaction currentContext = Transaction.instance();

        checkState(!currentContext.isWithinScope());
        checkState(!currentContext.withinContextFinilization);

        T res = currentContext.runDelegateWithTransactionAndRespectToOptimisticLocks(delegate, maxAttempts, stopOnError, readOnly, result);

        currentContext.withinContextFinilization = true;
        try {
            currentContext.finilizeUpdate(result);
        } finally {
            currentContext.withinContextFinilization = false;
        }

        return res;
    }

    public static void commit() {
        instance()._commit();
    }

    private void _commit() {
        checkState(isWithinScope()); // inside Transaction.scope(...)
        checkState(map.size() == 0); // not inside of any DocflowApi method
        if (rootUpdates.size() > 0) { // got anything to commit
            JPAPlugin.closeTx(false);
            finilizeUpdate(localResult);
            outerResult.append(localResult);
            localResult.clear();
            JPAPlugin.startTx(false);
        }
    }

    public void finilizeUpdate(final Result result) {
        checkState(stack.size() == 0);
        if (!result.isError())
            for (DocumentUpdateImpl rootUpdate : rootUpdates) {
                try {
                    rootUpdate.runTasks();
                    if (Docflow._anySubscriber())
                        // TODO: Optimize use of transaction for subscribers
                        rootUpdate.notifySubscribers();
                } catch (Exception e) {
                    result.addException(e);
                }
            }
        rootUpdates.clear();
        if (playResult != null) {
            final play.mvc.results.Result t = playResult;
            playResult = null;
            throw t;
        }
    }

    private <T> T runDelegateWithTransactionAndRespectToOptimisticLocks(Delegate<T> delegate, int maxAttempts, boolean stopOnError, boolean readOnly, Result result) {
        outerResult = result;
        localResult = new Result();
        if (stopOnError)
            localResult.setThrowExceptionOnError();
        try {
            rollbackPreviousTransactionIfOpened();
            for (int attempt = 0; ; attempt++) {
                JPAPlugin.startTx(readOnly); // new transaction
                localResult.clear();
                try {
                    T res = null;
                    try {
                        res = delegate.body(attempt, localResult);
                    } catch (play.mvc.results.Result e) {
                        playResult = e;
                    }
                    if (!localResult.isError()) {
                        JPAPlugin.closeTx(false); // commit
                        return res;
                    }
                } catch (OptimisticLockException e) {
                    if (attempt >= LOG_AFTER_ATTEMPT)
                        Logger.warn(new OptimisticLockCallStack(attempt), "Too many attempts to overcome an optimisitic lock");
                    JPAPlugin.closeTx(true); // rollback
                    if (attempt < maxAttempts)
                        continue;
                    localResult.addException(e);
                    return null;
                }
                JPAPlugin.closeTx(true); // rollback
                return null;
            }
        } catch (Failed e) {
            rollbackPreviousTransactionIfOpened();
            if (e.getCause() != null)
                localResult.addException(e.getCause());
            else
                localResult.addException(e);
        } catch (Exception e) {
            rollbackPreviousTransactionIfOpened();
            localResult.addException(e);
        } finally {
            Result t = localResult;
            outerResult = null;
            localResult = null;
            result.append(t);
        }
        return null;
    }

    public static void rollbackPreviousTransactionIfOpened() {
        try {
            JPAPlugin.closeTx(true);
        } catch (JPAException e1) { // then transaction was not opened
            // it says 'The JPA context is not initialized...', so we just ignor this
        }
    }
}
