package code.docflow.templateModel;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.DocflowConfig;
import code.docflow.collections.Item;
import code.docflow.compiler.enums.BuiltInFields;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.model.*;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.utils.BitArray;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class TmplTemplate {

    /**
     * Document this field belongs to.
     */
    TmplDocument document;

    /**
     * Source template.
     */
    Template srcTemplate;

    /**
     * If not null, then it's TmplTemplate derived from another by limiting list of fields.  Used for multi-tab document splitting.
     */
    TmplTemplate parentTemplate;

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

    // TODO: Obsolete: This is not going to be used in responsive layout. Consider deletion after 10/1/2014
    ImmutableList<TmplField> firstFields;
    ImmutableList<TmplField> secondFields;

    ImmutableList<TmplTab> tabs;
    ImmutableMap<String, TmplTab> tabByName;

    ImmutableList<TmplField> columns;
    ImmutableMap<String, TmplField> columnByName;

    /**
     * Builds limited by fiels list version of TmplTemplate.
     */
    public static TmplTemplate buildTabTemplate(Iterable<String> fields, TmplTemplate template) {
        TmplTemplate res = new TmplTemplate();
        res.parentTemplate = template;
        res.document = template.document;
        res.title = template.title;
        res.name = template.name;
        res.screen = template.screen;
        res.columns = template.columns;
        res.columnByName = template.columnByName;

        final ImmutableMap.Builder<String, TmplField> fldMapBuilder = ImmutableMap.builder();
        final ImmutableList.Builder<TmplField> fldListBuilder = ImmutableList.builder();
        ImmutableList.Builder<TmplField> firstFldListBuilder = null;
        ImmutableList.Builder<TmplField> secondFldListBuilder = null;
        if (template.firstFields != template.fields) {
            firstFldListBuilder = ImmutableList.builder();
            secondFldListBuilder = ImmutableList.builder();
        }

        for (String fieldName : fields) {
            String upperFieldName = fieldName.toUpperCase();
            TmplField tmplField = template.fieldByName.get(upperFieldName);
            if (tmplField == null) // Can be hidden field or field inaccessible for specific user
                continue;
            fldMapBuilder.put(upperFieldName, tmplField);
            fldListBuilder.add(tmplField);
            if (firstFldListBuilder != null)
                if (!tmplField.second)
                    firstFldListBuilder.add(tmplField);
                else
                    secondFldListBuilder.add(tmplField);
        }

        res.fields = fldListBuilder.build();
        res.fieldByName = fldMapBuilder.build();
        if (firstFldListBuilder == null) {
            res.firstFields = res.fields;
            res.secondFields = template.secondFields;
        } else {
            res.firstFields = firstFldListBuilder.build();
            res.secondFields = secondFldListBuilder.build();
        }

        return res;
    }


    @SuppressWarnings("unchecked")
    public static TmplTemplate buildFor(TmplDocument document, DocType docType, Template template,
                                        DocumentAccessActionsRights rights, boolean isCreateTemplate) {
        checkNotNull(template);

        final TmplTemplate res = new TmplTemplate();
        res.document = document;
        res.srcTemplate = template;
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
                    !readonly && rights.updateMask.get(field.index));
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
        } else {
            res.firstFields = res.fields;
        }
        res.secondFields = secondFldListBuilder.build();

        final ImmutableMap.Builder<String, TmplField> fldMapBuilder = ImmutableMap.builder();
        for (TmplField tmplField : fields)
            fldMapBuilder.put(tmplField.fullname.toUpperCase(), tmplField);
        res.fieldByName = fldMapBuilder.build();

        final ImmutableList.Builder<TmplAction> actionsListBuilder = ImmutableList.builder();

        BitArray fixedActionsMask = rights.actionsMask.copy();
        if (docType.linkedDocument) {
            // to make possible localization of create button for linked docs, even known that linked
            // document can be create only by assigning to field of its subj
            fixedActionsMask.set(CrudActions.CREATE.index, true);
        }
        fixedActionsMask.set(CrudActions.DELETE.index, true);
        fixedActionsMask.set(CrudActions.RECOVER.index, true);

        if (template.tabs != null && !isCreateTemplate) {
            final ImmutableList.Builder<TmplTab> tabsListBuilder = ImmutableList.builder();

            tabsListBuilder.add(TmplTab.buildMainTab(template, res)); // Add tab _main

            for (TemplateTab tab : template.tabs.values()) { // Add rest of tabs

                // Rule: When tab refers inaccessible by this user docType, this tab get skipped
                DocType tabDocType = DocflowConfig.instance.documents.get(tab.docType.toUpperCase());
                String userRoles = document.model.userRoles;
                if (userRoles != null) {
                    DocumentAccessActionsRights docTypeRights = RightsCalculator.instance.calculate(tabDocType, userRoles);
                    if (!docTypeRights.actionsMask.get(CrudActions.RETRIEVE.index))
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
                    tmplField = TmplField.buildSelfFieldFor(document.model, res, document);
                else {
                    tmplField = res.fieldByName.get(fieldName.toUpperCase());
                    if (tmplField == null)
                        continue;
                }
                columnsListBuilder.add(tmplField);
            }
            ImmutableList<TmplField> columns = res.columns = columnsListBuilder.build();

            final ImmutableMap.Builder<String, TmplField> columnsMapBuilder = ImmutableMap.builder();
            for (TmplField fld : columns)
                columnsMapBuilder.put(fld.name.toUpperCase(), fld);
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

        if ((template.modeMask & JsonTypeBinder.GENERATE__A) == 0)
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
        return document.actions;
    }

    public TmplAction getActionByName(String name) {
        return document.getActionByName(name);
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
        return document.document.fieldByFullname.get(BuiltInFields.DELETED.toString().toUpperCase()) != null;
    }
}
