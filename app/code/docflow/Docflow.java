package code.docflow;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.controlflow.Result;
import code.docflow.action.ActionParams;
import code.docflow.action.ActionsContext;
import code.docflow.action.DocumentUpdate;
import code.docflow.model.Action;
import code.docflow.model.DocType;
import code.docflow.model.State;
import code.docflow.model.Transition;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.jsonBinding.RecordAccessor;
import code.models.PersistentDocument;
import code.types.PolymorphicRef;
import code.users.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import docflow.DocflowMessages;
import play.Logger;
import play.db.jpa.JPABase;
import play.exceptions.JavaExecutionException;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import static com.google.common.base.Preconditions.*;

// TODO: Add check of 'rev'

public class Docflow {

    /**
     * If docflow.action(...) returns such object that means that Actions&lt;docType&gt;.&lt;action&gt;(...) either not implemented or returns void.
     */
    public static final Object VOID = new Object();

    public static <T extends PersistentDocument> T create(DocType docType, JsonNode update, Result result) {
        return (T) create(docType, null, update, null, null, result);
    }

    public static <T extends PersistentDocument> T create(DocType docType, JsonNode update, DocumentAccessActionsRights fullRights, Result result) {
        return (T) create(docType, null, update, fullRights, null, result);
    }

    public static <T extends PersistentDocument> T create(DocType docType, ActionParams params, JsonNode update, DocumentAccessActionsRights fullRights, PolymorphicRef subjId, Result result) {

        checkArgument(docType != null, "docType required");
        checkArgument(result != null, "result required");
        checkArgument(!docType.simple, "docType.simple cannot be created by this method");

        if (subjId == null) { // it's not linkedDocument case
            if (fullRights != null)
                checkArgument(docType == fullRights.docType, "Expected type of fullRights is '%s', but it is '%s'.", docType.name, fullRights.docType.name);
            else
                fullRights = RightsCalculator.instance.calculate(docType, CurrentUser.getInstance().getUserRoles());

            if (!fullRights.actionsMask.get(DocflowConfig.ImplicitActions.CREATE.index)) {
                result.addMsg(DocflowMessages.error_DocflowInsufficientRights_2, docType.name, DocflowConfig.ImplicitActions.CREATE.toString());
                return null;
            }
        }

        final PersistentDocument doc = docType.jsonBinder.recordAccessor.newRecord();
        Object res = null;

        final DocumentUpdate documentUpdate = ActionsContext.instance().push(doc, DocflowConfig.ImplicitActions.CREATE.toString(), params, result);
        if (documentUpdate == null)
            return null;
        try {
            if (update != null) {
                Result localResult = new Result();
                update(doc, update, null, documentUpdate, subjId, localResult);
                if (localResult.isNotOk() && localResult.getCode() != Result.ActionOmitted)
                    result.append(localResult);
            }

            if (result.isError())
                return null;

            if (docType.preCreateMethod != null) {
                try {
                    Object anotherDoc = docType.preCreateMethod.invoke(null, doc, result);
                    if (anotherDoc != null) {
                        ActionsContext.instance().setPreCreatedFoundEqualDocumentResult();
                        result.setCode(Result.PreCreatedFoundEqualDocument);
                        return (T) anotherDoc;
                    }
                } catch (IllegalAccessException e) {
                    throw new JavaExecutionException(e);
                } catch (InvocationTargetException e) {
                    throw new JavaExecutionException(e.getCause());
                }
            }

            if (result.isError())
                return null;

            res = action(doc, docType.actionsArray[DocflowConfig.ImplicitActions.CREATE.index], params, fullRights, documentUpdate, subjId, result);

        } catch (RuntimeException ex) {
            result.setCodeWithSeverity(Result.Failed);
            throw ex;
        } finally {
            ActionsContext.instance().pop(result);
        }

        return (res == VOID) ? (T) doc : (T) res;
    }

    public static Object action(DocType docType, String actionName, Result result) {
        return action(docType, actionName, null, null, result);
    }

    public static Object action(DocType docType, String actionName, ActionParams args, Result result) {
        return action(docType, actionName, args, null, result);
    }

