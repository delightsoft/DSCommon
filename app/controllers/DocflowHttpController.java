package controllers;

import code.DocumentId;
import code.controlflow.Result;
import code.docflow.Docflow;
import code.docflow.DocflowConfig;
import code.docflow.action.ActionParams;
import code.docflow.action.ActionsContext;
import code.docflow.model.Action;
import code.docflow.model.DocType;
import code.docflow.model.Template;
import code.docflow.queries.Query;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.templateModel.TmplAction;
import code.docflow.templateModel.TmplDocument;
import code.docflow.templateModel.TmplRoot;
import code.docflow.templateModel.TmplTemplate;
import code.jsonBinding.JsonBinding;
import code.jsonBinding.JsonTypeBinder;
import code.jsonBinding.RecordAccessor;
import code.jsonBinding.binders.type.TypeBinder;
import code.models.Document;
import code.models.PersistentDocument;
import code.users.CurrentUser;
import code.utils.CsvWriter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import docflow.DocflowMessages;
import play.Logger;
import play.Play;
import play.db.jpa.JPAPlugin;
import play.db.jpa.Transactional;
import play.exceptions.JavaExecutionException;
import play.exceptions.TemplateNotFoundException;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Util;
import play.mvc.With;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
@With(CurrentUser.CheckAccess.class)
public class DocflowHttpController extends Controller {

    public static final String DEFAULT_DOC_TYPE = "_Default";

    public static void app() {
        String baseUrl = Play.configuration.getProperty("application.baseUrl", "http://localhost:9000/");
        render(baseUrl);
    }

    /**
     * Returns almost static html files from view/Application folder.  But those files might
     * use groovy tags (to optimize HTML coding), code/Constants (to be consistent with
     * validation rules) and Play framework localization.
     */
    @Transactional(readOnly = true)  // transaction is required by template generation code, so keep it
    public static void generalTemplate(String template) {

        if (Strings.isNullOrEmpty(template))
            error(400, "Missing argument template");

        template = "tmpl/" + template + ".html";

        final TmplRoot model = TmplRoot.factory.get(CurrentUser.getInstance().getUserRoles());

        boolean debug = request.params.get("debug") != null;

        Logger.debug("Requested: '%s'", template);
        try {
            renderTemplate(template, model, debug); // null, just to call proper render
        } catch (TemplateNotFoundException e) {
            Logger.error("Missing file: '%s'", template);
            notFound();
        }
    }

