package controllers;

import code.docflow.DocflowConfig;
import code.docflow.compiler.enums.BuiltInTemplates;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.model.DocType;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.templateModel.*;
import code.docflow.users.CurrentUser;
import com.google.common.base.Strings;
import play.Logger;
import play.Play;
import play.data.validation.Required;
import play.db.jpa.Transactional;
import play.exceptions.TemplateNotFoundException;
import play.exceptions.UnexpectedException;
import play.mvc.With;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

@With(CurrentUser.CheckAccess.class)
public class DocflowHttpTemplatesController extends DocflowControllerBase {

    public static final String DEFAULT_DOC_TYPE = "_default";

    /**
     * Returns almost static html files from view/Application folder.  But those files might
     * use groovy tags (to optimize HTML coding), code/Constants (to be consistent with
     * validation rules) and Play framework localization.
     */
    @Transactional(readOnly = true)
    public static void generalTemplate(@Required String template) {

        returnIfErrors();

        template = "tmpl/" + template + ".html";

        final TmplModel model = TmplModel.factory.get(CurrentUser.getInstance().getUserRoles());

        boolean debug = request.params.get("debug") != null;

        Logger.debug("Requested: '%s'", template);

        CurrentUser currentUser = CurrentUser.getInstance();

        try {
            renderTemplate(template, model, currentUser, debug); // null, just to call proper render
        } catch (TemplateNotFoundException e) {
            Logger.error("Faild to load template: '%1$s'. Reason: '%2$s'", template, e.getMessage());
            notFound();
        }
    }

