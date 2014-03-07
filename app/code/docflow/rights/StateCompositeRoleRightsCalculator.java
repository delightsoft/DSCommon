package code.docflow.rights;

import code.docflow.DocflowConfig;
import code.models.Document;
import code.models.PersistentDocument;
import code.users.CurrentUser;
import code.utils.BitArray;
import play.exceptions.JavaExecutionException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.TreeMap;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

/**
 * Builds optimized calculator for given document.state and user composite roles, where composite roles is combination
 * of user roles.
 */
class StateCompositeRoleRightsCalculator {

    final DocumentAccessActionsRights unconditionalRigths = new DocumentAccessActionsRights();
    final DocumentAccessActionsRights unconditionalRigthsInAction = new DocumentAccessActionsRights();
    final DocumentAccessActionsRights unconditionalRigthsForDeletedObjects = new DocumentAccessActionsRights();

    TreeMap<Long, DocumentAccessActionsRights> conditionalRights;
    TreeMap<Long, DocumentAccessActionsRights> conditionalInActionRights;
    TreeMap<Long, DocumentAccessActionsRights> conditionalRightsForDeletedObjects;

    Method[] relationEvaluators;
    long[] evaluatorsMasks;
    BitArray[] viewMasks;
    BitArray[] updateMasks;
    BitArray[] actionsMasks;
    BitArray[] retrieveMasks;

    DocumentAccessActionsRights calculate(Document document, CurrentUser currentUser) {

        boolean inActionScope = currentUser.inActionScope;
        final boolean deleted = document instanceof PersistentDocument ? ((PersistentDocument) document).deleted : false;

        if (relationEvaluators == null)
            return inActionScope ? unconditionalRigthsInAction : deleted ? unconditionalRigthsForDeletedObjects : unconditionalRigths;

        int end = evaluatorsMasks.length;
        long mask = (1L << end) - 1L;
        long index = 0;
        for (int i = 0; i < end; i++) {
            if ((mask & (1L << i)) == 0)
                continue;
            Method relationEvaluator = relationEvaluators[i];
            try {
                if ((Boolean) relationEvaluator.invoke(null, document, currentUser.getUser())) {
                    mask &= ~evaluatorsMasks[i];
                    index |= (1L << i);
                }
            } catch (IllegalAccessException e) {
                throw new JavaExecutionException(e);
            } catch (InvocationTargetException e) {
                throw new JavaExecutionException(e);
            }
        }

        synchronized (conditionalRights) {
            final Long objIndex = new Long(index);

            DocumentAccessActionsRights res = (inActionScope ? conditionalInActionRights :
                    deleted ? conditionalRightsForDeletedObjects : conditionalRights).get(objIndex);
            if (res != null)
                return res;

            res = new DocumentAccessActionsRights();
            res.docType = document._docType();
            res.viewMask = unconditionalRigths.viewMask.copy();
            res.updateMask = unconditionalRigths.updateMask.copy();
            res.actionsMask = unconditionalRigths.actionsMask.copy();
            res.retrieveMask = unconditionalRigths.retrieveMask.copy();

            for (int i = 0; i < end; i++) {
                if ((index & (1L << i)) == 0)
                    continue;
                res.viewMask.add(viewMasks[i]);
                res.updateMask.add(updateMasks[i]);
                res.actionsMask.add(actionsMasks[i]);
                res.retrieveMask.add(retrieveMasks[i]);
            }

            conditionalRights.put(objIndex, res);

            final boolean isRetrievable = res.actionsMask.get(DocflowConfig.ImplicitActions.RETRIEVE.index);
            final boolean isDeletable = res.actionsMask.get(DocflowConfig.ImplicitActions.DELETE.index);

            final DocumentAccessActionsRights inAction = new DocumentAccessActionsRights();
            inAction.docType = res.docType;
            inAction.viewMask = res.viewMask;
            inAction.updateMask = unconditionalRigthsInAction.updateMask;
            inAction.actionsMask = unconditionalRigthsInAction.actionsMask;
            inAction.retrieveMask = res.retrieveMask;

            conditionalInActionRights.put(objIndex, inAction);

            final DocumentAccessActionsRights forDeleted = new DocumentAccessActionsRights();
            forDeleted.docType = res.docType;
            forDeleted.viewMask = res.viewMask;
            forDeleted.updateMask = res.updateMask.copy();
            forDeleted.updateMask.clear();
            forDeleted.actionsMask = unconditionalRigthsForDeletedObjects.actionsMask.copy();
            forDeleted.actionsMask.clear();
            if (isRetrievable) {
                forDeleted.actionsMask.set(DocflowConfig.ImplicitActions.RETRIEVE.index, true);
                if (isDeletable)
                    forDeleted.actionsMask.set(DocflowConfig.ImplicitActions.RECOVER.index, true);
            }
            forDeleted.retrieveMask = res.retrieveMask;

            conditionalRightsForDeletedObjects.put(objIndex, forDeleted);

            if (!isRetrievable) {
                // Rule: If object is not retrievalbe - no other actions (only for regular rights)
                res.viewMask.clear();
                res.updateMask.clear();
                res.actionsMask.clear();
            }

            return inActionScope ? inAction : deleted ? forDeleted : res;
        }
    }
}