    @Transactional(readOnly = true)  // transaction is required by template generation code, so keep it
    public static void documentHtmlTemplate(String docType, String t, String a, String item, String tab) {

        if (Strings.isNullOrEmpty(t))
            error("Missing parameter 't'");

        boolean debug = request.params.get("debug") != null;

        Logger.debug("Requested: '%s/%s.html'", docType, t);

        final DocflowConfig docflow = DocflowConfig.instance;
        final DocType docModel = docflow.documents.get(docType.toUpperCase());
        if (docModel == null) {
            if (Logger.isDebugEnabled())
                Logger.debug("Unknown document type: '%s'", docType);
            notFound();
        }

        final DocumentAccessActionsRights fullRights = RightsCalculator.instance.calculate(docModel, CurrentUser.getInstance().getUserRoles());
        if (!fullRights.actionsMask.get(DocflowConfig.ImplicitActions.RETRIEVE.index)) {
            if (Logger.isDebugEnabled())
                Logger.debug("User '%1$s' does not have right to retrieve documents of type '%2$s'.",
                        CurrentUser.getInstance().toString(), docModel.name);
            notFound();
        }

        final TmplRoot model = TmplRoot.factory.get(CurrentUser.getInstance().getUserRoles());

        final TmplDocument document = model.getDocumentByName(docType.toUpperCase());
        if (document == null) {
            if (Logger.isDebugEnabled())
                Logger.debug("Unknown document type '%2$s' or user '%1$s' does not have right to retrieve documents of that type.",
                        CurrentUser.getInstance().toString(), docType);
            notFound();
        }

        TmplTemplate template = null;
        TmplAction action = null;

        if (Strings.isNullOrEmpty(a)) {
            template = document.getTemplateByName(t.toUpperCase());
            if (template == null) {
                if (Logger.isDebugEnabled())
                    Logger.debug("Unknown document template %2$s' or user '%1$s' does not have right to retrieve documents of that type.",
                            CurrentUser.getInstance().toString(), t);
                notFound();
            }
        } else {
            final TmplTemplate formTemplate = document.getTemplateByName(DocflowConfig.BuiltInTemplates.FORM.getUpperCase());
            action = formTemplate.getActionByName(a.toUpperCase());
            if (action == null) {
                if (Logger.isDebugEnabled())
                    Logger.debug("Unknown action '%2$s' or user '%1$s' does not have right to retrieve action of that type.",
                            CurrentUser.getInstance().toString(), a);
                notFound();
            }
        }

        try {
            renderTemplate("tmpl/doc/" + docType + "/" + t + ".html", document, template, action, debug, item, tab);
        } catch (TemplateNotFoundException e) {
            try {
                renderTemplate("tmpl/doc/" + DEFAULT_DOC_TYPE + "/" + t + ".html", document, template, action, debug, item, tab);
            } catch (TemplateNotFoundException e2) {
                if (Logger.isDebugEnabled())
                    Logger.debug("Missing template: '%2$s/%1$s.html' or '%3$s/%1$s.html'.", t, docType, DEFAULT_DOC_TYPE);
                notFound();
            }
        }
    }

    /**
     * @param docType Document type name
     */
    @Transactional(readOnly = true)
    public static void list(String docType, String out, String t) throws IOException, NoSuchFieldException, InvocationTargetException, IllegalAccessException {

        if (Strings.isNullOrEmpty(docType))
            error(400, "Bad parameters");

        final boolean isCsv = (out != null && "csv".equalsIgnoreCase(out));

        final DocType docModel = DocflowConfig.instance.documents.get(docType.toUpperCase());
        if (docModel == null)
            notFound();

        final DocumentAccessActionsRights fullRights = RightsCalculator.instance.calculate(docModel, CurrentUser.getInstance().getUserRoles());
        if (!fullRights.actionsMask.get(DocflowConfig.ImplicitActions.RETRIEVE.index))
            notFound();

        if (Strings.isNullOrEmpty(t))
            t = "list";

        final Template tmpl = docModel.templates.get(t.toUpperCase());
        if (tmpl == null)
            error(400, String.format("Unknown template: '%s'.", t));

        final Method queryMethod = docModel.queryMethods.get(Strings.isNullOrEmpty(tmpl.query) ? t.toUpperCase() : tmpl.query.toUpperCase());
        if (queryMethod == null)
            error(400, String.format("Not implemented query: '%s'.", t));

        final Query.Result res;
        try {
            res = (Query.Result) queryMethod.invoke(null, request, fullRights);
        } catch (InvocationTargetException e) {
            throw new JavaExecutionException(e.getCause());
        }

        if (!isCsv) { // json
            response.contentType = "application/json";
            final JsonGenerator generator = JsonBinding.factory.createGenerator(response.out);
            try {
                final JsonTypeBinder binder = JsonTypeBinder.factory.get(Query.Result.class);
                binder.toJson(res, t, generator, null, null);
                generator.flush();
            } finally {
                generator.close();
            }
        } else { // svn
            // TODO: Remove limits from request
            if (res.csvFormatter == null)
                error(400, "CSV is not supported");

            // Hack: Encoding is hardcoded

            response.encoding = "windows-1251";
            response.contentType = "text/csv; encoding=windows-1251";
            response.setHeader("Content-disposition", String.format("attachment;filename=%s.xls",
                    Strings.isNullOrEmpty(res.csvFormatter.filename) ? "report" : res.csvFormatter.filename));

            // Windows-1251
            final CsvWriter csvWriter = new CsvWriter(new OutputStreamWriter(response.out, "windows-1251"));

            // BOM: UTF-16
//            response.out.write(255);
//            response.out.write(254);
//            final CSVWriter csvWriter = new CSVWriter(new OutputStreamWriter(response.out, "utf-16"), '\t', CSVWriter.NO_QUOTE_CHARACTER);

//            // BOM: UTF-8
//            response.out.write(239);
//            response.out.write(187);
//            response.out.write(191);
//            // TODO: Tab delimiter doesn't work in UTF-8 xls
//            final CSVWriter csvWriter = new CSVWriter(new PrintWriter(response.out), '\t', CSVWriter.NO_QUOTE_CHARACTER);
            res.csvFormatter.writeHeader(csvWriter, fullRights);
            for (Object line : res.list) {
                csvWriter.newLine();
                res.csvFormatter.writeLine((Document) line, csvWriter, fullRights);
            }
            csvWriter.close();
        }
    }

