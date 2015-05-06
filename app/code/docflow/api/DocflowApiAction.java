package code.docflow.api;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.Docflow;
import code.docflow.action.Transaction;
import code.docflow.action.ActionParams;
import code.docflow.action.DocumentUpdateImpl;
import code.docflow.compiler.enums.BuiltInStates;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.controlflow.Result;
import code.docflow.docs.DocumentPersistent;
import code.docflow.model.Action;
import code.docflow.model.State;
import code.docflow.model.Transition;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.types.DocumentRef;
import code.docflow.users.CurrentUser;
import docflow.DocflowMessages;
import play.exceptions.UnexpectedException;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class DocflowApiAction {
    public static Object _action(DocumentPersistent doc, Action action, ActionParams params, DocumentAccessActionsRights rights, DocumentUpdateImpl documentUpdate, DocumentRef subjId, Result result) {
        checkNotNull(action, "action");
        checkNotNull(result, "result");

        if (action.service)
            checkArgument(doc == null, "For service action '%s' argument 'docType' must be null.", action.name);
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

        final CurrentUser user = CurrentUser.getInstance();

        if (!user.inActionScope && subjId == null) { // this is not create some linkedDocument
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
            final boolean prevInActionScope = user.inActionScope;
            try {
                user.inActionScope = true;
                if (action.params != null)
                    res = action.actionMethod.invoke(null, params, result);
                else
                    res = action.actionMethod.invoke(null, result);
            } catch (IllegalAccessException e) {
                throw new UnexpectedException(e);
            } catch (InvocationTargetException e) {
                throw new UnexpectedException(e.getCause());
            } finally {
                user.inActionScope = prevInActionScope;
            }
        } else {
            doc = doc._attached();
            final State docState = doc._state();
            final boolean itsImport = action.implicitAction == CrudActions.CREATE && !doc._state().name.equals(BuiltInStates.NEW.toString());
            Transition transition = itsImport ? null : docState.transitionByName.get(action.name.toUpperCase());
            if (transition == null && !itsImport) {
                result.addMsg(DocflowMessages.error_DocflowActionNoAllowedInState_3, doc._fullId(), action.name, docState.name);
                return doc;
            }
            final boolean localUpdate = documentUpdate == null;
            if (localUpdate) {
                documentUpdate = Transaction.instance().push(doc, action.name, params, result);
                if (documentUpdate == null) {
                    result.setValue((action.actionMethod == null || action.actionMethod.getReturnType() == void.class) ? Docflow.VOID : null);
                    return doc;
                }
            } else {
                documentUpdate.action = action.name;
                documentUpdate.params = params;
            }
            try {
                if (action.actionMethod != null) {
                    final boolean prevInActionScope = user.inActionScope;
                    try {
                        user.inActionScope = true;
                        if (action.params != null)
                            res = action.actionMethod.invoke(null, doc, params, result);
                        else
                            res = action.actionMethod.invoke(null, doc, result);
                    } catch (IllegalAccessException e) {
                        throw new UnexpectedException(e);
                    } catch (InvocationTargetException e) {
                        throw new UnexpectedException(e.getCause());
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
                                throw new UnexpectedException(e);
                            } catch (InvocationTargetException e) {
                                throw new UnexpectedException(e);
                            }
                        }
                    if (transition.endStateModel != docState) {
                        doc._updateState(transition.endStateModel);
                        result.setCodeWithSeverity(Result.Ok);
                    }
                }
                if (localUpdate)
                    documentUpdate.saveDocument();
            } catch (Throwable e) {
                result.addException(e);
                return doc;
            } finally {
                if (localUpdate)
                    Transaction.instance().pop(result);
            }
        }

        if (result.isError())
            return doc;

        result.setValue((action.actionMethod == null || action.actionMethod.getReturnType() == void.class) ? Docflow.VOID : res);
        return doc;
    }
}