    public static Object action(DocType docType, String actionName, ActionParams args, DocumentAccessActionsRights rights, Result result) {
        checkArgument(docType != null, "docType required");
        checkArgument(!Strings.isNullOrEmpty(actionName), "actionName required");
        final Action action = docType.actions.get(actionName.toUpperCase());
        checkArgument(action.service, "Action '%s': Not a service action called against document model (docType).", action.name);
        return action(null, action, args, rights, null, null, result);
    }

    public static Object action(PersistentDocument doc, String actionName, Result result) {
        return action(doc, actionName, null, null, result);
    }

    public static Object action(PersistentDocument doc, String actionName, ActionParams args, Result result) {
        return action(doc, actionName, args, null, result);
    }

    public static Object action(PersistentDocument doc, String actionName, ActionParams args, DocumentAccessActionsRights rights, Result result) {
        checkArgument(!Strings.isNullOrEmpty(actionName), "actionName");
        final Action action = doc._docType().actions.get(actionName.toUpperCase());
        checkArgument(!action.service, "Action '%s': Service action called against document instance.", action.name);
        return action(doc, action, args, rights, null, null, result);
    }

    public static Object action(PersistentDocument doc, Action action, ActionParams args, DocumentAccessActionsRights rights, Result result) {
        return action(doc, action, args, rights, null, null, result);
    }

    public static Object action(final PersistentDocument doc, final Action action, final ActionParams params, DocumentAccessActionsRights rights, DocumentUpdate documentUpdate, PolymorphicRef subjId, final Result result) {

        checkNotNull(action, "action");
        checkNotNull(result, "result");

        if (action.service)
            checkArgument(doc == null, "For service action '%s' argument 'doc' must be null.", action.name);
        else {
            checkNotNull(doc, "doc");
            checkArgument(doc._docType() == action.document, "Expected action for docType '%s', but not '%s'.", doc._docType().name, action.document.name);
        }

        if (params != null) {
            if (action.params == null)
                checkArgument(false, "Action do not take params.");
            final String pclass = params.getClass().getName();
            final String expectedClass = action.getFullParamsClassName();
            checkArgument(pclass.equals(expectedClass), "Expected type of params is '%s', but not '%s'.", expectedClass, pclass);
        }

        if (subjId == null) { // this is not create some linkedDocument
            if (rights != null)
                checkArgument(doc._docType() == rights.docType, "Expected type of rights is '%s', but not '%s'.", doc._docType().name, rights.docType.name);
            else if (action.service)
                rights = RightsCalculator.instance.calculate(action.document, CurrentUser.getInstance().getUserRoles()); // full rights, in case of service
            else
                rights = RightsCalculator.instance.calculate(doc, CurrentUser.getInstance());

            if (!rights.actionsMask.get(action.index)) {
                result.addMsg(DocflowMessages.error_DocflowInsufficientRights_2, doc._fullId(), action.name);
                return null;
            }
        }
        Object res = null;
        if (action.service) {
            CurrentUser user = CurrentUser.getInstance();
            boolean prevInActionScope = user.inActionScope;
            user.inActionScope = true;
            try {
                if (action.params != null)
                    res = action.actionMethod.invoke(null, params, result);
                else
                    res = action.actionMethod.invoke(null, result);
            } catch (IllegalAccessException e) {
                throw new JavaExecutionException(e);
            } catch (InvocationTargetException e) {
                throw new JavaExecutionException(e.getCause());
            } finally {
                user.inActionScope = prevInActionScope;
            }
        } else {
            final State docState = doc._state();
            final boolean itsImport = action.implicitAction == DocflowConfig.ImplicitActions.CREATE && !doc._state().name.equals(DocflowConfig.ImplicitStates.NEW.toString());
            Transition transition = itsImport ? null : docState.transitionByName.get(action.name.toUpperCase());
            if (transition == null && !itsImport) {
                result.addMsg(DocflowMessages.error_DocflowActionNoAllowedInState_3, doc._fullId(), action.name, docState.name);
                return null;
            }
            final boolean localUpdate = documentUpdate == null;
            if (localUpdate) {
                documentUpdate = ActionsContext.instance().push(doc, action.name, params, result);
                if (documentUpdate == null)
                    return (action.actionMethod == null || action.actionMethod.getReturnType() == void.class) ? VOID : null;
            } else {
                documentUpdate.action = action.name;
                documentUpdate.params = params;
            }
            try {
                if (action.actionMethod != null) {
                    CurrentUser user = CurrentUser.getInstance();
                    boolean prevInActionScope = user.inActionScope;
                    user.inActionScope = true;
                    try {
                        if (action.params != null)
                            res = action.actionMethod.invoke(null, doc, params, result);
                        else
                            res = action.actionMethod.invoke(null, doc, result);
                    } catch (IllegalAccessException e) {
                        throw new JavaExecutionException(e);
                    } catch (InvocationTargetException e) {
                        throw new JavaExecutionException(e.getCause());
                    } finally {
                        user.inActionScope = prevInActionScope;
                    }
                }
                if (!itsImport) {
                    final ArrayList<Transition> conditionalTransitions = transition.conditionalTransitions;
                    if (conditionalTransitions != null)
                        for (int i = 0; i < conditionalTransitions.size(); i++) {
                            Transition conditionalTransition = conditionalTransitions.get(i);
                            try {
                                if ((Boolean) conditionalTransition.preconditionEvaluator.invoke(null, doc)) {
                                    transition = conditionalTransition;
                                    break;
                                }
                            } catch (IllegalAccessException e) {
                                throw new JavaExecutionException(e);
                            } catch (InvocationTargetException e) {
                                throw new JavaExecutionException(e);
                            }
                        }
                    if (transition.endStateModel != docState) {
                        doc._updateState(transition.endStateModel);
                        broadcastStateChange(doc, docState);
                        result.setCodeWithSeverity(Result.Ok);
                    }
                }
                documentUpdate.saveDocument();
            } catch (RuntimeException ex) {
                result.setCodeWithSeverity(Result.Failed);
                throw ex;
            } finally {
                if (localUpdate)
                    ActionsContext.instance().pop(result);
            }
        }
        if (result.isError())
            return null;
        return (action.actionMethod == null || action.actionMethod.getReturnType() == void.class) ? VOID : res;
    }

