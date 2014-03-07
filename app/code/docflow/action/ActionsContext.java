package code.docflow.action;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.controlflow.Result;
import code.docflow.DocflowConfig;
import code.models.PersistentDocument;
import com.google.common.base.Preconditions;
import play.db.jpa.JPAPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * Tracks current source for actions.
 */
public final class ActionsContext {

    Stack<DocumentUpdate> stack = new Stack<DocumentUpdate>();
    Map<Integer, DocumentUpdate> map = new HashMap<Integer, DocumentUpdate>();

    public int updatesCount;
    public int actionsCount;

    private final static ThreadLocal<ActionsContext> instance = new ThreadLocal<ActionsContext>() {
        @Override
        protected ActionsContext initialValue() {
            return new ActionsContext();
        }
    };

    public static ActionsContext instance() {
        return instance.get();
    }

    public void setPreCreatedFoundEqualDocumentResult() {
        stack.peek().preCreatedFoundEqualDocument = true;
    }

    public DocumentUpdate push(PersistentDocument doc, String action, ActionParams params, Result result) {
        Preconditions.checkNotNull(doc);
        Preconditions.checkNotNull(result);
        DocumentUpdate documentUpdate = map.get(System.identityHashCode(doc));
        if (documentUpdate != null) {
            if (action == null) { // it's update
                if (documentUpdate.wasUpdate) {
                    result.setCode(Result.UpdateRejected);
                    return null;
                }
            } else if (documentUpdate.wasAction) {
                result.setCode(Result.ActionSkipped);
                return null;
            }
            stack.push(null); // since it's duplicated documentUpdate
        } else {
            documentUpdate = new DocumentUpdate(doc, action, params);
            if (stack.size() == 0) {
                updatesCount = 0;
                actionsCount = 0;
            } else {
                final DocumentUpdate prevContext = stack.peek();
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
                documentUpdate.action = DocflowConfig.ImplicitActions.UPDATE.toString();
            documentUpdate.wasUpdate = true;
        }

        return documentUpdate;
    }

    public void pop(Result result) {
        final DocumentUpdate documentUpdate = stack.pop();
        if (documentUpdate != null) {
            if (documentUpdate.wasAction)
                actionsCount++;
            if (documentUpdate.wasUpdate)
                updatesCount++;
        }
        if (stack.size() == 0) {
            map.clear(); // map gets cleared only at the very end of action processing
            if (result.isError())
                JPAPlugin.closeTx(true);
            else
                documentUpdate.saveHistoryAndLinkLinkedDocs();
        }
    }
}
