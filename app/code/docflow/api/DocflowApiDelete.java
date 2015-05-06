package code.docflow.api;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.controlflow.Result;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.model.Action;
import code.docflow.docs.DocumentVersioned;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.docs.DocumentPersistent;
import code.docflow.users.CurrentUser;
import docflow.DocflowMessages;
import play.exceptions.UnexpectedException;

import java.lang.reflect.InvocationTargetException;

import static com.google.common.base.Preconditions.checkState;

public class DocflowApiDelete {
    public static DocumentVersioned _delete(DocumentVersioned doc, final boolean delete, final Result result) {

        final DocumentAccessActionsRights rights = RightsCalculator.instance.calculate(doc, CurrentUser.getInstance());

        if (!rights.actionsMask.get(CrudActions.RETRIEVE.index)) {
            result.setCode(Result.NotFound);
            return doc;
        }

        if (!rights.actionsMask.get(delete ? CrudActions.DELETE.index : CrudActions.RECOVER.index)) {
            result.setCode(Result.ActionProhibited);
            return doc;
        }

        doc = (DocumentVersioned) doc._attached();

        if (doc.deleted == delete) {
            result.setCode(Result.ActionOmitted);
            return doc;
        }

        final Action action = doc._docType().actions.get((delete ? CrudActions.DELETE : CrudActions.RECOVER).toString().toUpperCase());
        if (action.actionMethod != null) {
            CurrentUser user = CurrentUser.getInstance();
            final boolean prevInActionScope = user.inActionScope;
            try {
                user.inActionScope = true;
                checkState(action.params == null);
                action.actionMethod.invoke(doc, result);
            } catch (IllegalAccessException e) {
                throw new UnexpectedException(e);
            } catch (InvocationTargetException e) {
                throw new UnexpectedException(e.getCause());
            } finally {
                user.inActionScope = prevInActionScope;
            }
        }

        doc.rev++;
        doc.deleted = delete;
        doc.save();

        // TODO: Fix work with history for delete/restore.  Apply right src
//        DocumentUpdate.writeHistory(verDoc, delete ? DocflowConfig.CrudActions.DELETE.toString() : DocflowConfig.CrudActions.RECOVER.toString(), null, null, null);

        return doc;
    }
}
