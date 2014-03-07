package code.docflow.templateModel;

import code.docflow.DocflowConfig;
import code.docflow.model.*;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.models.Document;
import code.users.CurrentUser;
import code.utils.BitArray;
import code.utils.EnumUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class TmplDocument {

    public static final String DOCUMENT_ROOT = "doc.";

    DocType document;
    DocumentAccessActionsRights rights;

    boolean report;

    /**
     * Roots of the model.
     */
    TmplRoot root;

    /**
     * Title in form for localization.
     */
    String title;

    /**
     * Document type name.
     */
    String name;

    ImmutableList<TmplState> states;

    ImmutableList<TmplTemplate> templates;
    ImmutableMap<String, TmplTemplate> templatesByName;

    ImmutableList<TmplFilter> filters;
    ImmutableList<TmplSortOrder> sortOrders;

    TreeMap<String, String> fieldTitle = new TreeMap<String, String>();
    TreeMap<String, String> actionTitle = new TreeMap<String, String>();
    TreeMap<String, String> enumTitle = new TreeMap<String, String>();

    boolean linkedDocument;

    public static TmplDocument buildFor(TmplRoot root, DocType document, DocumentAccessActionsRights rights) {
        checkNotNull(document);
        checkNotNull(rights);

        final TmplDocument res = new TmplDocument();
        res.document = document;
        res.rights = rights;
        res.report = document.report;
        res.root = root;
        res.title = DOCUMENT_ROOT + document.name;
        res.name = document.name;
        res.linkedDocument = document.linkedDocument;

        final BitArray fieldsMask = rights.viewMask.copy();
        fieldsMask.add(rights.updateMask);

        final ImmutableList.Builder<TmplState> stateListBuilder = ImmutableList.builder();
        for (State state : document.states.values()) {
            final TmplState tmplState = TmplState.buildFor(root, res, state);
            stateListBuilder.add(tmplState);
        }
        res.states = stateListBuilder.build();

        BitArray actionsMask = rights.actionsMask;
        if (document.linkedDocument) {
            actionsMask = actionsMask.copy();
            actionsMask.set(DocflowConfig.ImplicitActions.CREATE.index, true);
        }

        final ImmutableList.Builder<TmplTemplate> templatesListBuilder = ImmutableList.builder();

        final Template listTmpl = document.templates.get(DocflowConfig.BuiltInTemplates.LIST.getUpperCase());
        if (listTmpl != null)
            addTemplate(document, rights, res, fieldsMask, templatesListBuilder, listTmpl);

        // Generate system built-in template 'create'
        if ((rights.actionsMask.get(DocflowConfig.ImplicitActions.CREATE.index) || document.linkedDocument) && !document.udt) {
            final Template form = document.templates.get(DocflowConfig.BuiltInTemplates.FORM.getUpperCase());
            final DocumentAccessActionsRights stateNewRights = (document.jsonBinder == null) ? // working with codegen project
                    rights :
                    RightsCalculator.instance.calculate((Document) document.jsonBinder.sample, CurrentUser.getInstance());
            final TmplTemplate tmplTemplate = TmplTemplate.buildFor(res, document, form, stateNewRights, true);
            tmplTemplate.name = "create";
            tmplTemplate.screen = true;
            templatesListBuilder.add(tmplTemplate);
        }

        final Template formTmpl = document.templates.get(DocflowConfig.BuiltInTemplates.FORM.getUpperCase());
        if (formTmpl != null)
            addTemplate(document, rights, res, fieldsMask, templatesListBuilder, formTmpl);

        for (Template template : document.templates.values()) {
            if (EnumUtil.isEqual(DocflowConfig.BuiltInTemplates.LIST, template.name) ||
                    EnumUtil.isEqual(DocflowConfig.BuiltInTemplates.CREATE, template.name) ||
                    EnumUtil.isEqual(DocflowConfig.BuiltInTemplates.FORM, template.name))
                continue;
            addTemplate(document, rights, res, fieldsMask, templatesListBuilder, template);
        }

        ImmutableList<TmplTemplate> templates = res.templates = templatesListBuilder.build();

        final ImmutableMap.Builder<String, TmplTemplate> templatesMapBuilder = ImmutableMap.builder();
        for (int i = 0; i < templates.size(); i++) {
            TmplTemplate template = templates.get(i);
            templatesMapBuilder.put(template.name.toUpperCase(), template);
        }
        res.templatesByName = templatesMapBuilder.build();

        ImmutableList.Builder<TmplFilter> filtersBuilder = ImmutableList.builder();
        if (document.filters != null)
            for (DocumentFilter filter : document.filters.values()) {
                final TmplFilter tmplFilter = TmplFilter.buildFor(res, filter);
                filtersBuilder.add(tmplFilter);
            }
        res.filters = filtersBuilder.build();

        ImmutableList.Builder<TmplSortOrder> ordersBuilder = ImmutableList.builder();
        if (document.sortOrders != null)
            for (DocumentSortOrder order : document.sortOrders.values()) {
                if ("_none".equals(order.name))
                    continue;
                final TmplSortOrder tmplSortOrder = TmplSortOrder.buildFor(res, order);
                ordersBuilder.add(tmplSortOrder);
            }
        res.sortOrders = ordersBuilder.build();

        return res;
    }

    private static void addTemplate(DocType document, DocumentAccessActionsRights rights, TmplDocument res, BitArray fieldsMask, ImmutableList.Builder<TmplTemplate> templatesListBuilder, Template template) {
// TODO: Reconsider - some documents, like 'document Demo report' might not have fields at all.  Also, ocasional skipping a template will cause null-pointer exceptions later in the code
//        if (fieldsMask.isIntersects(template.fieldsMask))
            templatesListBuilder.add(TmplTemplate.buildFor(res, document, template, rights, false));
    }

    public TmplRoot getRoot() {
        return root;
    }

    public String getTitle() {
        return title;
    }

    public String getName() {
        return name;
    }

    public boolean isReport() {
        return report;
    }

    public ImmutableList<TmplState> getStates() {
        return states;
    }

    public String getEnumTitle(String name) {
        return enumTitle.get(name.toUpperCase());
    }

    public String getActionTitle(String name) {
        return actionTitle.get(name.toUpperCase());
    }

    public boolean hasAction(String name) {
        Action action = document.actions.get(name.toUpperCase());
        return action == null ? false : rights.actionsMask.get(action.index);
    }

    public String getFieldTitle(String name) {
        return fieldTitle.get(name.toUpperCase());
    }

    public boolean canViewField(String name) {
        Field field = document.fieldByFullname.get(name.toUpperCase());
        return field == null ? false : rights.viewMask.get(field.index);
    }

    public boolean canUpdateField(String name) {
        Field field = document.fieldByFullname.get(name);
        return field == null ? false : rights.updateMask.get(field.index);
    }

    public ImmutableList<TmplTemplate> getTemplates() {
        return templates;
    }

    public TmplTemplate getTemplateByName(String name) {
        return templatesByName.get(name.toUpperCase());
    }

    public ImmutableList<TmplFilter> getFilters() {
        return filters;
    }

    public ImmutableList<TmplSortOrder> getSortOrders() {
        return sortOrders;
    }

    public boolean getLinkedDocument() {
        return linkedDocument;
    }
}
