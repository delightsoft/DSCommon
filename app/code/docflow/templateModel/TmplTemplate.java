package code.docflow.templateModel;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.DocflowConfig;
import code.docflow.collections.Item;
import code.docflow.model.*;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.jsonBinding.JsonTypeBinder;
import code.utils.BitArray;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import static com.google.common.base.Preconditions.checkNotNull;

public class TmplTemplate {

    /**
     * Document this field belongs to.
     */
    TmplDocument document;

    /**
     * Title in form for localization.
     */
    String title;

    /**
     * Simple name of the template.
     */
    String name;

    boolean screen;

    ImmutableList<TmplField> fields;
    ImmutableMap<String, TmplField> fieldByName;

    ImmutableList<TmplField> firstFields;
    ImmutableList<TmplField> secondFields;

    ImmutableList<TmplAction> actions;
    ImmutableMap<String, TmplAction> actionByName;

    ImmutableList<TmplTab> tabs;
    ImmutableMap<String, TmplTab> tabByName;

    ImmutableList<TmplField> columns;
    ImmutableMap<String, TmplField> columnByName;

    @SuppressWarnings("unchecked")
    public static TmplTemplate buildFor(TmplDocument document, DocType docType, Template template,
                                        DocumentAccessActionsRights rights, boolean isCreateTemplate) {
        checkNotNull(template);

        final TmplTemplate res = new TmplTemplate();
        res.document = document;
        res.title = "doc." + document.name + ".tmpl." + (isCreateTemplate ? "create" : template.name);
        res.name = template.name;
        res.screen = template.screen;

        rights = fixRightsInRespectToTemplateMode(template, rights);

        final BitArray mask = rights.viewMask.copy();
        mask.add(rights.updateMask);
        mask.intersect(template.fieldsMask);

        boolean readonly = (template.modeMask & JsonTypeBinder.GENERATE__U) == 0;

        boolean anySecond = false;
        final ImmutableList.Builder<TmplField> fldListBuilder = ImmutableList.builder();
        for (Field field : docType.entities.get(0).fields) { // docType.entities.get(0).fields includes implicit fields
            if (!mask.get(field.index))
                continue;
            if (field.second)
                anySecond = true;
            final TmplField tf = TmplField.buildFor(document, res, null, null, template, field, rights, mask,
                    readonly, rights.viewMask.get(field.index) || (readonly && rights.updateMask.get(field.index)),
                    readonly ? false : rights.updateMask.get(field.index));
            tf.document = document;
            fldListBuilder.add(tf);
        }
        ImmutableList<TmplField> fields = res.fields = fldListBuilder.build();

        final ImmutableList.Builder<TmplField> secondFldListBuilder = ImmutableList.builder();
        if (anySecond) {
            final ImmutableList.Builder<TmplField> firstFldListBuilder = ImmutableList.builder();
            for (int i = 0; i < fields.size(); i++) {
                TmplField fld = fields.get(i);
                if (fld.getSecond())
                    secondFldListBuilder.add(fld);
                else
                    firstFldListBuilder.add(fld);
            }
            res.firstFields = firstFldListBuilder.build();
        }
        else {
            res.firstFields = res.fields;
        }
        res.secondFields = secondFldListBuilder.build();

        final ImmutableMap.Builder<String, TmplField> fldMapBuilder = ImmutableMap.builder();
        for (int i = 0; i < fields.size(); i++) {
            TmplField tmplField = fields.get(i);
            fldMapBuilder.put(tmplField.fullname.toUpperCase(), tmplField);
        }
        res.fieldByName = fldMapBuilder.build();

        final ImmutableList.Builder<TmplAction> actionsListBuilder = ImmutableList.builder();

        BitArray fixedActionsMask = rights.actionsMask.copy();
        if (docType.linkedDocument) {
            // to make possible localization of create button for linked docs, even known that linked
            // document can be create only by assigning to field of its subj
            fixedActionsMask.set(DocflowConfig.ImplicitActions.CREATE.index, true);
        }
        fixedActionsMask.set(DocflowConfig.ImplicitActions.DELETE.index, true);
        fixedActionsMask.set(DocflowConfig.ImplicitActions.RECOVER.index, true);

        final BitArray.EnumTrueValues actionsIterator = fixedActionsMask.getEnumTrueValues();
        int actionIndex;
        while ((actionIndex = actionsIterator.next()) != -1) {
            final Action action = docType.actionsArray[actionIndex];
            final TmplAction ta = TmplAction.buildFor(document, docType, action);
            ta.document = document;
            actionsListBuilder.add(ta);
        }
        ImmutableList<TmplAction> actions = res.actions = actionsListBuilder.build();

        final ImmutableMap.Builder<String, TmplAction> actionsMapBuilder = ImmutableMap.builder();
        for (int i = 0; i < actions.size(); i++) {
            TmplAction tmplAction = actions.get(i);
            actionsMapBuilder.put(tmplAction.name.toUpperCase(), tmplAction);
        }
        res.actionByName = actionsMapBuilder.build();

        if (template.tabs != null && !isCreateTemplate) {
            final ImmutableList.Builder<TmplTab> tabsListBuilder = ImmutableList.builder();
            for (TemplateTab tab : template.tabs.values()) {

                // Rule: When tab refers inaccessible by this user docType, this tab get skipped
                DocType tabDocType = DocflowConfig.instance.documents.get(tab.docType.toUpperCase());
                String userRoles = document.root.userRoles;
                if (userRoles != null) {
                    DocumentAccessActionsRights docTypeRights = RightsCalculator.instance.calculate(tabDocType, userRoles);
                    if (!docTypeRights.actionsMask.get(DocflowConfig.ImplicitActions.RETRIEVE.index))
                        continue;
                }

                final TmplTab tmplTab = TmplTab.buildFor(res, tab);
                tabsListBuilder.add(tmplTab);
            }
            ImmutableList<TmplTab> tabs = res.tabs = tabsListBuilder.build();

            final ImmutableMap.Builder<String, TmplTab> tabsMapBuilder = ImmutableMap.builder();
            for (int i = 0; i < tabs.size(); i++) {
                TmplTab tmplTab = tabs.get(i);
                tabsMapBuilder.put(tmplTab.name.toUpperCase(), tmplTab);
            }
            res.tabByName = tabsMapBuilder.build();
        }

        if (template.columns != null) {
            final ImmutableList.Builder<TmplField> columnsListBuilder = ImmutableList.builder();
            for (Item column : template.columns.values()) {
                final String fieldName = column.name;
                TmplField tmplField = null;
                if (column.name.equalsIgnoreCase(DocflowConfig.FIELD_SELF))
                    tmplField = TmplField.buildSelfFieldFor(document.root, res, document);
                else {
                    tmplField = res.fieldByName.get(fieldName.toUpperCase());
                    if (tmplField == null)
                        continue;
                }
                columnsListBuilder.add(tmplField);
            }
            ImmutableList<TmplField> columns = res.columns = columnsListBuilder.build();

            final ImmutableMap.Builder<String, TmplField> columnsMapBuilder = ImmutableMap.builder();
            for (int i = 0; i < columns.size(); i++) {
                TmplField fld = columns.get(i);
                columnsMapBuilder.put(fld.name.toUpperCase(), fld);
            }
            res.columnByName = columnsMapBuilder.build();
        }

        return res;
    }