    public static void update(PersistentDocument doc, JsonNode update, Result result) {
        update(doc, update, null, null, null, result);
    }

    public static void update(PersistentDocument doc, JsonNode update, DocumentAccessActionsRights rights, Result result) {
        update(doc, update, rights, null, null, result);
    }

    public static void update(PersistentDocument doc, JsonNode update, DocumentAccessActionsRights rights, DocumentUpdate documentUpdate, PolymorphicRef subjId, Result result) {

        checkArgument(subjId == null || !doc.isPersistent());

        checkNotNull(doc, "doc");
        final DocType docType = doc._docType();

        checkNotNull(update, "update");
        checkNotNull(result, "result");
        if (rights != null)
            checkArgument(docType == rights.docType, "Expected type of rights is '%s', but it is '%s'.", doc._docType().name, rights.docType.name);

        if (rights == null)
            rights = RightsCalculator.instance.calculate(doc, CurrentUser.getInstance());

        final boolean isNewDocument = !doc.isPersistent();

        if (!isNewDocument && !rights.actionsMask.get(DocflowConfig.ImplicitActions.UPDATE.index)) {
            result.addMsg(DocflowMessages.error_DocflowInsufficientRights_2, doc._docType().name, DocflowConfig.ImplicitActions.UPDATE.toString());
            return;
        }

        boolean localUpdate = (documentUpdate == null);
        if (localUpdate) {
            documentUpdate = ActionsContext.instance().push(doc, null, null, result);
            if (documentUpdate == null)
                return;
        }
        try {
            documentUpdate.newRecords = new ArrayList<JPABase>();
            Object updateParams = null;
            if (docType.preUpdateMethod != null) {
                try {
                    updateParams = docType.preUpdateMethod.invoke(null, doc);
                } catch (IllegalAccessException e) {
                    throw new JavaExecutionException(e);
                } catch (InvocationTargetException e) {
                    throw new JavaExecutionException(e.getCause());
                }
            }

            RecordAccessor recordAccessor = docType.jsonBinder.recordAccessor;
            try {
                docType.jsonBinder.fromJson(doc, update, rights, null, null, documentUpdate, recordAccessor.getPolymorphicRef(doc), null, result);
            } catch (Exception e) {
                Logger.error(e, "Docflow: jsonBinder failed");
                result.setCode(Result.Failed);
            }
            if (subjId != null) // it's linkedDocument case
                if (subjId.isNew())
                    documentUpdate.setLinkedDocumentSubj = true;
                else {
                    try {
                        if (recordAccessor.fldSubjRef != null)
                            recordAccessor.fldSubjSetter.invoke(doc, subjId.getDocumentUnsafe());
                        else
                            recordAccessor.fldSubjSetter.invoke(doc, subjId);
                    } catch (InvocationTargetException e) {
                        throw new JavaExecutionException(e.getCause());
                    } catch (IllegalAccessException e) {
                        throw new JavaExecutionException(e);
                    }
                }

            if (!isNewDocument && documentUpdate.changes.getJson() == null)
                return;

            Action updateAction = docType.actionsArray[DocflowConfig.ImplicitActions.UPDATE.index];
            if (updateAction.actionMethod != null) {
                CurrentUser user = CurrentUser.getInstance();
                boolean prevInActionScope = user.inActionScope;
                user.inActionScope = true;
                try {
                    if (docType.preUpdateMethod != null)
                        updateAction.actionMethod.invoke(null, doc, updateParams, result);
                    else
                        updateAction.actionMethod.invoke(null, doc, result);
                    if (result.isError())
                        return;
                } catch (IllegalAccessException e) {
                    throw new JavaExecutionException(e);
                } catch (InvocationTargetException e) {
                    throw new JavaExecutionException(e.getCause());
                } finally {
                    user.inActionScope = prevInActionScope;
                }
            }
            if (localUpdate)
                documentUpdate.saveDocument();

            result.setCodeWithSeverity(Result.Ok);

        } catch (RuntimeException ex) {
            result.setCodeWithSeverity(Result.Failed);
            throw ex;
        } finally {
            if (localUpdate)
                ActionsContext.instance().pop(result);
        }
    }