    @Transactional(readOnly = true)  // transaction is required by template generation code, so keep it
    public static void documentHtmlTemplate(String docType, String b, @Required String t, String a, String item, String tab) {

        returnIfErrors();

        if (Strings.isNullOrEmpty(b))
            b = "ngApp";

        boolean debug = request.params.get("debug") != null;

        Logger.debug("Requested: '%s/%s.html'", docType, t);

        final DocflowConfig docflow = DocflowConfig.instance;
        final DocType docModel = docflow.documents.get(docType.toUpperCase());
        if (docModel == null) {
            if (Logger.isDebugEnabled())
                Logger.debug("Unknown document type: '%s'", docType);
            notFound();
        }

        CurrentUser currentUser = CurrentUser.getInstance();

        final DocumentAccessActionsRights fullRights = RightsCalculator.instance.calculate(docModel, currentUser.getUserRoles());
        if (!fullRights.actionsMask.get(CrudActions.RETRIEVE.index)) {
            if (Logger.isDebugEnabled())
                Logger.debug("User '%1$s' does not have right to retrieve documents of type '%2$s'.",
                        currentUser, docModel.name);
            notFound();
        }

        final TmplModel model = TmplModel.factory.get(currentUser.getUserRoles());

        final TmplDocument document = model.getDocumentByName(docType.toUpperCase());
        if (document == null) {
            if (Logger.isDebugEnabled())
                Logger.debug("Unknown document type '%2$s' or user '%1$s' does not have right to retrieve documents of that type.",
                        currentUser, docType);
            notFound();
        }

        TmplTemplate template = null;
        TmplAction action = null;

        template = document.getTemplateByName(t.toUpperCase());
        if (Strings.isNullOrEmpty(a)) { // It's document form
            if (template == null) {
                if (Logger.isDebugEnabled())
                    Logger.debug("Unknown document template '%2$s' or user '%1$s' does not have right to retrieve documents of that type.",
                            currentUser, t);
                error(400, String.format("Template '%1$s' not defined", t));
            }
        } else { // It's action parameters form
            final TmplTemplate formTemplate = document.getTemplateByName(BuiltInTemplates.FORM.getUpperCase());
            action = formTemplate.getActionByName(a.toUpperCase());
            if (action == null) {
                if (Logger.isDebugEnabled())
                    Logger.debug("Unknown action '%2$s' or user '%1$s' does not have right to retrieve action of that type.",
                            currentUser, a);
                error(400, String.format("Action '%1$s' not found or not accessible", a));
            }
        }

        // Get optional http request parameter 'd'
        int dParam = (Play.mode == Play.Mode.DEV) ? 1 : 0;
        try {
            final String dStr = request.params.get("d");
            if (dStr != null)
                dParam = Integer.parseInt(dStr);
            if (!(0 <= dParam && dParam <= 2))
                dParam = 1;
        } catch (NumberFormatException e) {
            // nothing
        }

        String p = "";
        String s = (dParam == 1) ? "  " : "";
        String n = (dParam == 1) ? "\n" : "";
        boolean d = dParam >= 1;

        TmplDocument itemDocument = document;
        TmplTemplate itemFormTemplate = null;
        if (!Strings.isNullOrEmpty(item)) { // It's template for form template tab
            itemDocument = model.getDocumentByName(item.toUpperCase());
            if (itemDocument == null) {
                if (Logger.isDebugEnabled())
                    Logger.debug("Unknown document type '%2$s' or user '%1$s' does not have right to retrieve documents of that type.",
                            currentUser, item);
                error(400, String.format("Document type '%1$s' not defined", item));
            }
            itemFormTemplate = itemDocument.getTemplateByName(BuiltInTemplates.FORM.name());
            if (itemFormTemplate.getTabs() == null) { // 'item' expected to be used with 'tab', so tabs should be defined
                if (Logger.isDebugEnabled())
                    Logger.debug("Document type '%1$s': Template '%2$s': There are no tabs defined.", item, BuiltInTemplates.FORM.toString());
                error(400, String.format("Document type '%1$s' Template '%2$s': There are no tabs", item, BuiltInTemplates.FORM.toString()));
            }
        }

        if (template.getTabs() != null) {
            if (Strings.isNullOrEmpty(tab)) // If tab was not specified, then use '_main' tab as default
                tab = TmplTab.TAB_MAIN;
            if (itemFormTemplate == null)
                itemFormTemplate = itemDocument.getTemplateByName(BuiltInTemplates.FORM.name());
            TmplTab itemTab = itemFormTemplate.getTabByName(tab);
            if (itemTab == null) {
                if (Logger.isDebugEnabled())
                    Logger.debug("Document type '%1$s': Template '%2$s': There is no tab '%3$s' or it's not accessible to the user '%4$s'.",
                            item, BuiltInTemplates.FORM.toString(), tab, currentUser);
                error(400, String.format("Document type '%1$s' template '%2$s' has no tabs", item, BuiltInTemplates.FORM.toString()));
            }
            if (itemTab.getTemplate().getDocument() != document) { // Potential inconsistency: tab is linked to another type of the document
                if (Logger.isDebugEnabled())
                    Logger.debug("Document type '%1$s': Template '%2$s': Tab '%3$s' has document type, but document type '%4$s' was requested.",
                            item, BuiltInTemplates.FORM.toString(), tab, currentUser);
                error(400, String.format("Inconsistent arguments: Tab '%1$s' has document type '%2$s', but document type '%3$s' was requested",
                        tab, itemTab.getTemplate().getDocument().getName(), docType));
            }
            // This could be a tab-specific sub-template, when attribute 'fields' were specified for the tab.
            template = itemTab.getTemplate();
            // itemTab template is a sub-template, so it has no tabs.  Tabs are linked to parent template
            if (Strings.isNullOrEmpty(item))
                item = document.getName(); // assigning 'item' will force template to obtain parent template for this document.  the one that has tabs
        }

        String tmplName = b + "/doc/" + docType + "/" + t;
        try {
            renderTemplate("tmpl/" + b + "/doc/" + docType + "/" + t + ".html", model, currentUser, document, template, action, debug, item, tab, tmplName, p, s, n, d);
        } catch (TemplateNotFoundException e) {
            if (!e.getMessage().endsWith(t + ".html")) // NotFoundException while rendering template
                throw new UnexpectedException(String.format("Template '%1$s': '%2$s'",
                        docType + "/" + t + ".html", e.getMessage()));
            try {
                tmplName = b + "/doc/" + DEFAULT_DOC_TYPE + "/" + t;
                renderTemplate("tmpl/" + b + "/doc/" + DEFAULT_DOC_TYPE + "/" + t + ".html", model, currentUser, document, template, action, debug, item, tab, tmplName, p, s, n, d);
            } catch (TemplateNotFoundException e2) {
                if (!e2.getMessage().endsWith(t + ".html"))
                    throw new UnexpectedException(String.format("Template '%1$s': '%2$s'",
                            DEFAULT_DOC_TYPE + "/" + t + ".html", e2.getMessage()));
                if (Logger.isDebugEnabled())
                    Logger.debug("Missing template: '%2$s/%1$s.html' or '%3$s/%1$s.html'.", t, docType, DEFAULT_DOC_TYPE);
                notFound();
            }
        }
    }
}
