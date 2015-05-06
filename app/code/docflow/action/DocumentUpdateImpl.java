package code.docflow.action;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.Docflow;
import code.docflow.compiler.enums.BuiltInActionSource;
import code.docflow.compiler.enums.BuiltInTemplates;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.controlflow.TaskJob;
import code.docflow.docs.*;
import code.docflow.model.DocType;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.jsonBinding.HistoryAccessor;
import code.docflow.jsonBinding.JsonBinding;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.types.DocumentRef;
import code.docflow.users.CurrentUser;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.DocflowFile;
import play.db.jpa.GenericModel;
import play.exceptions.UnexpectedException;
import play.jobs.Job;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Utility class used to cascade update action into other actions (including create).
 */
public final class DocumentUpdateImpl extends DocumentUpdate {

    public interface BackReference {
        void set(DocumentRef ref);
    }

    public ArrayList<GenericModel> newRecords;
    public Object updateParams;
    public boolean preCreatedFoundEqualDocument;
    public ArrayList<BackReference> backReferences;
    public boolean setLinkedDocumentSubj;

    public ArrayList<DocumentUpdateImpl> consequentUpdates;

    public ConcurrentSkipListMap<String, DocumentAccessActionsRights> historyTemplateRightsMap = new ConcurrentSkipListMap<String, DocumentAccessActionsRights>();

    public DocumentUpdateImpl(DocumentPersistent doc, String action, ActionParams params) {
        final DocType docType = doc._docType();
        this.doc = doc;
        this.action = action;
        this.params = params;
        this.newRecords = new ArrayList<GenericModel>();
        if (doc._isPersisted() && docType.historyTableName != null) {
            // TODO: Implement separated field to keep this information, until them it's not in use
//            undoNode = JsonNodeFactory.instance.objectNode();
            changesNode = JsonNodeFactory.instance.objectNode();
        }
    }

    public void saveDocument() {
        doc.save();
        for (GenericModel newRecord : newRecords)
            newRecord.save();
        if (backReferences != null) {
            final DocumentRef backRef = doc._ref();
            for (BackReference backReference : backReferences)
                backReference.set(backRef);
        }
    }

    public void saveHistoryAndLinkLinkedDocs() {
        if (!preCreatedFoundEqualDocument)
            writeHistoryAndLinkLinkedDocs(null, this);
    }

    private void writeHistoryAndLinkLinkedDocs(DocumentHistory src, DocumentUpdateImpl srcUpdate) {

        JsonTypeBinder binder = JsonTypeBinder.factory.get(doc.getClass());

        DocumentHistory history = null;

        if (doc instanceof DocumentVersioned) {

            final DocumentVersioned verDoc = (DocumentVersioned) doc;
            final HistoryAccessor ha = HistoryAccessor.factory.get(binder.type); // proxy class safe
            history = ha.newRecord(verDoc);

            history.rev = verDoc.rev;
            history.action = action;
            if (src == null) {
                final Document user = CurrentUser.getInstance().getUser();
                history.srcType = user != null ? user._fullId() : BuiltInActionSource.SYSTEM.toString();
            } else {
                history.srcType = src._docType().name;
                history.srcHistId = src.getId();
            }
            try {
                if (params != null) {
                    StringWriter sw = new StringWriter();
                    JsonGenerator generator = JsonBinding.factory.createGenerator(sw);
                    generator.disable(JsonGenerator.Feature.QUOTE_FIELD_NAMES);
                    generator.writeTree(JsonTypeBinder.factory.get(params.getClass()).toJson(params, BuiltInTemplates.ID.toString(), null, null));
                    generator.flush();
                    history.params = sw.toString();
                }
                // TODO: Add undoHistory
                if (changesNode != null) {
                    if (changesNode.size() > 0) {
                        final StringWriter sw = new StringWriter();
                        final JsonGenerator generator = JsonBinding.factory.createGenerator(sw);
                        generator.disable(JsonGenerator.Feature.QUOTE_FIELD_NAMES);
                        generator.writeTree(changesNode);
                        generator.flush();
                        generator.close();
                        history.changes = sw.toString();
                    }
                } else {
                    final DocType docType = verDoc._docType();
                    DocumentAccessActionsRights historyTemplateRights = historyTemplateRightsMap.get(docType.name);
                    if (historyTemplateRights == null) {
                        final DocumentAccessActionsRights normalRights = RightsCalculator.instance.calculate(verDoc, CurrentUser.getInstance());
                        historyTemplateRights = new DocumentAccessActionsRights();
                        historyTemplateRights.docType = normalRights.docType;
                        historyTemplateRights.viewMask = docType.notDerivedFieldsMask;
                        historyTemplateRights.updateMask = normalRights.updateMask;
                        historyTemplateRights.actionsMask = normalRights.actionsMask;
                        historyTemplateRights.retrieveMask = normalRights.retrieveMask;
                        historyTemplateRightsMap.putIfAbsent(docType.name, historyTemplateRights);
                    }
                    final StringWriter sw = new StringWriter();
                    final JsonGenerator generator = (JsonBinding.factory != null ? JsonBinding.factory : new JsonFactory()).createGenerator(sw);
                    generator.disable(JsonGenerator.Feature.QUOTE_FIELD_NAMES);
                    generator.writeTree(docType.jsonBinder.toJson(verDoc, BuiltInTemplates.HISTORY.toString(), historyTemplateRights, null));
                    generator.flush();
                    generator.close();
                    history.changes = sw.toString();
                }
            } catch (IOException e) {
                throw new UnexpectedException(e);
            }

            history.save();
        }

        if (consequentUpdates != null)
            for (DocumentUpdateImpl consequentUpdate : consequentUpdates)
                if (!consequentUpdate.preCreatedFoundEqualDocument)
                    consequentUpdate.writeHistoryAndLinkLinkedDocs(history, this);
    }

    public void runTasks() {
        if (!preCreatedFoundEqualDocument) {
            if (wasAction && action.equals(CrudActions.CREATE.toString()) && doc._docType().task)
                TaskJob.taskJobs.add(new TaskJob((DocumentPersistent) doc));
            if (consequentUpdates != null)
                for (DocumentUpdateImpl consequentUpdate : consequentUpdates)
                    consequentUpdate.runTasks();
        }
    }

    public void notifySubscribers() {
        if (!preCreatedFoundEqualDocument) {
            Docflow._dispatch(this);
            if (consequentUpdates != null)
                for (DocumentUpdateImpl consequentUpdate : consequentUpdates)
                    consequentUpdate.notifySubscribers();
        }
    }
}
