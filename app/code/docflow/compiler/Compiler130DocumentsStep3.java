package code.docflow.compiler;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.compiler.enums.BuiltInActions;
import code.docflow.compiler.enums.BuiltInActionsGroups;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.model.*;
import code.docflow.yaml.YamlMessages;
import code.docflow.utils.EnumUtil;
import code.docflow.utils.NamesUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
/**
 * 1. Builds doc.actionsArray.
 * 2. Creates doc.states view/update/actions rights masks.
 * 3. Checks states transitions names - they must correspond to actions names.
 */
public class Compiler130DocumentsStep3 {

    public static void doJob(DocflowConfig docflowConfig, Result result) {
        int stateGlobalIndex = 0;
        for (DocType doc : docflowConfig.documents.values()) {

            // create actions structures
            final CrudActions[] implicitActions = CrudActions.values();
            final Action updateAction = doc.actions.get(CrudActions.UPDATE.name());
            final Action deleteAction = doc.actions.get(CrudActions.DELETE.name());
            final Action recoverAction = doc.actions.get(CrudActions.RECOVER.name());
            doc.actionsArray = new Action[doc.actions.size() + implicitActions.length
                    + (updateAction != null ? -1 : 0)
                    + (deleteAction != null ? -1 : 0)
                    + (recoverAction != null ? -1 : 0)];

            // process actions from yaml
            int ai = implicitActions.length;
            for (Action action : doc.actions.values()) {
                if (EnumUtil.isEqual(BuiltInActionsGroups.ALL, action.name)) {
                    result.addMsg(YamlMessages.error_ActionHasReservedName, doc.name, action.name);
                    continue;
                }
                if (EnumUtil.isEqual(BuiltInActions.NEWINSTANCE, action.name) && !action.service) {
                    result.addMsg(YamlMessages.error_ActionMustBeAService, doc.name, action.name);
                    continue;
                }
                action.document = doc;
                if (action != updateAction && action != deleteAction && action != recoverAction) {
                    action.index = ai;
                    doc.actionsArray[ai++] = action;
                }

                if (action.params != null) {
                    if (action == deleteAction || action == recoverAction) {
                        result.addMsg(YamlMessages.error_ActionParameterNotSupported, doc.name, action.name);
                        continue;
                    }
                    for (Field param : action.params.values()) {
                        // Rule: Enforce Java style of field naming
                        param.name = NamesUtil.turnFirstLetterInLowerCase(param.name);
                        param.fullname = param.name;
                        if (doc.module.schema == DocflowModule.Schema.V1) // in V2 and futher, nullable only fields that were explicitly marked so
                            param.nullable = !param.required;
                        param.template = "_" + doc.name + "__" + action.name + "_" + param.name;
                        if (param.type == null) {
                            final Field fldType = docflowConfig.fieldTypes.get(param.udtType.toUpperCase());
                            if (fldType == null) {
                                result.addMsg(YamlMessages.error_ActionParameterHasUnknownType, doc.name, action.name, param.name, param.udtType);
                                continue;
                            }
                            fldType.mergeTo(param);
                        }
                    }
                    action.paramsClassName = NamesUtil.turnFirstLetterInUpperCase(action.name) + "Params";
                }

                if (action.outOfForm && action.other)
                    result.addMsg(YamlMessages.error_ActionOutOfFormAndOtherInTheSameTime, doc.name, action.name);
            }

            // add implicit actions
            for (ai = 0; ai < implicitActions.length; ai++) {
                CrudActions implAction = implicitActions[ai];
                // to make possible to define params on update.  params for update will be populated by preUpdate method
                final boolean redefinedUpdateAction = implAction == CrudActions.UPDATE && updateAction != null;
                final boolean redefinedDeleteAction = implAction == CrudActions.DELETE && deleteAction != null;
                final boolean redefinedRecoverAction = implAction == CrudActions.RECOVER && recoverAction != null;
                Action action = redefinedUpdateAction ? updateAction :
                        redefinedDeleteAction ? deleteAction :
                                redefinedRecoverAction ? recoverAction :
                                        new Action();
                if (action.accessedFields == null)
                    action.accessedFields = new HashSet<String>();
                action.implicitAction = implAction;
                action.name = implAction.name().toLowerCase();
                action.document = doc;
                action.index = ai;
                // TODO: Add for delete and update action override of attributes
                action.update = implAction.update;
                if (!action.accessedFields.contains("DISPLAY"))
                    action.display = implAction.display;
                if (!action.accessedFields.contains("OTHER"))
                    action.other = implAction.other;
                if (!redefinedUpdateAction && !redefinedDeleteAction && !redefinedRecoverAction) {
                    final Action prev = doc.actions.put(action.name.toUpperCase(), action);
                    if (prev != null)
                        result.addMsg(YamlMessages.error_ActionHasReservedName, doc.name, prev.name);
                }
                doc.actionsArray[ai] = action;
            }

            doc.statesArray = new State[doc.states.size()];
            int si = 0;
            for (State state : doc.states.values()) {

                doc.statesArray[state.index = si++] = state;
                state.document = doc;
                state.globalIndex = stateGlobalIndex++;

                if (state.transitions == null)
                    continue;

                for (boolean processNonConditionalTransitions = true; ; processNonConditionalTransitions = false) {

                    for (Transition transition : state.transitions.values()) {

                        if (processNonConditionalTransitions ^ (transition.preconditions == null))
                            continue;

                        final Action action = doc.actions.get(transition.name.toUpperCase());
                        if (action == null) {
                            result.addMsg(YamlMessages.error_DocumentStateTransitionNoSuchAction, doc.name, state.name, transition.name);
                            continue;
                        }
                        transition.actionModel = action;

                        if (action.implicitAction != null && action.implicitAction != CrudActions.CREATE) {
                            result.addMsg(YamlMessages.error_DocumentStateTransitionRefersDocumentWideAction, doc.name, state.name, transition.name, action.name);
                            continue;
                        }

                        // process endState
                        State endState = doc.states.get(transition.endState.toUpperCase());
                        if (endState == null) {
                            result.addMsg(YamlMessages.error_DocumentStateTransitionNoSuchEndState, doc.name, state.name, transition.name, transition.endState);
                            continue;
                        }
                        transition.endStateModel = endState;

                        if (processNonConditionalTransitions)
                            state.transitionByName.put(transition.name.toUpperCase(), transition);
                        else {
                            final Transition unconditionalTransition = state.transitionByName.get(transition.name.toUpperCase());
                            if (unconditionalTransition == null) {
                                result.addMsg(YamlMessages.error_DocumentStateConditionalTransitionHasNoCorrespondedUnconditionalTransition, doc.name, state.name, transition.keyInNormalCase);
                                continue;
                            }

                            if (unconditionalTransition.conditionalTransitions == null)
                                unconditionalTransition.conditionalTransitions = new ArrayList<Transition>();

                            unconditionalTransition.conditionalTransitions.add(transition);

                            for (int i = 0; i < transition.preconditions.length; i++) {
                                String preconditionName = transition.preconditions[i];
                                String key = preconditionName.toUpperCase();
                                if (doc.preconditions == null)
                                    doc.preconditions = new LinkedHashMap<String, Precondition>();
                                Precondition precondition = doc.preconditions.get(key);
                                if (precondition == null) {
                                    precondition = new Precondition();
                                    precondition.name = preconditionName;
                                    doc.preconditions.put(key, precondition);
                                }
                                precondition.transitions.add(transition);
                            }
                        }
                    }

                    if (!processNonConditionalTransitions || result.isError())
                        break;
                }
            }

            // index relations
            if (doc.relations != null) {
                int i = 0;
                for (DocumentRelation relation : doc.relations.values())
                    relation.index = i++;
            }
        }
        docflowConfig.globalStatesCount = stateGlobalIndex;
    }
}
