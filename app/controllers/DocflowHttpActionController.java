package controllers;

import code.docflow.Docflow;
import code.docflow.DocflowConfig;
import code.docflow.action.ActionParams;
import code.docflow.action.Transaction;
import code.docflow.api.DocflowApiAction;
import code.docflow.api.DocflowApiCreate;
import code.docflow.api.DocflowApiDelete;
import code.docflow.api.DocflowApiUpdate;
import code.docflow.api.http.ActionResult;
import code.docflow.compiler.enums.BuiltInTemplates;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.controlflow.Result;
import code.docflow.docs.DocumentPersistent;
import code.docflow.docs.DocumentVersioned;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.jsonBinding.RecordAccessor;
import code.docflow.model.Action;
import code.docflow.model.DocType;
import code.docflow.model.Template;
import code.docflow.types.DocumentRef;
import code.docflow.users.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import docflow.DocflowMessages;
import play.db.jpa.JPAPlugin;
import play.db.jpa.Transactional;
import play.exceptions.UnexpectedException;
import play.jobs.Job;
import play.libs.F;
import play.mvc.Util;
import play.mvc.With;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

@With(CurrentUser.CheckAccess.class)
public class DocflowHttpActionController extends DocflowControllerBase {

    /**
     * Performs actions, including service actions, and returns action's result.
     *
     * @param action action name (case insensitive).
     * @param id     ether full object id or just docType, depending on action.
     * @param r      incoming document (data.doc) revision, required by some actions.
     * @param t      doc (and default) template name, optional - default 'list'.
     * @param rt     result template name, optional - default value of 't'
     * @param ot     Ok doc template name, optional - default value of 't'
     * @param body   POST body, that optionally includes 'params' and 'doc'.
     */
    @Transactional(readOnly = false)
    public static void action(String action, DocumentRef id, Integer r, String t, String rt, String ot, JsonNode body) {
        actionImpl(action, id, r, t, rt, ot, body);
    }

    // Note: actionImpl makes possible to add action(...) method in another controllers, that are under different rights

