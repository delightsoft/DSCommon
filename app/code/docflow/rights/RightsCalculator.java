package code.docflow.rights;

import code.docflow.DocflowConfig;
import code.docflow.model.*;
import code.models.Document;
import code.users.CurrentUser;
import code.utils.BitArray;
import com.google.common.base.Preconditions;
import play.Play;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static com.google.common.base.Preconditions.checkState;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

public class RightsCalculator {

    public static RightsCalculator instance = new RightsCalculator();

    public static void _resetForTest() {
        checkState(Play.mode == Play.Mode.DEV);
        instance = new RightsCalculator();
    }

    /**
     * Map by user's composite role (comma deleimited list of Roles) of thread-save vectors of rights calculater per State.
     */
    private ConcurrentSkipListMap<String, AtomicReferenceArray<StateCompositeRoleRightsCalculator>> stateCompositeRoleRightsCalculators;
    private ConcurrentSkipListMap<String, AtomicReferenceArray<DocumentAccessActionsRights>> docTypeCompositeRoleRights;

    protected RightsCalculator() {
        stateCompositeRoleRightsCalculators = new ConcurrentSkipListMap<String, AtomicReferenceArray<StateCompositeRoleRightsCalculator>>();
        docTypeCompositeRoleRights = new ConcurrentSkipListMap<String, AtomicReferenceArray<DocumentAccessActionsRights>>();
    }

    /**
     * Rights calculator, which supports exactly the same logic as referenceCalculator(), but makes
     * it in a way that will dramatically increase performance in case of mass rights checks.
     * <p/>
     * <b>It's a thread-safe optimized method.</b>
     */
    public DocumentAccessActionsRights calculate(Document document, CurrentUser user) {

        if (document._docType() == null)
            return null;

        final String userRoles = user.getUserRoles();
        AtomicReferenceArray<StateCompositeRoleRightsCalculator> stateCompositeRoleRightsCalculatorsArray = stateCompositeRoleRightsCalculators.get(userRoles);

        if (stateCompositeRoleRightsCalculatorsArray == null) {
            stateCompositeRoleRightsCalculatorsArray = new AtomicReferenceArray<StateCompositeRoleRightsCalculator>(DocflowConfig.instance.globalStatesCount);
            AtomicReferenceArray<StateCompositeRoleRightsCalculator> prev = stateCompositeRoleRightsCalculators.putIfAbsent(userRoles, stateCompositeRoleRightsCalculatorsArray);
            if (prev != null)
                stateCompositeRoleRightsCalculatorsArray = prev;
        }

        final State state = document._state();
        if (state == null)
            return null;
        final int stateIndex = state.globalIndex;
        StateCompositeRoleRightsCalculator calculator = stateCompositeRoleRightsCalculatorsArray.get(stateIndex);
        if (calculator == null) {
            String[] roles = userRoles.split(",");
            calculator = buildCalculator(state, roles);
            stateCompositeRoleRightsCalculatorsArray.lazySet(stateIndex, calculator);
        }
        return calculator.calculate(document, user);
    }