    private static DocumentAccessActionsRights fixRightsInRespectToTemplateMode(Template template, DocumentAccessActionsRights r) {
        DocumentAccessActionsRights rights;
        rights = new DocumentAccessActionsRights();
        rights.viewMask = r.viewMask;

        if ((template.modeMask & JsonTypeBinder.GENERATE__U) == 0)
            rights.updateMask = new BitArray(r.updateMask.size());
        else
            rights.updateMask = r.updateMask;

        if ((template.modeMask & JsonTypeBinder.GENERATE_$A) == 0)
            rights.actionsMask = new BitArray(r.actionsMask.size());
        else
            rights.actionsMask = r.actionsMask;
        return rights;
    }

    public TmplDocument getDocument() {
        return document;
    }

    public String getTitle() {
        return title;
    }

    public String getName() {
        return name;
    }

    public boolean getScreen() {
        return screen;
    }

    public ImmutableList<TmplField> getFields() {
        return fields;
    }

    public ImmutableList<TmplField> getFirstFields() {
        return firstFields;
    }

    public ImmutableList<TmplField> getSecondFields() {
        return secondFields;
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

    public ImmutableList<TmplTab> getTabs() {
        return tabs;
    }

    public TmplTab getTabByName(String name) {
        return tabByName.get(name.toUpperCase());
    }

    public ImmutableList<TmplField> getColumns() {
        return columns;
    }

    public TmplField getColumnByName(String name) {
        return columnByName.get(name.toUpperCase());
    }

    public boolean canBeDeleted() {
        return document.document.fieldByFullname.get(DocflowConfig.ImplicitFields.DELETED.toString().toUpperCase()) != null;
    }
}
