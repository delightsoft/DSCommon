package controllers;

import code.docflow.DocflowConfig;
import code.docflow.compiler.enums.BuiltInTemplates;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.controlflow.Result;
import code.docflow.docs.DocumentPersistent;
import code.docflow.jsonBinding.JsonBinding;
import code.docflow.model.DocType;
import code.docflow.model.Template;
import code.docflow.queries.DocListBuilder;
import code.docflow.queries.QueryParams;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.users.CurrentUser;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import play.data.validation.Required;
import play.db.jpa.Transactional;
import play.exceptions.JavaExecutionException;
import play.exceptions.UnexpectedException;
import play.mvc.Controller;
import play.mvc.With;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

@With(CurrentUser.CheckAccess.class)
public class DocflowHttpExportController extends DocflowControllerBase {

    /**
     * @param docType Document type name
     */
    @Transactional(readOnly = true)
    public static void export(@Required String docType, String t) {

        returnIfErrors();

        final DocType docModel = DocflowConfig.instance.documents.get(docType.toUpperCase());
        if (docModel == null)
            notFound();

        final DocumentAccessActionsRights fullRights = RightsCalculator.instance.calculate(docModel, CurrentUser.getInstance().getUserRoles());
        if (!fullRights.actionsMask.get(CrudActions.RETRIEVE.index))
            notFound();

        if (Strings.isNullOrEmpty(t))
            t = BuiltInTemplates.EXPORT.toString();

        final Template tmpl = docModel.templates.get(t.toUpperCase());
        if (tmpl == null)
            error(400, String.format("Unknown template: '%s'.", t));

        final String queryKey = Strings.isNullOrEmpty(tmpl.query) ? t.toUpperCase() : tmpl.query.toUpperCase();
        Method queryMethod = docModel.queryMethods.get(queryKey);
        if (queryMethod == null)
            error(400, String.format("Not implemented query: '%s'.", t));

        final QueryParams params = new QueryParams(docModel, true, Controller.params, fullRights);
        final DocListBuilder builder;
        try {
            builder = (DocListBuilder) queryMethod.invoke(null, params);
        } catch (InvocationTargetException e) {
            throw new JavaExecutionException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new UnexpectedException(e.getCause());
        }

        final List<DocumentPersistent> list = builder.getPage();

        final ObjectNode res = JsonNodeFactory.instance.objectNode();
        final Result result = params.getResult();
        res.put("code", result.getCode().name);
        if (result.anyMessage())
            res.put("message", result.toHtml());
        else
            res.putNull("message");
        res.put("query", params.toJson());
        if (list == null) res.putNull("list");
        else res.put("list", JsonBinding.toJsonNode(list, t));
        JsonBinding.nodeToStream(res, response.out);
    }
}
