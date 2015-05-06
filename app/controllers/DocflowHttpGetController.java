package controllers;

import code.docflow.DocflowConfig;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.docs.DocumentPersistent;
import code.docflow.model.DocType;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.jsonBinding.JsonBinding;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.jsonBinding.RecordAccessor;
import code.docflow.docs.Document;
import code.docflow.types.DocumentRef;
import code.docflow.users.CurrentUser;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.Strings;
import play.db.jpa.JPAPlugin;
import play.db.jpa.Transactional;
import play.jobs.Job;
import play.libs.F;
import play.mvc.With;

import java.io.IOException;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

@With(CurrentUser.CheckAccess.class)
public class DocflowHttpGetController extends DocflowControllerBase {

    /**
     * Returns document with given id or Http error.
     *
     * @param id full object id.
     * @param t  result template name, optional.
     */
    @Transactional(readOnly = true)
    public static void get(DocumentRef id, String t) throws IOException {

        final String docType = id.type;
        final Long docId = id.id;

        if (Strings.isNullOrEmpty(docType) || docId == null || docId < 1)
            error(400, "Bad parameters");

        final DocType docModel = DocflowConfig.instance.documents.get(docType.toUpperCase());
        if (docModel == null)
            notFound();

        final DocumentAccessActionsRights fullRights = RightsCalculator.instance.calculate(docModel, CurrentUser.getInstance().getUserRoles());

        if (!fullRights.actionsMask.get(CrudActions.RETRIEVE.index))
            notFound();

        Template tmpl = null;
        if (!Strings.isNullOrEmpty(t)) {
            tmpl = docModel.templates.get(t.toUpperCase());
            if (tmpl == null)
                error(400, "Bad parameters: Unknown template");
        }

        final RecordAccessor docAccessor = RecordAccessor.factory.get(docModel.jsonBinder.type);

        Document res = docAccessor.findById(docId);
        if (res == null)
            notFound();

        // TODO: Remove. This is an experement, would await() works here
//        final F.Promise now = new Job<Object>() {
//            @Override
//            public void doJob() throws Exception {
//                Thread.sleep(4000);
//            }
//        }.now();
//
//        await(now);
//        if (res._isPersisted()) {
//            JPAPlugin.startTx(false);
//            res = ((DocumentPersistent) res)._attached();
//        }

        response.contentType = "application/json";
        final JsonGenerator generator = JsonBinding.factory.createGenerator(response.out);
        try {
            final JsonTypeBinder binder = JsonTypeBinder.factory.get(res.getClass());
            generator.writeTree(binder.toJson(res, tmpl, null, null));
            generator.flush();
        } finally {
            generator.close();
        }
    }
}
