package code.docflow.action;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.DocflowConfig;
import code.docflow.model.DocType;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.jsonBinding.*;
import code.models.HistoryBase;
import code.models.PersistentDocument;
import code.users.CurrentUser;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import play.db.jpa.JPABase;
import play.exceptions.JavaExecutionException;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListMap;

import static com.google.common.base.Preconditions.checkState;

/**
 * Utility class used to cascade update action into other actions (including create).
 */
public final class DocumentUpdate {

    public PersistentDocument doc;
    public String action;
    public ActionParams params;
    public ArrayList<JPABase> newRecords;
    public boolean wasUpdate;
    public boolean wasAction;
    public boolean preCreatedFoundEqualDocument;
    public boolean setLinkedDocumentSubj;

    public JsonBindingChanges changes;
    public JsonGenerator changesGenerator;
    public ArrayList<DocumentUpdate> consequentUpdates = new ArrayList<DocumentUpdate>();

    public ConcurrentSkipListMap<String, DocumentAccessActionsRights> historyTemplateRightsMap = new ConcurrentSkipListMap<String, DocumentAccessActionsRights>();

    public DocumentUpdate(PersistentDocument doc, String action, ActionParams params) {
        this.doc = doc;
        this.action = action;
        this.params = params;
        this.newRecords = new ArrayList<JPABase>();
        if (doc.isPersistent()) {
            checkState(changes == null);
            changes = new JsonBindingChanges(JsonBinding.factory);
            changesGenerator = changes.getJsonGenerator();
        }
    }

    public void saveDocument() {
        doc.save();
        for (JPABase newRecord : newRecords)
            newRecord._save();
    }

    public void saveHistoryAndLinkLinkedDocs() {
        if (!preCreatedFoundEqualDocument)
            writeHistoryAndLinkLinkedDocs(null, this);
    }

    private void writeHistoryAndLinkLinkedDocs(HistoryBase src, DocumentUpdate srcUpdate) {

        JsonTypeBinder binder = JsonTypeBinder.factory.get(doc.getClass());
        final HistoryAccessor ha = HistoryAccessor.factory.get(binder.type); // proxy class safe
        final HistoryBase history = ha.newRecord(doc);

        if (setLinkedDocumentSubj) {
            final RecordAccessor recordAccessor = binder.recordAccessor;
            try {
                final PersistentDocument srcDoc = srcUpdate.doc;
                if (recordAccessor.fldSubjRef != null)
                    recordAccessor.fldSubjSetter.invoke(doc, srcDoc);
                else {
                    final RecordAccessor srcDocAccessor = JsonTypeBinder.factory.get(srcDoc.getClass()).recordAccessor;
                    recordAccessor.fldSubjSetter.invoke(doc, srcDocAccessor.getPolymorphicRef(srcDoc));
                }
                doc.save();
            } catch (InvocationTargetException e) {
                throw new JavaExecutionException(e.getCause());
            } catch (IllegalAccessException e) {
                throw new JavaExecutionException(e);
            }
        }

        history.rev = doc.rev;
        history.action = action;
        if (src == null)
            history.srcType = DocflowConfig.BuiltInActionSource.SYSTEM.toString();
        else {
            history.srcType = src._docType().name;
            history.srcHistId = src.getId();
        }
        if (params != null) {
            try {
                StringWriter sw = new StringWriter();
                JsonGenerator gen = JsonBinding.factory.createGenerator(sw);
                gen.disable(JsonGenerator.Feature.QUOTE_FIELD_NAMES);
                JsonTypeBinder.factory.get(params.getClass()).toJson(params, DocflowConfig.BuiltInTemplates.ID.toString(), gen, null, null);
                gen.flush();
                history.params = sw.toString();
            } catch (IOException e) {
                throw new JavaExecutionException(e);
            }
        }
        if (changes != null)
            history.changes = changes.getJson();
        else
            try {
                final StringWriter sw = new StringWriter();
                final JsonGenerator gen = (JsonBinding.factory != null ? JsonBinding.factory : new JsonFactory()).createGenerator(sw);
                gen.disable(JsonGenerator.Feature.QUOTE_FIELD_NAMES);
                final DocType docType = doc._docType();
                DocumentAccessActionsRights historyTemplateRights = historyTemplateRightsMap.get(docType.name);
                if (historyTemplateRights == null) {
                    final DocumentAccessActionsRights normalRights = RightsCalculator.instance.calculate(doc, CurrentUser.getInstance());
                    historyTemplateRights = new DocumentAccessActionsRights();
                    historyTemplateRights.docType = normalRights.docType;
                    historyTemplateRights.viewMask = docType.notDerivedFieldsMask;
                    historyTemplateRights.updateMask = normalRights.updateMask;
                    historyTemplateRights.actionsMask = normalRights.actionsMask;
                    historyTemplateRights.retrieveMask = normalRights.retrieveMask;
                    historyTemplateRightsMap.putIfAbsent(docType.name, historyTemplateRights);
                }
                docType.jsonBinder.toJson(doc, DocflowConfig.BuiltInTemplates.HISTORY.toString(), gen, historyTemplateRights, null);
                gen.flush();
                history.changes = sw.toString();
            } catch (IOException e) {
                throw new JavaExecutionException(e);
            }

        history.save();

        for (DocumentUpdate consequentUpdate : consequentUpdates)
            if (!consequentUpdate.preCreatedFoundEqualDocument)
                consequentUpdate.writeHistoryAndLinkLinkedDocs(history, this);
    }
}
