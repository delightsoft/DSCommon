package code.docflow.compiler;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.collections.Item;
import code.docflow.compiler.enums.*;
import code.docflow.model.*;
import code.docflow.yaml.YamlMessages;
import code.docflow.utils.BitArray;
import code.docflow.utils.NamesUtil;

import java.util.LinkedHashMap;

/**
 * 1. Builds permissions masks.
 */
public class Compiler140DocumentsStep4 {

    public static void doJob(DocflowConfig docflowConfig, Result result) {
        for (DocType docType : docflowConfig.documents.values()) {

            if (docType.udt)
                continue;

            fixFilterAndSortOrder(docType, result);

            docType.derivedFieldsMask = new BitArray(docType.allFields.size());
            docType.calculatedFieldsMask = new BitArray(docType.allFields.size());
            for (Field field : docType.allFields)
                if (field.derived) {
                    docType.derivedFieldsMask.set(field.index, true);
                    if (field.calculated)
                        docType.calculatedFieldsMask.set(field.index, true);
                }

            docType.implicitFieldsMask = new BitArray(docType.allFields.size());
            for (Item item : docType.fieldsGroups.get(BuiltInFieldsGroups.IMPLICIT.getUpperCase()).fields)
                docType.implicitFieldsMask.set(docType.fieldByFullname.get(item.name.toUpperCase()).index, true);

            docType.implicitTopLevelFieldsMask = new BitArray(docType.allFields.size());
            for (Item item : docType.fieldsGroups.get(BuiltInFieldsGroups.IMPLICIT_TOP_LEVEL.getUpperCase()).fields)
                docType.implicitTopLevelFieldsMask.set(docType.fieldByFullname.get(item.name.toUpperCase()).index, true);

            docType.notDerivedFieldsMask = docType.derivedFieldsMask.copy();
            docType.notDerivedFieldsMask.inverse();

            docType.serviceActionsMask = new BitArray(docType.actionsArray.length);

            if (!docType.udt) // _udt document is not applicable for CRUD
                for (int i = 0; i < docType.actionsArray.length; i++) {
                    Action action = docType.actionsArray[i];
                    if (action.service)
                        docType.serviceActionsMask.set(action.index, true);
                }

            // Start documents full permissions masks from here
            docType.fullViewMask = docType.implicitTopLevelFieldsMask.copy();
            docType.fullUpdateMask = new BitArray(docType.allFields.size());
            docType.fullActionsMask = docType.serviceActionsMask.copy();

            // Process states and their's transitions
            for (State state : docType.states.values()) {

                // State rights implicates document level rights
                state.viewMask = docType.implicitTopLevelFieldsMask.copy();
                state.updateMask = new BitArray(docType.allFields.size());
                state.actionsMask = new BitArray(docType.actionsArray.length);

                if (BuiltInStates.NEW.name().equals(state.name.toUpperCase())) {
                    state.actionsMask.set(CrudActions.CREATE.index, true);
                    state.actionsMask.set(CrudActions.RETRIEVE.index, true);
                } else {
                    state.actionsMask.set(CrudActions.RETRIEVE.index, true);
                    state.actionsMask.set(CrudActions.UPDATE.index, true);
                    state.actionsMask.set(CrudActions.DELETE.index, true);
                }

                if (state.view != null)
                    for (Item fieldOrFieldsGroup : state.view.values()) {
                        String key = fieldOrFieldsGroup.name.toUpperCase();
                        if (key.startsWith("_")) {
                            final FieldsGroup fieldsGroup = docType.fieldsGroups.get(key.substring(1));
                            if (fieldsGroup == null) {
                                result.addMsg(YamlMessages.error_DocumentStateContainsUndefinedFieldsGroupInView, docType.name, state.name, fieldOrFieldsGroup.name.substring(1));
                                continue;
                            }
                            state.viewMask.add(fieldsGroup.mask);
                        } else {
                            Field field = docType.fieldByFullname.get(key);
                            if (field == null) {
                                result.addMsg(YamlMessages.error_DocumentStateContainsUndefinedFieldInView, docType.name, state.name, fieldOrFieldsGroup.name);
                                continue;
                            }
                            for (int i = field.index; i < field.endIndex; i++)
                                state.viewMask.set(i, true);
                            for (FieldStructure s = field.structure; s != null; s = s.structure)
                                state.viewMask.set(s.index, true);
                        }
                    }
                if (state.update != null)
                    for (Item fieldOrFieldsGroup : state.update.values()) {
                        String key = fieldOrFieldsGroup.name.toUpperCase();
                        if (key.startsWith("_")) {
                            final FieldsGroup fieldsGroup = docType.fieldsGroups.get(key.substring(1));
                            if (fieldsGroup == null) {
                                result.addMsg(YamlMessages.error_DocumentStateContainsUndefinedFieldsGroupInUpdate, docType.name, state.name, fieldOrFieldsGroup.name.substring(1));
                                continue;
                            }
                            state.updateMask.add(fieldsGroup.mask);
                        } else {
                            Field field = docType.fieldByFullname.get(key);
                            if (field == null) {
                                result.addMsg(YamlMessages.error_DocumentStateContainsUndefinedFieldInUpdate, docType.name, state.name, fieldOrFieldsGroup.name);
                                continue;
                            }
                            for (int i = field.index; i < field.endIndex; i++)
                                state.updateMask.set(i, true);
                            for (FieldStructure s = field.structure; s != null; s = s.structure)
                                state.updateMask.set(s.index, true);
                        }
                    }

                if (state.transitions != null)
                    for (Transition transition : state.transitions.values())
                        state.actionsMask.set(docType.actions.get(transition.name.toUpperCase()).index, true);

                state.updateMask.subtract(docType.implicitFieldsMask);
                state.updateMask.subtract(docType.derivedFieldsMask);

                docType.fullViewMask.add(state.viewMask);
                docType.fullUpdateMask.add(state.updateMask);
                docType.fullActionsMask.add(state.actionsMask);

            }
            // Rule: Exclude derived field from any update
            docType.fullUpdateMask.subtract(docType.derivedFieldsMask);

            if (docType.module.schema == DocflowModule.Schema.V1) { // in V2 and futher, nullable only fields that were explicitly marked so
                // Rule: only fields updateable in first state are actually required in DB scheme (Note: This rule is obsolete for Schema.V2)
                final State state = docType.statesArray[0];
                final BitArray.EnumTrueValues it = state.updateMask.getEnumTrueValues();
                int fi;
                while ((fi = it.next()) >= 0) {
                    final Field field = docType.allFields.get(fi);
                    field.nullable = !field.required;
                }
            }

            // Rule: If structure field is nullable, obviously all fields within structure are nullable as well
            for (Field field : docType.allFields)
                if ((field.type == BuiltInTypes.STRUCTURE || field.type == BuiltInTypes.SUBTABLE || field.type == BuiltInTypes.TAGS) &&
                        field.nullable) {
                    FieldStructure fieldStructure = (FieldStructure) field;
                    for (Field fld : fieldStructure.fields.values())
                        fld.nullable = true;
                }
        }
    }

