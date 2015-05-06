package code.docflow.controlflow;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.action.Transaction;
import code.docflow.docs.Document;
import code.docflow.users.CurrentUser;
import play.Logger;
import play.db.jpa.JPAPlugin;
import play.db.jpa.NoTransaction;
import play.exceptions.JPAException;
import play.exceptions.UnexpectedException;
import play.jobs.Job;

/**
 * Extends Play Job, to support DSCommon rules: 1. Keep working ander user, who instantiated this job; 2. In any
 * case (Ok or Exception) result will be returned as Result object.
 * <p/>
 * Child must overide doDocflowJob method.
 * <p/>
 * Job should be started from context where user is authenticated, so all the work job is done
 * being sourced by proper user.
 */
@NoTransaction
public abstract class DocflowJob<V> extends Job<V> {

    private final Document user;
    private final String userRoles;

    private Result result;

    public static class Context {
        boolean withinScope;
    }

    private final static ThreadLocal<Context> context = new ThreadLocal<Context>() {
        @Override
        protected Context initialValue() {
            return new Context();
        }
    };

    public static boolean isWithinScope() {
        return context.get().withinScope;
    }

    public DocflowJob() {
        final CurrentUser currentUser = CurrentUser.getInstance();
        user = currentUser.getUser() == null ? CurrentUser.ANONYMOUS_USER : currentUser.getUser();
        userRoles = currentUser.getUserRoles() == null ? CurrentUser.ANONYMOUS_USER.roles : currentUser.getUserRoles();
    }

    protected DocflowJob(Document user, String userRoles) {
        this.user = user;
        this.userRoles = userRoles;
    }

    /**
     * Returns result of the job.  Result is available only after job completion.
     */
    public final Result getResult() {
        return result;
    }

    public final void execute(final Result result) {
        final Result localResult = this.result = new Result();
        try {
            this.doDocflowJob(localResult);
            result.append(localResult);
        } catch (Exception e) {
            result.append(localResult);
            throw new UnexpectedException(e);
        }
    }

    @Override
    public V doJobWithResult() throws Exception {
        final Result result = new Result();
        V res = null;
        final CurrentUser currentUser = CurrentUser.getInstance();
        final boolean prevInActionScopeValue = currentUser.inActionScope;
        try {
            context.get().withinScope = true;
            currentUser.inActionScope = true;
            currentUser.setUser(user, userRoles);
            res = doDocflowJob(result);
            if (result.isError())
                res = null;

            try {
                JPAPlugin.closeTx(result.isError()); // commit or rollback depending on result.isError()
            } catch (JPAException e1) { // then no transaction is opened at the moment
                // it says 'The JPA context is not initialized...', so we just ignor this
            }
        } catch (Throwable e) {
            result.addException(e);
            Transaction.rollbackPreviousTransactionIfOpened();
            Logger.error("Job '%s': Error:\n%s", this.getClass().getName(), result.toString());
        } finally {
            currentUser.inActionScope = prevInActionScopeValue;
            context.get().withinScope = false;
        }

        this.result = result;
        return res;
    }

    public abstract V doDocflowJob(Result result) throws Exception;
}
