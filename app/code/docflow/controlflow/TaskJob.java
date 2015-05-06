package code.docflow.controlflow;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.Docflow;
import code.docflow.action.Transaction;
import code.docflow.api.http.ActionResult;
import code.docflow.compiler.enums.BuiltInFields;
import code.docflow.compiler.enums.TaskActions;
import code.docflow.docs.DocumentPersistent;
import code.docflow.model.DocType;
import code.docflow.users.CurrentUser;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.db.jpa.JPAPlugin;
import play.db.jpa.NoTransaction;
import play.exceptions.JPAException;
import play.jobs.Job;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@NoTransaction
public class TaskJob extends Job<ActionResult> {

    final DocumentPersistent task;
    final public ObjectNode resultNode;

    public static final JobSequence<ActionResult> taskJobs = new JobSequence<ActionResult>();

    public TaskJob(DocumentPersistent task) {

        checkNotNull(task, "task");
        checkArgument(task._docType().task, "Document '%s': Is not a task document", task._docType().name);

        this.task = task;
        this.resultNode = JsonNodeFactory.instance.objectNode();
    }

    // TODO: Restart jobs on reload

    @Override
    public ActionResult doJobWithResult() throws Exception {
        final Result result = new Result();
        ActionResult res = null;

        final CurrentUser currentUser = CurrentUser.getInstance();
        final boolean prevInActionScopeValue = currentUser.inActionScope;
        try {
            currentUser.inActionScope = true;
            currentUser.setUser(task, CurrentUser.SYSTEM_USER.roles);

            transitTaskToRunningState(result);

            if (!result.isError()) {

                final Result taskResult = new Result();

                try {
                    // run task code, and process exceptions
                    final DocType taskDocType = task._docType();
                    res = (ActionResult) taskDocType.taskEvoluator.invoke(null, task, resultNode, taskResult);
                } catch (Throwable e) {
                    taskResult.addException(e);
                }

                try {
                    JPAPlugin.closeTx(taskResult.isError()); // commit or rollback depending on taskResult
                } catch (JPAException e1) { // then transaction was not opened
                    // it says 'The JPA context is not initialized...', so we just ignor this
                } catch (Throwable e) {
                    taskResult.addException(e);
                }

                taskResult.outputException();
                saveResultToTaskDocumentAndTransitToTerminalState(taskResult, result);
            }
        } finally {
            currentUser.inActionScope = prevInActionScopeValue;
        }

        if (result.isError())
            result.toLogger(String.format("TaskJob: Running task '%s'", task._fullId()));

        if (res == null || result.isError()) {
            if (Transaction.instance().isWithinScope())
                res = new ActionResult(task, null, null, null, result);
            else {
                JPAPlugin.startTx(true); // readonly
                res = new ActionResult(task, null, null, null, result);
                JPAPlugin.closeTx(false);
            }
        }
        return res;
    }

    private void transitTaskToRunningState(final Result result) {
        Transaction.scope(result, new Transaction.Delegate() {
            @Override
            public Object body(int attempt, Result result) {
                Docflow.action(task._attached(), TaskActions.STARTJOB.toString(), result);
                return null;
            }
        });
    }

    private static void transitTaskBackToAwaitState(final DocumentPersistent task, final Result result) {
        Transaction.scope(result, new Transaction.Delegate() {
            @Override
            public Object body(int attempt, final Result result) {
                Docflow.action(task._attached(), TaskActions.BACKTOAWAIT.toString(), result);
                return null;
            }
        });
    }

    private void saveResultToTaskDocumentAndTransitToTerminalState(final Result taskResult, final Result result) {
        resultNode.put(BuiltInFields.RESULT.toString(), Result2Json.toJson(taskResult));
        Transaction.scope(result, new Transaction.Delegate() {
            @Override
            public Object body(int attempt, final Result result) {
                final Result localResult = new Result();
                final DocumentPersistent attachedTask = task._attached();
                Docflow.update(attachedTask, resultNode, localResult);
                if (localResult.isError()) { // expecting errors like 'Unexpected fields' in the task update
                    if (localResult.hasException()) { // reopen transaction on exception, just in case
                        JPAPlugin.closeTx(true);
                        JPAPlugin.startTx(false);
                    }
                    resultNode.removeAll();
                    resultNode.put(BuiltInFields.RESULT.toString(), Result2Json.toJson(localResult));
                    localResult.clear();
                    Docflow.update(attachedTask, resultNode, result);
                }
                if (!result.isError()) {
                    Docflow.action(attachedTask._attached(), (taskResult.isError() ? TaskActions.ERROR : TaskActions.SUCCESS).toString(), result);
                }
                return null;
            }
        });
    }
}