    /**
     * Returns document with given id or Http error.
     *
     * @param id full object id.
     * @param t  result template name, optional.
     */
    @Transactional(readOnly = true)
    public static void get(DocumentId id, String t) throws IOException {

        final String docType = id.docType;
        final Long docId = id.docId;

        if (Strings.isNullOrEmpty(docType) || docId == null || docId < 1)
            error(400, "Bad parameters");

        final DocType docModel = DocflowConfig.instance.documents.get(docType.toUpperCase());
        if (docModel == null)
            notFound();

        final DocumentAccessActionsRights fullRights = RightsCalculator.instance.calculate(docModel, CurrentUser.getInstance().getUserRoles());

        if (!fullRights.actionsMask.get(DocflowConfig.ImplicitActions.RETRIEVE.index))
            notFound();

        Template tmpl = null;
        if (!Strings.isNullOrEmpty(t)) {
            tmpl = docModel.templates.get(t.toUpperCase());
            if (tmpl == null)
                error(400, "Bad parameters: Unknown template");
        }

        final RecordAccessor docAccessor = RecordAccessor.factory.get(docModel.jsonBinder.type);

        final Document res = docAccessor.findById(docId);
        if (res == null)
            notFound();

        response.contentType = "application/json";
        final JsonGenerator generator = JsonBinding.factory.createGenerator(response.out);
        try {
            final JsonTypeBinder binder = JsonTypeBinder.factory.get(res.getClass());
            binder.toJson(res, tmpl, generator, null, null);
            generator.flush();
        } finally {
            generator.close();
        }
    }

    /**
     * Performs actions, including service actions, and returns action's result.
     *
     * @param action action name (case insensitive).
     * @param id     ether full object id or just docType, depending on action.
     * @param r      incoming document (data.doc) revision, required by some actions.
     * @param t      result template name, optional.
     * @param body   POST body, that optionally includes 'params' and 'doc'.
     */
    @Transactional(readOnly = false)
    public static void action(String action, DocumentId id, Integer r, String t, String rt, JsonNode body) throws IOException {
        actionImpl(action, id, r, t, rt, body);
    }