    public static void delete(final PersistentDocument doc, final Result result) {
        delete(doc, true, null, result);
    }

    public static void recover(final PersistentDocument doc, final Result result) {
        delete(doc, false, null, result);
    }

    public static void delete(final PersistentDocument doc, boolean delete, DocumentAccessActionsRights rights, final Result result) {

        if (rights == null)
            rights = RightsCalculator.instance.calculate(doc, CurrentUser.getInstance());

        if (!rights.actionsMask.get(DocflowConfig.ImplicitActions.RETRIEVE.index)) {
            result.setCode(Result.NotFound);
            return;
        }

        if (!rights.actionsMask.get(delete ? DocflowConfig.ImplicitActions.DELETE.index : DocflowConfig.ImplicitActions.RECOVER.index)) {
            result.setCode(Result.ActionProhibited);
            return;
        }

        if (doc.deleted == delete) {
            result.setCode(Result.ActionOmitted);
            return;
        }

        // TODO: Check revision

        final Action action = doc._docType().actions.get((delete ? DocflowConfig.ImplicitActions.DELETE : DocflowConfig.ImplicitActions.RECOVER).toString().toUpperCase());
        if (action.actionMethod != null) {
            CurrentUser user = CurrentUser.getInstance();
            boolean prevInActionScope = user.inActionScope;
            user.inActionScope = true;
            try {
                checkState(action.params == null);
                action.actionMethod.invoke(doc, result);
            } catch (IllegalAccessException e) {
                throw new JavaExecutionException(e);
            } catch (InvocationTargetException e) {
                throw new JavaExecutionException(e);
            } finally {
                user.inActionScope = prevInActionScope;
            }
        }

        doc.rev++;
        doc.deleted = delete;
        doc.save();

        // TODO: Fix work with history for delete/restore.  Apply right src
//        DocumentUpdate.writeHistory(doc, delete ? DocflowConfig.ImplicitActions.DELETE.toString() : DocflowConfig.ImplicitActions.RECOVER.toString(), null, null, null);

    }

    public static void broadcastStateChange(PersistentDocument doc, State fromState) {
        // TODO:
    }
}
