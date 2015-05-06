package code.docflow.templateModel;

import code.docflow.compiler.enums.BuiltInTemplates;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.model.*;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.docs.Document;
import code.docflow.users.CurrentUser;
import code.docflow.utils.BitArray;
import code.docflow.utils.EnumUtil;
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
    TmplModel model;

    /**
     * Title in form for localization.
     */
    String title;

    /**
     * Document type name.
     */
    String name;

    ImmutableList<TmplField> fields;
    ImmutableMap<String, TmplField> fieldByName;

    ImmutableList<TmplAction> actions;
    ImmutableMap<String, TmplAction> actionByName;

    ImmutableList<TmplState> states;

    ImmutableList<TmplTemplate> templates;
    ImmutableMap<String, TmplTemplate> templatesByName;

    ImmutableList<TmplFilter> filters;
    ImmutableList<TmplSortOrder> sortOrders;

    TreeMap<String, String> fieldTitle = new TreeMap<String, String>();
    TreeMap<String, String> actionTitle = new TreeMap<String, String>();
    TreeMap<String, String> enumTitle = new TreeMap<String, String>();

    boolean linkedDocument;

    public static TmplDocument buildFor(TmplModel root, DocType docType, DocumentAccessActionsRights rights) {
        checkNotNull(docType);
        checkNotNull(rights);

        final TmplDocument res = new TmplDocument();
        res.document = docType;
        res.rights = rights;
        res.report = docType.report;
        res.model = root;
        res.title = DOCUMENT_ROOT + docType.name;
        res.name = docType.name;
        res.linkedDocument = docType.linkedDocument;

        final BitArray fieldsMask = rights.viewMask.copy();
        fieldsMask.add(rights.updateMask);

        final BitArray mask = rights.viewMask.copy();
        mask.add(rights.updateMask);

        final ImmutableList.Builder<TmplField> fldListBuilder = ImmutableList.builder();
        for (Field field : docType.entities.get(0).fields) { // docType.entities.get(0).fields includes implicit fields
            if (!mask.get(field.index))
                continue;
            final TmplField tf = TmplField.buildFor(res, null, null, null, null, field, rights, mask,
                    false, rights.viewMask.get(field.index), rights.updateMask.get(field.index));
            fldListBuilder.add(tf);
        }
        ImmutableList<TmplField> fields = res.fields = fldListBuilder.build();

        final ImmutableMap.Builder<String, TmplField> fldMapBuilder = ImmutableMap.builder();
        for (TmplField tmplField : fields)
            fldMapBuilder.put(tmplField.fullname.toUpperCase(), tmplField);
        res.fieldByName = fldMapBuilder.build();

        if (docType.udt) // udt happend here in codeGen, so we could take proper l18n titles
            return res;

        final ImmutableList.Builder<TmplAction> actionsListBuilder = ImmutableList.builder();

        BitArray fixedActionsMask = rights.actionsMask.copy();
        if (docType.linkedDocument) {
            // to make possible localization of create button for linked docs, even known that linked
            // document can be create only by assigning to field of its subj
            fixedActionsMask.set(CrudActions.CREATE.index, true);
        }
        fixedActionsMask.set(CrudActions.DELETE.index, true);
        fixedActionsMask.set(CrudActions.RECOVER.index, true);

        final BitArray.EnumTrueValues actionsIterator = fixedActionsMask.getEnumTrueValues();
        int actionIndex;
        while ((actionIndex = actionsIterator.next()) != -1) {
            final Action action = docType.actionsArray[actionIndex];
            final TmplAction ta = TmplAction.buildFor(res, docType, action);
            actionsListBuilder.add(ta);
        }
        ImmutableList<TmplAction> actions = res.actions = actionsListBuilder.build();

        final ImmutableMap.Builder<String, TmplAction> actionsMapBuilder = ImmutableMap.builder();
        for (TmplAction tmplAction : actions)
            actionsMapBuilder.put(tmplAction.name.toUpperCase(), tmplAction);
        res.actionByName = actionsMapBuilder.build();

        final ImmutableList.Builder<TmplState> stateListBuilder = ImmutableList.builder();
        for (State state : docType.states.values()) {
            final TmplState tmplState = TmplState.buildFor(root, res, state);
            stateListBuilder.add(tmplState);
        }
        res.states = stateListBuilder.build();

        BitArray actionsMask = rights.actionsMask;
        if (docType.linkedDocument) {
            actionsMask = actionsMask.copy();
            actionsMask.set(CrudActions.CREATE.index, true);
        }

        final ImmutableList.Builder<TmplTemplate> templatesListBuilder = ImmutableList.builder();
        final Template listTmpl = docType.templates.get(BuiltInTemplates.LIST.getUpperCase());
        if (listTmpl != null)
            addTemplate(docType, rights, res, fieldsMask, templatesListBuilder, listTmpl);

        // Generate system built-in template 'create'
        if ((rights.actionsMask.get(CrudActions.CREATE.index) || docType.linkedDocument) && !docType.udt) {
            final Template form = docType.templates.get(BuiltInTemplates.FORM.getUpperCase());
            final DocumentAccessActionsRights stateNewRights = (docType.jsonBinder == null) ? // working with codegen project
                    rights :
                    RightsCalculator.instance.calculate((Document) docType.jsonBinder.sample, CurrentUser.getInstance());
            final TmplTemplate tmplTemplate = TmplTemplate.buildFor(res, docType, form, stateNewRights, true);
            tmplTemplate.name = BuiltInTemplates.CREATE.toString();
            tmplTemplate.screen = true;
            templatesListBuilder.add(tmplTemplate);
        }

        final Template formTmpl = docType.templates.get(BuiltInTemplates.FORM.getUpperCase());
        if (formTmpl != null)
            addTemplate(docType, rights, res, fieldsMask, templatesListBuilder, formTmpl);

        for (Template template : docType.templates.values()) {
            if (EnumUtil.isEqual(BuiltInTemplates.LIST, template.name) ||
                    EnumUtil.isEqual(BuiltInTemplates.CREATE, template.name) ||
                    EnumUtil.isEqual(BuiltInTemplates.FORM, template.name))
                continue;
            addTemplate(docType, rights, res, fieldsMask, templatesListBuilder, template);
        }

        ImmutableList<TmplTemplate> templates = res.templates = templatesListBuilder.build();

        final ImmutableMap.Builder<String, TmplTemplate> templatesMapBuilder = ImmutableMap.builder();
        for (int i = 0; i < templates.size(); i++) {
            TmplTemplate template = templates.get(i);
            templatesMapBuilder.put(template.name.toUpperCase(), template);
        }
        res.templatesByName = templatesMapBuilder.build();

        ImmutableList.Builder<TmplFilter> filtersBuilder = ImmutableList.builder();
        if (docType.filters != null)
            for (DocumentFilter filter : docType.filters.values()) {
                final TmplFilter tmplFilter = TmplFilter.buildFor(res, filter);
                filtersBuilder.add(tmplFilter);
            }
        res.filters = filtersBuilder.build();

        ImmutableList.Builder<TmplSortOrder> ordersBuilder = ImmutableList.builder();
        if (docType.sortOrders != null)
            for (DocumentSortOrder order : docType.sortOrders.values()) {
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

    public TmplModel getModel() {
        return model;
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

    public ImmutableList<TmplField> getFields() {
        return fields;
    }

    public TmplField getFieldByName(String name) {
        return fieldByName.get(name.toUpperCase());
    }

    public ImmutableList<TmplAction> getActions() {
        return actions;
    }

    public TmplAction getActionByName(String name) {
        return actionByName.get(name.toUpperCase());
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

    // TODO: Reconsider: When I moved back getFields and getAction from TmplTemplate to TmplDocument, this method seems to be odd
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