    @Util
    public static void actionImpl(String action, DocumentId id, Integer r, String t, String rt, JsonNode body) throws IOException {

        final String docType = id == null ? null : id.docType;
        final Long docId = id == null ? null : id.docId;
        final Integer docRev = r;

        final Result result = new Result();
        result.setCode(Result.ActionOmitted);

        if (Strings.isNullOrEmpty(action) || Strings.isNullOrEmpty(docType))
            error(400, "Bad parameters.");

        final DocType docModel = DocflowConfig.instance.documents.get(docType.toUpperCase());

        if (docModel == null)
            error(400, String.format("Unknown docType: %s.", docType));

        final Action docAction = docModel.actions.get(action.toUpperCase());

        if (docModel == null)
            error(400, String.format("Unknown action: %s.", action));

        if (Strings.isNullOrEmpty(t))
            t = DocflowConfig.BuiltInTemplates.LIST.toString();

        final Template tmpl = docModel.templates.get(t.toUpperCase());
        if (tmpl == null)
            error(400, String.format("Unknown template: '%s'.", t));

        if (!Strings.isNullOrEmpty(rt)) {
            final Template resTmpl = docModel.templates.get(rt.toUpperCase());
            if (resTmpl == null)
                error(400, String.format("Unknown template: '%s'.", rt));
        }

        final boolean isCreateAction = docAction.implicitAction == DocflowConfig.ImplicitActions.CREATE;
        final boolean isUpdateAction = docAction.implicitAction == DocflowConfig.ImplicitActions.UPDATE;
        final boolean isDeleteAction = docAction.implicitAction == DocflowConfig.ImplicitActions.DELETE;
        final boolean isRecoverAction = docAction.implicitAction == DocflowConfig.ImplicitActions.RECOVER;

        if ((isCreateAction || docAction.service) && (docId != null || docRev != null))
            error(400, "Bad parameters: Action not appplicable for existing document.");

        if (!(isCreateAction || docAction.service) && (docId == null || docId < 1))
            error(400, "Bad parameters: Action required document id.");

        PersistentDocument resultDoc = null;
        Object resultData = Docflow.VOID;

        final JsonNode docNode = docAction.service ? null : (body == null ? null : body.get("doc"));

        ActionParams params = null;
        if (docAction.params != null) {

            try {
                params = (ActionParams) docAction.paramsClass.newInstance();
            } catch (InstantiationException e) {
                throw new JavaExecutionException(e);
            } catch (IllegalAccessException e) {
                throw new JavaExecutionException(e);
            }

            final JsonNode jsonParams = body.get("params");
            if (jsonParams != null)
                JsonTypeBinder.factory.get(params.getClass()).fromJson(params, jsonParams, null, null, null, null, null, null, result);
            if (result.isError())
                returnFailedActionResult(result);
        }

        final RecordAccessor docAccessor = docModel.jsonBinder.recordAccessor;

        if (isCreateAction) {
            resultDoc = Docflow.create(docModel, params, docNode, null, null, result);
            if (result.isError())
                returnFailedActionResult(result);
            else {
                result.addMsg(DocflowMessages.info_HttpCreateSucceeded);
                if (result.getCode().severity < Result.Ok.severity)
                    result.setCode(Result.Ok); // drop all technical result code to Ok
            }
            throw new ActionResult(resultDoc, null, t, rt, result);
        }

        if (docAction.service) {
            resultData = Docflow.action(null, docAction, params, null, null, null, result);
            if (result.isError())
                returnFailedActionResult(result);
            else if (result.getCode().severity < Result.Ok.severity)
                result.setCode(Result.Ok); // drop all technical result code to Ok
            throw new ActionResult(null, resultData, t, rt, result);
        }

        PersistentDocument doc = (docId == null) ? docAccessor.newRecord() : docAccessor.findById(docId);

        if (doc == null) { // can only happen on update, so docId not null
            result.addMsg(DocflowMessages.error_DocflowDocumentNotFound_1, id);
            returnFailedActionResult(result);
        }

        // TODO: Should I required docRev in case of rev?
        if (docRev != null && doc.rev != docRev) {
            result.addMsg(DocflowMessages.error_DocflowObsoleteRevision_3, doc._fullId(), docRev, doc.rev);
            // TODO: Consider adding information about concurrent user.  This would required having DSCommon own copy of DocflowMessages.
            returnFailedActionResult(result);
        }

        if (docNode != null) {
            Docflow.update(doc, docNode, null, null, null, result);
            if (result.isError())
                returnFailedActionResult(result);
            if (result.getCode() != Result.ActionOmitted)
                resultDoc = doc;
        }

        if (!isUpdateAction) {

            if (resultDoc != null) {
                JPAPlugin.closeTx(false);
                JPAPlugin.startTx(false);
                doc = resultDoc = docAccessor.findById(docAccessor.getId(doc));
            }

            if (isDeleteAction || isRecoverAction) {
                Docflow.delete(doc, isDeleteAction, null, result);
                if (!result.isError())
                    resultDoc = doc;
            } else
                resultData = Docflow.action(doc, docAction, params, null, null, null, result);

            if (result.isError()) {
                resultData = null;
                JPAPlugin.closeTx(true);
                if (resultDoc != null) {
                    JPAPlugin.startTx(true);
                    resultDoc = docAccessor.findById(docAccessor.getId(resultDoc));
                }
            } else if (resultDoc != null || ActionsContext.instance().updatesCount > 0 ||
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

        throw new ActionResult(resultDoc, resultData != Docflow.VOID ? resultData : null, t, rt, result);
    }

    private static void returnFailedActionResult(Result result) throws IOException {
        JPAPlugin.closeTx(true);
        throw new ActionResult(null, null, null, null, result);
    }

    public static class ActionResult extends play.mvc.results.Result {
        Result result;
        String template;
        String resultTemplate;

        StringWriter docJson;
        StringWriter resultJson;

        public ActionResult(PersistentDocument resultDoc, Object resultData, String template, String resultTemplate, Result result) {
            this.result = result;
            this.template = template;
            this.resultTemplate = resultTemplate;

            if (resultDoc != null)
                try {
                    final JsonGenerator generator = JsonBinding.factory.createGenerator(docJson = new StringWriter());
                    JsonTypeBinder.factory.get(resultDoc.getClass()).toJson(resultDoc, template, generator, null, null);
                    generator.flush();
                    generator.close();
                } catch (IOException e) {
                    throw new JavaExecutionException(e);
                }

            if (resultData != null)
                try {
                    final JsonGenerator generator = JsonBinding.factory.createGenerator(resultJson = new StringWriter());
                    final String templateName = Strings.isNullOrEmpty(resultTemplate) ? template : resultTemplate;
                    if (resultData instanceof Document) {
                        JsonTypeBinder.factory.get(resultData.getClass()).toJson(resultData,
                                templateName, generator, null, null);
                    } else
                        try {
                            TypeBinder.factory.get(resultData.getClass()).copyToJson(resultData, new JsonTypeBinder.TemplateName(templateName), generator, null, 0);
                        } catch (Exception e) {
                            throw new JavaExecutionException(e);
                        }

                    generator.flush();
                    generator.close();
                } catch (IOException e) {
                    throw new JavaExecutionException(e);
                }
        }

        @Override
        public void apply(Http.Request request, Http.Response response) {

            response.contentType = "application/json";
            response.status = 200; // business logic errors are not considered as HTTP protocol failures

            try {
                final JsonGenerator generator = JsonBinding.factory.createGenerator(response.out);
                try {
                    generator.writeStartObject();

                    generator.writeStringField("code", result.getCode().name);

                    if (result.anyMessage())
                        generator.writeStringField("message", result.toHtml());
                    else
                        generator.writeNullField("message");

                    if (docJson != null) {
                        generator.writeFieldName("doc");
                        generator.writeRaw(":");
                        generator.writeRaw(docJson.toString());
                    }

                    if (resultJson != null) {
                        // Note: Jackson do not changes stream state to next field after writeRaw()
                        generator.writeRaw(",\"result\":");
                        generator.writeRaw(resultJson.toString());
                    }

                    generator.writeEndObject();
                    generator.flush();

                } catch (RuntimeException e) {
                    Logger.error("Result rendering: Exception: %s", e);
                    throw e;
                } finally {
                    generator.close();
                }
            } catch (IOException e) {
                throw new JavaExecutionException(e);
            }
        }
    }
}
