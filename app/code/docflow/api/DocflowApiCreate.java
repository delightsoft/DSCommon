package code.docflow.api;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.action.DocumentUpdateImpl;
import code.docflow.action.Transaction;
import code.docflow.controlflow.Result;
import code.docflow.Docflow;
import code.docflow.action.ActionParams;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.model.DocType;
import code.docflow.docs.DocumentPersistent;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.types.DocumentRef;
import code.docflow.users.CurrentUser;
import com.fasterxml.jackson.databind.node.ObjectNode;
import docflow.DocflowMessages;
import play.exceptions.UnexpectedException;

import java.lang.reflect.InvocationTargetException;

import static com.google.common.base.Preconditions.checkArgument;

public class DocflowApiCreate {
    public static <T extends DocumentPersistent> T _create(DocType docType, ActionParams params, ObjectNode update, DocumentAccessActionsRights fullRights, DocumentRef subjId, Result result) {
        checkArgument(docType != null, "docType required");
        checkArgument(result != null, "result required");

        final CurrentUser user = CurrentUser.getInstance();

        if (!user.inActionScope && subjId == null) { // it's not linkedDocument case
            if (fullRights != null)
                checkArgument(docType == fullRights.docType, "Expected type of fullRights is '%s', but it is '%s'.", docType.name, fullRights.docType.name);
            else {
                fullRights = RightsCalculator.instance.calculate(docType, user.getUserRoles());
            }
            if (!fullRights.actionsMask.get(CrudActions.CREATE.index)) {
                result.addMsg(DocflowMessages.error_DocflowInsufficientRights_2, docType.name, CrudActions.CREATE.toString());
                return null;
            }
        }

        final DocumentPersistent doc = docType.jsonBinder.recordAccessor.newRecord();
        Object res = null;

        final DocumentUpdateImpl documentUpdate = Transaction.instance().push(doc, CrudActions.CREATE.toString(), params, result);
        if (documentUpdate == null) // it's cycled document update
            return null;
        try {
            if (update != null) {
                Result localResult = new Result();
                DocflowApiUpdate._update(doc, update, null, documentUpdate, subjId, localResult);
                if (localResult.isNotOk() && localResult.getCode() != Result.ActionOmitted)
                    result.append(localResult);
            }

            if (result.isError())
                return null;

            if (docType.preCreateMethod != null) {
                try {
                    Object anotherDoc = docType.preCreateMethod.invoke(null, doc, result);
                    if (anotherDoc != null) {
                        Transaction.instance().setPreCreatedFoundEqualDocumentResult();
                        result.setCode(Result.PreCreatedFoundEqualDocument);
                        return (T) anotherDoc;
                    }
                } catch (IllegalAccessException e) {
                    throw new UnexpectedException(e);
                } catch (InvocationTargetException e) {
                    throw new UnexpectedException(e.getCause());
                }
            }

            if (result.isError())
                return (T) doc;

            res = DocflowApiAction._action(doc, docType.actionsArray[CrudActions.CREATE.index], params, fullRights, documentUpdate, subjId, result);

            documentUpdate.saveDocument();

        } catch (Throwable e) {
            result.addException(e);
            return null;
        } finally {
            Transaction.instance().pop(result);
        }

        return (res == Docflow.VOID) ? (T) doc : (T) res;
    }
}