    /**
     * Calculates rights for document type in whole.
     * <p/>
     * <b>It's a thread-safe optimized method.</b>
     */
    public DocumentAccessActionsRights calculate(DocType docType, final String userRoles) {
        AtomicReferenceArray<DocumentAccessActionsRights> docTypeCompositeRoleRightsArray = docTypeCompositeRoleRights.get(userRoles);
        if (docTypeCompositeRoleRightsArray == null) {
            docTypeCompositeRoleRightsArray = new AtomicReferenceArray<DocumentAccessActionsRights>(DocflowConfig.instance.documentsArray.length);
            final AtomicReferenceArray<DocumentAccessActionsRights> prev = docTypeCompositeRoleRights.putIfAbsent(userRoles, docTypeCompositeRoleRightsArray);
            if (prev != null)
                docTypeCompositeRoleRightsArray = prev;
        }

        DocumentAccessActionsRights rights = docTypeCompositeRoleRightsArray.get(docType.index);
        if (rights == null) {
            rights = new DocumentAccessActionsRights();
            rights.docType = docType;

            for (String roleName : userRoles.split(",")) {
                final Role role = DocflowConfig.instance.roles.get(roleName.toUpperCase());
                final RoleDocument roleDocument = role != null ? role.documents.get(docType.name.toUpperCase()) : null;
                if (roleDocument != null) {
                    if (rights.viewMask == null) {
                        rights.viewMask = roleDocument.viewMask.copy();
                        rights.updateMask = roleDocument.updateMask.copy();
                        rights.actionsMask = roleDocument.actionsMask.copy();
                    } else {
                        rights.viewMask.add(roleDocument.viewMask);
                        rights.updateMask.add(roleDocument.updateMask);
                        rights.actionsMask.add(roleDocument.actionsMask);
                    }
                    if (roleDocument.relations != null) {
                        int i = 0;
                        for (Relation relation : roleDocument.relations) {
                            rights.viewMask.add(relation.viewMask);
                            rights.updateMask.add(relation.updateMask);
                            rights.actionsMask.add(relation.actionsMask);
                            i++;
                        }
                    }
                }
            }

            if (rights.viewMask == null) {
                rights.viewMask = rights.updateMask = new BitArray(docType.allFields.size());
                rights.actionsMask = new BitArray(docType.actionsArray.length);
            } else {
                rights.viewMask.intersect(docType.fullViewMask);
                rights.updateMask.intersect(docType.fullUpdateMask);
                rights.actionsMask.intersect(docType.fullActionsMask);
            }

            // Rule: If full-rights contain DELETE action they also must include RECOVER action
            if (rights.actionsMask.get(DocflowConfig.ImplicitActions.DELETE.index))
                rights.actionsMask.set(DocflowConfig.ImplicitActions.RECOVER.index, true);

            // Rule: !create => !newInstance
            final Action newInstanceAction = docType.actions.get(DocflowConfig.BuiltInActions.NEW_INSTANCE.getUpperCase());
            if (newInstanceAction != null && !rights.actionsMask.get(DocflowConfig.ImplicitActions.CREATE.index))
                rights.actionsMask.set(newInstanceAction.index, false);

            docTypeCompositeRoleRightsArray.lazySet(docType.index, rights);
        }

        return rights;
    }

    private static class RelationInfo {
        Method relationEvaluator;
        BitArray viewMask;
        BitArray updateMask;
        BitArray actionsMask;
        BitArray retrieveMask;
    }

    private static final Comparator<RelationInfo> relationSortRule = new Comparator<RelationInfo>() {
        public int compare(RelationInfo left, RelationInfo right) {
            if (right == null)
                return 1;
            if (left == null)
                return -1;
            if (left.viewMask.isImplicated(right.viewMask) ||
                    left.updateMask.isImplicated(right.updateMask) ||
                    left.actionsMask.isImplicated(right.actionsMask))
                return 1;
            if (right.viewMask.isImplicated(left.viewMask) ||
                    right.updateMask.isImplicated(left.updateMask) ||
                    right.actionsMask.isImplicated(left.actionsMask))
                return -1;
            return 0;
        }
    };

