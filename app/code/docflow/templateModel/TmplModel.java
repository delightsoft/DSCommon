package code.docflow.templateModel;

import code.docflow.DocflowConfig;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.model.DocType;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.utils.BitArray;
import code.docflow.utils.Builder;
import code.docflow.utils.TemplatesBuildersFactory;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import play.Logger;
import play.Play;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class TmplModel extends Builder {

    public static TemplatesBuildersFactory<TmplModel> factory = new TemplatesBuildersFactory<TmplModel>() {
        @Override
        public TmplModel newInstance(String roles) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(roles));
            return buildFor(roles, false);
        }
    };

    public static TemplatesBuildersFactory<TmplModel> factoryWithUdtDocument = new TemplatesBuildersFactory<TmplModel>() {
        @Override
        public TmplModel newInstance(String roles) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(roles));
            return buildFor(roles, true);
        }
    };

    String userRoles;
    ImmutableList<TmplDocument> documents;
    ImmutableMap<String, TmplDocument> documentByName;

    protected TmplModel() {
    }

    @Override
    protected void init() {
        // nothing
    }

    protected static TmplModel buildFor(final String userRoles, boolean withUdtDocument) {

        if (userRoles == null) {
            final String MSG = "TmplRoot.buildFor is used without user rights.  It's only acceptable within development environment.";
            if (Play.mode == Play.Mode.DEV) {
                Logger.warn(MSG);
            } else
                throw new UnsupportedOperationException(MSG);
        }

        final TmplModel res = new TmplModel();

        res.userRoles = userRoles;

        ImmutableList.Builder<TmplDocument> docListBuilder = ImmutableList.builder();

        final DocType[] docs = DocflowConfig.instance.documentsArray;
        // TODO: Failes here during app load, if there were prior compilations errors
        for (int i = 0; i < docs.length; i++) {
            DocType document = docs[i];

            if (document.udt && !withUdtDocument)
                continue;

            DocumentAccessActionsRights fullRights = null;
            if (userRoles != null && !document.udt)
                fullRights = RightsCalculator.instance.calculate(document, userRoles);
            else {
                fullRights = new DocumentAccessActionsRights();
                fullRights.docType = document;
                fullRights.viewMask = fullRights.updateMask = new BitArray(document.allFields.size());
                fullRights.actionsMask = new BitArray(document.actionsArray.length);
                fullRights.viewMask.inverse();
                fullRights.actionsMask.inverse();
            }

            if (!fullRights.actionsMask.get(CrudActions.RETRIEVE.index) && !document.udt)
                continue;
            final TmplDocument tdocument = TmplDocument.buildFor(res, document, fullRights);
            docListBuilder.add(tdocument);
        }
        res.documents = docListBuilder.build();

        ImmutableMap.Builder<String, TmplDocument> docMapBuilder = ImmutableMap.builder();
        for (int i = 0; i < res.documents.size(); i++) {
            TmplDocument tmplDocument = res.documents.get(i);
            docMapBuilder.put(tmplDocument.name.toUpperCase(), tmplDocument);
        }
        res.documentByName = docMapBuilder.build();

        for (int i = 0; i < res.documents.size(); i++) {
            TmplDocument tmplDocument = res.documents.get(i);
            if (tmplDocument.templates != null)
                for (int j = 0; j < tmplDocument.templates.size(); j++) {
                    TmplTemplate tmplTemplate = tmplDocument.templates.get(j);
                    if (tmplTemplate.tabs != null)
                        for (int k = 0; k < tmplTemplate.tabs.size(); k++) {
                            TmplTab tmplTab = tmplTemplate.tabs.get(k);
                            tmplTab.linkToTemplate(res);
                        }
                }
        }

        return res;
    }

    public ImmutableList<TmplDocument> getDocuments() {
        return documents;
    }

    public TmplDocument getDocumentByName(String name) {
        checkNotNull(name);
        return documentByName.get(name.toUpperCase());
    }
}