    private static void fixFilterAndSortOrder(DocType docType, Result result) {

        // filters
        if (docType.filters == null) {
            docType.filters = new LinkedHashMap<String, DocumentFilter>();
            if (docType.states.size() > 2) { // then auto filters by states
                DocumentFilter all = new DocumentFilter();
                all.name = BuiltInFilters.ALL.toString();
                docType.filters.put(BuiltInFilters.ALL.name(), all);
                for (State state : docType.statesArray) {
                    DocumentFilter filterByState = new DocumentFilter();
                    filterByState.name = "state" + NamesUtil.turnFirstLetterInUpperCase(state.name);
                    filterByState.where = "doc.state='" + NamesUtil.wordsToUpperUnderscoreSeparated(state.name) + "'";
                    if (!docType.light && !docType.simple)
                        filterByState.where += " and doc.deleted=false";
                    docType.filters.put(filterByState.name.toUpperCase(), filterByState);
                }
            }
        } else
            for (DocumentFilter filter : docType.filters.values()) {
                if (filter.where != null && !docType.report)
                    filter.where = "(" + filter.where + ") and doc.deleted=false";
                else if (filter.where == null && filter.name.startsWith("state") && !filter.name.equals("state")) {
                    String[] ands = filter.name.substring("state".length()).split("Or");
                    boolean isNot = false;
                    if (ands[0].startsWith("Not")) {
                        isNot = true;
                        ands[0] = ands[0].substring("Not".length());
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < ands.length; i++) {
                        String stateName = ands[i];
                        State state = docType.states.get(stateName.toUpperCase());
                        if (state == null) {
                            result.addMsg(YamlMessages.error_SortOrderFieldNotFound, docType.name, filter.name,
                                    NamesUtil.turnFirstLetterInLowerCase(stateName));
                            continue;
                        }
                        if (sb.length() > 0)
                            sb.append(isNot ? " and " : " or ");
                        else if (!isNot)
                            sb.append("(");
                        sb.append(isNot ? "doc.state<>'" : "doc.state='").append(NamesUtil.wordsToUpperUnderscoreSeparated(state.name)).append("'");
                    }
                    if (!isNot)
                        sb.append(")");
                    if (!docType.light && !docType.simple)
                        sb.append(" and doc.deleted=false");
                    filter.where = sb.toString();
                }
            }

        DocumentFilter all = docType.filters.get(BuiltInFilters.ALL.name());
        DocumentFilter deleted = docType.filters.get(BuiltInFilters.DELETED.name());

        if (all == null) {
            all = new DocumentFilter();
            all.name = BuiltInFilters.ALL.toString();
            docType.filters.put(BuiltInFilters.ALL.name(), all);
        }

        if (!docType.report && !docType.light && !docType.simple) {
            if (all.where == null)
                all.where = "doc.deleted=false";
            if (deleted == null) {
                deleted = new DocumentFilter();
                deleted.name = BuiltInFilters.DELETED.toString();
                docType.filters.put(BuiltInFilters.DELETED.name(), deleted);
            }
            if (deleted.where == null)
                deleted.where = "doc.deleted=true";
        } if (all.where == null)
            all.where = "";

        // sortOrders
        if (docType.sortOrders != null)
            for (DocumentSortOrder sortOrder : docType.sortOrders.values()) {
                if (sortOrder.sortOrder != null || !(sortOrder.name.startsWith("by") && !sortOrder.equals("by")))
                    continue;
                String[] ands = sortOrder.name.substring("by".length()).split("And");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < ands.length; i++) {
                    String field = ands[i];
                    if (field.endsWith("Desc"))
                        field = field.substring(0, field.length() - "Desc".length());
                    Field fld = docType.fieldByFullname.get(field.toUpperCase().replace("_", "."));
                    if (fld == null) {
                        result.addMsg(YamlMessages.error_SortOrderFieldNotFound, docType.name, sortOrder.name,
                                NamesUtil.turnFirstLetterInLowerCase(field));
                        continue;
                    }
                    if (sb.length() > 0)
                        sb.append(", ");
                    sb.append("doc.").append(fld.fullname.replace("_", "."));
                    if (field.endsWith("Desc"))
                        sb.append(" desc");
                }
                sortOrder.sortOrder = sb.toString();
            }
        else {
            docType.sortOrders = new LinkedHashMap<String, DocumentSortOrder>();
            generateDefaultSortOrdersForLevel(docType.fields, docType);
            if (docType.sortOrders.size() == 0) {
                DocumentSortOrder so = new DocumentSortOrder();
                so.name = "_none";
                docType.sortOrders.put(so.name.toUpperCase(), so);
            }
        }
    }

    public static void generateDefaultSortOrdersForLevel(LinkedHashMap<String, Field> fields, DocType docType) {
        for (Field field : fields.values()) {
            if (field.implicitFieldType != null && !(
                    field.implicitFieldType == BuiltInFields.CREATED ||
                            field.implicitFieldType == BuiltInFields.MODIFIED))
                continue;
            if (field.type == BuiltInTypes.ENUM ||
                    field.type == BuiltInTypes.SUBTABLE ||
                    field.type == BuiltInTypes.TAGS ||
                    field.type == BuiltInTypes.REFERS ||
                    field.type == BuiltInTypes.POLYMORPHIC_REFERS)
                continue;
            if (field.calculated)
                continue;
            if (field.type == BuiltInTypes.STRUCTURE) {
                generateDefaultSortOrdersForLevel(((FieldStructure) field).fields, docType);
                continue;
            }
            DocumentSortOrder so = new DocumentSortOrder();
            so.name = "by" + NamesUtil.turnFirstLetterInUpperCase(field.fullname).replace(".", "_");
            so.sortOrder = "doc." + field.fullname;
            docType.sortOrders.put(so.name.toUpperCase(), so);
        }
    }
}