    private StateCompositeRoleRightsCalculator buildCalculator(State state, String[] roles) {

        final DocflowConfig docflow = DocflowConfig.instance;
        final DocType document = state.document;

        final StateCompositeRoleRightsCalculator res = new StateCompositeRoleRightsCalculator();

        final DocumentAccessActionsRights unconditionalRigths = res.unconditionalRigths;
        unconditionalRigths.docType = state.document;

        // 1. Calc unconditional rights.  If no relation in roleDocument - done
        for (int i = 0; i < roles.length; i++) {
            final String roleName = roles[i];
            final Role role = docflow.roles.get(roleName.toUpperCase());
            final RoleDocument roleDocument = role != null ? role.documents.get(document.name.toUpperCase()) : null;
            if (roleDocument == null)
                continue;
            if (unconditionalRigths.viewMask == null) {
                unconditionalRigths.viewMask = roleDocument.viewMask.copy();
                unconditionalRigths.updateMask = roleDocument.updateMask.copy();
                unconditionalRigths.actionsMask = roleDocument.actionsMask.copy();
            } else {
                unconditionalRigths.viewMask.add(roleDocument.viewMask);
                unconditionalRigths.updateMask.add(roleDocument.updateMask);
                unconditionalRigths.actionsMask.add(roleDocument.actionsMask);
            }
        }
        if (unconditionalRigths.viewMask == null) {
            unconditionalRigths.viewMask = unconditionalRigths.updateMask = new BitArray(document.allFields.size());
            unconditionalRigths.actionsMask = new BitArray(document.actionsArray.length);
        } else {
            unconditionalRigths.viewMask.intersect(state.viewMask);
            unconditionalRigths.updateMask.intersect(state.updateMask);
            unconditionalRigths.actionsMask.intersect(state.actionsMask);
        }

        if (document.relations != null)
            unconditionalRigths.retrieveMask = new BitArray(document.relations.size());

        final boolean isRetrievable = unconditionalRigths.actionsMask.get(DocflowConfig.ImplicitActions.RETRIEVE.index);
        final boolean isDeletable = unconditionalRigths.actionsMask.get(DocflowConfig.ImplicitActions.DELETE.index);

        final boolean isNewState = state.index == 0;
        final DocumentAccessActionsRights unconditionalRigthsInAction = res.unconditionalRigthsInAction;
        unconditionalRigthsInAction.docType = unconditionalRigths.docType;
        unconditionalRigthsInAction.viewMask = unconditionalRigths.viewMask;
        // Rule: In inAction mode actions's java code can update all fields, except fields from implicit group
        unconditionalRigthsInAction.updateMask = document.implicitFieldsMask.copy();
        unconditionalRigthsInAction.updateMask.inverse();
        if (isNewState) {
            final Field stateFld = document.fieldByFullname.get(DocflowConfig.ImplicitFields.STATE.name());
            if (stateFld != null)
                unconditionalRigthsInAction.updateMask.set(stateFld.index, true);
        }
        unconditionalRigthsInAction.actionsMask = state.actionsMask.copy();
        // Rule: In inAction mode action's java code can perform all actions allowed in the state, plus actions - update/delete/recover
        unconditionalRigthsInAction.actionsMask.set(DocflowConfig.ImplicitActions.RETRIEVE.index, true);
        if (!isNewState) {
            unconditionalRigthsInAction.actionsMask.set(DocflowConfig.ImplicitActions.UPDATE.index, true);
            unconditionalRigthsInAction.actionsMask.set(DocflowConfig.ImplicitActions.DELETE.index, true);
            unconditionalRigthsInAction.actionsMask.set(DocflowConfig.ImplicitActions.RECOVER.index, true);
        }
        unconditionalRigthsInAction.retrieveMask = unconditionalRigths.retrieveMask;

        final DocumentAccessActionsRights unconditionalRigthsForDeletedObjects = res.unconditionalRigthsForDeletedObjects;
        unconditionalRigthsForDeletedObjects.docType = unconditionalRigths.docType;
        unconditionalRigthsForDeletedObjects.viewMask = unconditionalRigths.viewMask;
        unconditionalRigthsForDeletedObjects.updateMask = unconditionalRigths.updateMask.copy();
        unconditionalRigthsForDeletedObjects.updateMask.clear();
        unconditionalRigthsForDeletedObjects.actionsMask = unconditionalRigths.actionsMask.copy();
        unconditionalRigthsForDeletedObjects.actionsMask.clear();
        if (isRetrievable) {
            unconditionalRigthsForDeletedObjects.actionsMask.set(DocflowConfig.ImplicitActions.RETRIEVE.index, true);
            if (isDeletable)
                unconditionalRigthsForDeletedObjects.actionsMask.set(DocflowConfig.ImplicitActions.RECOVER.index, true);
        }
        unconditionalRigthsForDeletedObjects.retrieveMask = unconditionalRigths.retrieveMask;

        if (document.relations != null) {

            // 2. Filter out relations that are not implicated into state rights
            final RelationInfo[] relationsMasks = new RelationInfo[document.relations.size()];
            for (int i = 0; i < roles.length; i++) {
                final String roleName = roles[i];
                final Role role = docflow.roles.get(roleName.toUpperCase());
                final RoleDocument roleDocument = role.documents.get(document.name.toUpperCase());
                if (roleDocument != null && roleDocument.relations != null) {
                    int j = 0;
                    for (Relation relation : roleDocument.relations) {
                        final int relationIndex = relation.documentRelation.index;
                        RelationInfo r = relationsMasks[relationIndex];
                        if (r == null) {
                            r = relationsMasks[relationIndex] = new RelationInfo();
                            r.relationEvaluator = relation.documentRelation.evaluator;
                            r.viewMask = relation.viewMask.copy();
                            r.updateMask = relation.updateMask.copy();
                            r.actionsMask = relation.actionsMask.copy();
                            r.retrieveMask = new BitArray(roleDocument.relations.length);
                            r.retrieveMask.set(j, true);
                        } else {
                            r.viewMask.add(relation.viewMask);
                            r.updateMask.add(relation.updateMask);
                            r.actionsMask.add(relation.actionsMask);
                        }
                        j++;
                    }
                }
            }

            BitArray nonUnconditionalView = unconditionalRigths.viewMask.copy();
            nonUnconditionalView.inverse();
            BitArray nonUnconditionalUpdate = unconditionalRigths.updateMask.copy();
            nonUnconditionalUpdate.inverse();
            BitArray nonUnconditionalActions = unconditionalRigths.actionsMask.copy();
            nonUnconditionalActions.inverse();
            for (int j = 0; j < relationsMasks.length; j++) {
                RelationInfo relationsMask = relationsMasks[j];
                if (relationsMask == null)
                    continue;
                relationsMask.viewMask.intersect(state.viewMask);
                relationsMask.viewMask.intersect(nonUnconditionalView);
                relationsMask.updateMask.intersect(state.updateMask);
                relationsMask.updateMask.intersect(nonUnconditionalUpdate);
                relationsMask.actionsMask.intersect(state.actionsMask);
                relationsMask.actionsMask.intersect(nonUnconditionalActions);
                if (relationsMask.viewMask.isEmpty() && relationsMask.updateMask.isEmpty() && relationsMask.actionsMask.isEmpty())
                    relationsMasks[j] = null;
            }

            // 3. Sort remaining relations - implicated comes later
            Arrays.sort(relationsMasks, relationSortRule);

            // 4. Generate array for relations calculation.  Done
            int relationsCount = 0;
            for (RelationInfo relationsMask : relationsMasks)
                if (relationsMask != null)
                    relationsMasks[relationsCount++] = relationsMask;

            if (relationsCount > 0) {

                // Note: Right Calculator code written with presumption that there would be way mauch less then 64 relations
                // involved into computation of rigths for pair state / composite role.
                Preconditions.checkState(relationsCount <= 64);

                res.conditionalRights = new TreeMap<Long, DocumentAccessActionsRights>();
                res.conditionalInActionRights = new TreeMap<Long, DocumentAccessActionsRights>();
                res.conditionalRightsForDeletedObjects = new TreeMap<Long, DocumentAccessActionsRights>();
                res.relationEvaluators = new Method[relationsCount];
                res.evaluatorsMasks = new long[relationsCount];
                res.viewMasks = new BitArray[relationsCount];
                res.updateMasks = new BitArray[relationsCount];
                res.actionsMasks = new BitArray[relationsCount];
                res.retrieveMasks = new BitArray[relationsCount];

                for (int i = 0; i < relationsCount; i++) {
                    if (relationsMasks[i] == null)
                        continue;
                    long mask = 0;
                    for (int j = i + 1; j < relationsCount; j++)
                        if (relationsMasks[j] != null)
                            if (relationsMasks[i].viewMask.isImplicated(relationsMasks[j].viewMask) &&
                                    relationsMasks[i].updateMask.isImplicated(relationsMasks[j].updateMask) &&
                                    relationsMasks[i].actionsMask.isImplicated(relationsMasks[j].actionsMask))
                                mask |= 1L << j;
                    res.relationEvaluators[i] = relationsMasks[i].relationEvaluator;
                    res.evaluatorsMasks[i] = mask;
                    res.viewMasks[i] = relationsMasks[i].viewMask;
                    res.updateMasks[i] = relationsMasks[i].updateMask;
                    res.actionsMasks[i] = relationsMasks[i].actionsMask;
                    res.retrieveMasks[i] = relationsMasks[i].retrieveMask;
                }
            }
        }

        return res;
    }
}