    @Util
    public static void actionImpl(final String action, final DocumentRef id, final Integer r,
                                  final String t, final String rt, final String ot, final JsonNode body) {

        final String t1 = Strings.isNullOrEmpty(t) ? BuiltInTemplates.LIST.toString() : t;
        final String rt1 = Strings.isNullOrEmpty(rt) ? t1 : rt;
        final String ot1 = Strings.isNullOrEmpty(ot) ? t1 : ot;

        final String docTypeName = id == null ? null : id.type;
        // TODO: Fix logic after changing DocumentId to DocumentRef, where id is not nullable, but if zero it means a new document
        final Long docId = id == null ? null : (id.id == 0 ? null : id.id);
        final Integer docRev = r;

        final Result result = new Result();
        result.setCode(Result.ActionOmitted);

        if (Strings.isNullOrEmpty(action) || Strings.isNullOrEmpty(docTypeName))
            error(400, "Bad parameters.");

        final DocType docType = DocflowConfig.instance.documents.get(docTypeName.toUpperCase());

        if (docType == null)
            error(400, String.format("Unknown docType: %s.", docType));

        final Action docAction = docType.actions.get(action.toUpperCase());

        if (docType == null)
            error(400, String.format("Unknown action: %s.", action));

        final Template tmpl = docType.templates.get(t1.toUpperCase());
        if (tmpl == null)
            error(400, String.format("Unknown template: '%s'.", t1));

        final Template resTmpl = docType.templates.get(rt1.toUpperCase());
        if (resTmpl == null)
            error(400, String.format("Unknown template: '%s'.", rt1));

        final Template okTmpl = docType.templates.get(ot1.toUpperCase());
        if (okTmpl == null)
            error(400, String.format("Unknown template: '%s'.", ot1));

        final boolean isCreateAction = docAction.implicitAction == CrudActions.CREATE;
        final boolean isUpdateAction = docAction.implicitAction == CrudActions.UPDATE;
        final boolean isDeleteAction = docAction.implicitAction == CrudActions.DELETE;
        final boolean isRecoverAction = docAction.implicitAction == CrudActions.RECOVER;

        if ((isCreateAction || docAction.service) && (docId != null || docRev != null))
            error(400, "Bad parameters: Action not appplicable for existing document.");

        if (!(isCreateAction || docAction.service) && (docId == null || docId < 1))
            error(400, "Bad parameters: Action required document id.");

        Transaction.scope(result, new Transaction.Delegate<Object>() {
            @Override
            public Object body(int attempt, Result result) {

                DocumentPersistent resultDoc = null;
                Object resultData = Docflow.VOID;

                final JsonNode docNode = docAction.service ? null : (body == null ? null : body.get("doc"));
                if (docNode != null && docNode.getNodeType() != JsonNodeType.OBJECT)
                    result.addMsg(DocflowMessages.error_ValidationJsonObjectExpected_2, "doc", body.get("doc"));

                ActionParams params1 = null;
                if (docAction.params != null) {

                    try {
                        params1 = (ActionParams) docAction.paramsClass.newInstance();
                    } catch (InstantiationException e) {
                        throw new UnexpectedException(e);
                    } catch (IllegalAccessException e) {
                        throw new UnexpectedException(e);
                    }

                    final JsonNode jsonParams = body.get("params");
                    if (jsonParams != null) {
                        if (jsonParams.getNodeType() != JsonNodeType.OBJECT)
                            result.addMsg(DocflowMessages.error_ValidationJsonObjectExpected_2, "params", jsonParams);

                        final CurrentUser currentUser = CurrentUser.getInstance();
                        final boolean initialInActionScope = currentUser.inActionScope;
                        try {
                            currentUser.inActionScope = true;
                            // in action is required to create DocflowFile by users without direct rights to create DocflowFile
                            JsonTypeBinder.factory.get(params1.getClass()).fromJson(params1, (ObjectNode) jsonParams, null, null, null, null, null, null, result);
                        } finally {
                            currentUser.inActionScope = initialInActionScope;
                        }
                    }
                    if (result.isError())
                        returnFailedActionResult(result);
                }

                final RecordAccessor docAccessor = docType.jsonBinder.recordAccessor;

                if (isCreateAction) {
                    resultDoc = DocflowApiCreate._create(docType, params1, (ObjectNode) docNode, null, null, result);
                    if (result.isError())
                        returnFailedActionResult(result);
                    else {
                        result.addMsg(DocflowMessages.info_HttpCreateSucceeded);
                        if (result.getCode().severity < Result.Ok.severity)
                            result.setCode(Result.Ok); // drop all technical result code to Ok
                    }

                    throw new ActionResult(resultDoc, null, ot1, rt1, result);
                }

                if (docAction.service) {
                    DocflowApiAction._action(null, docAction, params1, null, null, null, result);
                    resultData = result.getValue();
                    if (result.isError())
                        returnFailedActionResult(result);
                    else if (result.getCode().severity < Result.Ok.severity)
                        result.setCode(Result.Ok); // drop all technical result code to Ok
                    throw new ActionResult(null, resultData, t1, rt1, result);
                }

                DocumentPersistent doc = (docId == null) ? docAccessor.newRecord() : docAccessor.findById(docId);

                if (doc == null) { // can only happen on update, so docId not null
                    result.addMsg(DocflowMessages.error_DocflowDocumentNotFound_1, id);
                    returnFailedActionResult(result);
                }

                if (doc instanceof DocumentVersioned) {
                    final DocumentVersioned verDoc = (DocumentVersioned) doc;
                    // TODO: Should I required docRev in case of rev?
                    if (docRev != null && verDoc.rev != docRev) {
                        result.addMsg(DocflowMessages.error_DocflowObsoleteRevision_3, doc._fullId(), docRev, verDoc.rev);
                        // TODO: Consider adding information about concurrent user.  This would required having DSCommon own copy of DocflowMessages.
                        returnFailedActionResult(result);
                    }
                }

                if (docNode != null) {
                    doc = DocflowApiUpdate._update(doc, (ObjectNode) docNode, null, null, null, result);
                    if (result.isError())
                        returnFailedActionResult(result);
                    if (result.getCode() != Result.ActionOmitted)
                        resultDoc = doc;
                }

                if (!isUpdateAction) {

                    if (resultDoc != null)
                        Transaction.commit();

                    if (isDeleteAction || isRecoverAction) {
                        if (!(doc instanceof DocumentVersioned))
                            result.addMsg(DocflowMessages.error_CannotDeleteOrRecoverNonVersionedDocument_1, doc._fullId());
                        else {
                            doc = DocflowApiDelete._delete((DocumentVersioned) doc, isDeleteAction, result);
                            if (!result.isError())
                                resultDoc = doc;
                        }
                    } else {
                        doc = (DocumentPersistent) DocflowApiAction._action(doc, docAction, params1, null, null, null, result);
                        resultData = result.getValue();
                    }

                    if (result.isError()) {
                        resultData = null;
                        JPAPlugin.closeTx(true);
                        if (resultDoc != null) {
                            JPAPlugin.startTx(true);
                            resultDoc = docAccessor.findById(docAccessor.getId(resultDoc));
                        }
                    } else if (resultDoc != null || Transaction.instance().updatesCount > 0 ||
                            result.getCode() == Result.Ok) // Note: Ok at least means that document state has changed
                        resultDoc = doc;
                }

                if (!result.isError()) {
                    if (result.getCode().severity < Result.Ok.severity)
                        result.setCode(Result.Ok); // drop all technical result code to Ok
                    if (isUpdateAction)
                        result.addMsg(DocflowMessages.info_HttpUpdateSucceeded);
                    else if (isDeleteAction)
                        result.addMsg(DocflowMessages.info_HttpDeleteSucceeded);
                    else if (isRecoverAction)
                        result.addMsg(DocflowMessages.info_HttpRecoverSucceeded);
                }

                throw new ActionResult(resultDoc, resultData != Docflow.VOID ? resultData : null, ot1, rt1, result);
            }
        });
        returnFailedActionResult(result);
    }

    @Util
    public static void returnFailedActionResult(Result result) {
        throw new ActionResult(null, null, null, null, result);
    }
}
