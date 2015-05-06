package code.docflow.api;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.action.DocumentUpdateImpl;
import code.docflow.action.Transaction;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.controlflow.Result;
import code.docflow.docs.DocumentPersistent;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.jsonBinding.RecordAccessor;
import code.docflow.model.Action;
import code.docflow.model.DocType;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.types.DocumentRef;
import code.docflow.users.CurrentUser;
import com.fasterxml.jackson.databind.node.ObjectNode;
import docflow.DocflowMessages;
import play.db.jpa.GenericModel;
import play.exceptions.UnexpectedException;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class DocflowApiUpdate {
    public static DocumentPersistent _update(DocumentPersistent doc, ObjectNode update, DocumentAccessActionsRights rights, DocumentUpdateImpl documentUpdate, DocumentRef subjId, Result result) {

        doc = doc._attached();

        checkArgument(subjId == null || !doc._isPersisted());

        checkNotNull(doc, "doc");
        final DocType docType = doc._docType();

        checkNotNull(update, "update");
        checkNotNull(result, "result");
        if (rights != null)
            checkArgument(docType == rights.docType, "Expected type of rights is '%s', but it is '%s'.", doc._docType().name, rights.docType.name);

        if (rights == null)
            rights = RightsCalculator.instance.calculate(doc, CurrentUser.getInstance());

        final boolean isNewDocument = !doc._isPersisted();

        if (!isNewDocument && !rights.actionsMask.get(CrudActions.UPDATE.index)) {
            result.addMsg(DocflowMessages.error_DocflowInsufficientRights_2, doc._docType().name, CrudActions.UPDATE.toString());
            return doc;
        }

        // localUpdate true, means that this update is not a part of create or action operation
        boolean localUpdate = (documentUpdate == null);
        if (localUpdate) {
            documentUpdate = Transaction.instance().push(doc, null, null, result);
            if (documentUpdate == null)
                return doc;
        }
        try {
            documentUpdate.newRecords = new ArrayList<GenericModel>();
            Object updateParams = null;
            if (docType.preUpdateMethod != null) {
                try {
                    documentUpdate.updateParams = updateParams = docType.preUpdateMethod.invoke(null, doc);
                } catch (IllegalAccessException e) {
                    throw new UnexpectedException(e);
                } catch (InvocationTargetException e) {
                    throw new UnexpectedException(e.getCause());
                }
            }

            JsonTypeBinder binder = JsonTypeBinder.factory.get(doc.getClass());
            final RecordAccessor recordAccessor = binder.recordAccessor;

            try {
                docType.jsonBinder.fromJson(doc, (ObjectNode) update, rights, null, null, documentUpdate, doc._ref(), null, result);
                if (result.isError())
                    return doc;
            } catch (Throwable e) {
                result.addException(e);
                return doc;
            }
            if (subjId != null) // it's linkedDocument case
                if (subjId.isNew()) {
                    final DocumentPersistent finalDoc = doc;
                    Transaction.instance().backRefOnSave(subjId.getDocumentUnsafe(), new DocumentUpdateImpl.BackReference() {
                        @Override
                        public void set(DocumentRef ref) {
                            try {
                                recordAccessor.fldSubjSetter.invoke(finalDoc, ref);
                                recordAccessor.save(finalDoc);
                            } catch (InvocationTargetException e) {
                                throw new UnexpectedException(e.getCause());
                            } catch (IllegalAccessException e) {
                                throw new UnexpectedException(e);
                            }
                        }
                    });
                } else {
                    try {
                        recordAccessor.fldSubjSetter.invoke(doc, subjId);
                    } catch (InvocationTargetException e) {
                        throw new UnexpectedException(e.getCause());
                    } catch (IllegalAccessException e) {
                        throw new UnexpectedException(e);
                    }
                }

            if (docType.textMethod != null) {
                final String text = (String) docType.textMethod.invoke(null, doc);
                checkNotNull(text);
                final Object currenValue = recordAccessor.fldTextGetter.invoke(doc);
                if (currenValue == null || !currenValue.equals(text)) {
                    recordAccessor.fldTextSetter.invoke(doc, text);
                    documentUpdate.wasUpdate = true;
                }
            }

            if (!isNewDocument && !documentUpdate.wasUpdate)
                return doc;

            Action updateAction = docType.actionsArray[CrudActions.UPDATE.index];
            if (updateAction.actionMethod != null) {
                CurrentUser user = CurrentUser.getInstance();
                final boolean prevInActionScope = user.inActionScope;
                try {
                    user.inActionScope = true;
                    if (docType.preUpdateMethod != null)
                        updateAction.actionMethod.invoke(null, doc, updateParams, result);
                    else
                        updateAction.actionMethod.invoke(null, doc, result);
                    if (result.isError())
                        return doc;
                } catch (IllegalAccessException e) {
                    throw new UnexpectedException(e);
                } catch (InvocationTargetException e) {
                    throw new UnexpectedException(e.getCause());
                } finally {
                    user.inActionScope = prevInActionScope;
                }
            }
            if (localUpdate)
                documentUpdate.saveDocument();
            result.setCodeWithSeverity(Result.Ok);
        } catch (Throwable e) {
            result.addException(e);
            return doc;
        } finally {
            if (localUpdate)
                Transaction.instance().pop(result);
        }
        return doc;
    }
}
