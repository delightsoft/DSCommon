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
import code.docflow.utils.NamesUtil;
import code.docflow.yaml.compositeKeyHandlers.TransitionCompositeKeyHandler;

import java.util.*;

/**
 * 1. Checks that fields do not have reserved names.
 * 2. Create Document.entities.
 * 3. Adds implicit fields (like id, rev, create etc.).
 * 4. Sets Field.index and Field.endIndex.
 * 5. Sets Field.fullname.
 * 6. Builds Document.allFields.
 * 7. Builds Document.implicitFields.
 * 8. Processes 'textstorage' attribute on fields, and conditionally adds 'textStorage' field.
 * 9. Collects fields to Entities
 * 10. Links Fields of type Structure to it's Entity
 * 11. Generates Entity.javaClassName and Entity.tableName
 */
public class Compiler110DocumentsStep1 {

    private static final Comparator<DocType> DOCUMENT_ASCENDING_SORT_BY_NAME = new Comparator<DocType>() {
        public int compare(DocType o1, DocType o2) {
            return o1.name.compareTo(o2.name);
        }
    };

    public static void doJob(DocflowConfig docflowConfig, Result result) {

        docflowConfig.documentsArray = new DocType[docflowConfig.documents.size()];
        int di = 0;
        for (DocType doc : docflowConfig.documents.values()) {
            doc.index = di;
            docflowConfig.documentsArray[di++] = doc;
        }
        Arrays.sort(docflowConfig.documentsArray, DOCUMENT_ASCENDING_SORT_BY_NAME);

        for (int i = 0; i < docflowConfig.documentsArray.length; i++) {

            DocType doc = docflowConfig.documentsArray[i];

            if (doc.task) {
                doc.simple = doc.rev = true;
                if (doc.states != null)
                    result.addMsg(YamlMessages.error_NoStatesInTaskDoc, doc.name);
            }

            if (doc.light)
                doc.simple = true;

            // empty fields list, if none
            if (doc.fields == null)
                doc.fields = new LinkedHashMap<String, Field>();

            if (doc.actions == null)
                doc.actions = new LinkedHashMap<String, Action>();

            if (doc.states == null)
                doc.states = new LinkedHashMap<String, State>();

            if (doc.task) {

                // Note: Reference schema you can see in {DSCommonTest}/docflow/documents/ControlsTask.yaml

                // add field 'result'
                FieldSimple fieldResult = new FieldSimple();
                fieldResult.name = BuiltInFields.RESULT.toString();
                fieldResult.type = BuiltInTypes.RESULT;
                fieldResult.derived = true;
                fieldResult.accessedFields = new HashSet<String>();
                doc.fields.put(BuiltInFields.RESULT.name(), fieldResult);

                // add actions
                addAction(doc, TaskActions.STARTJOB);
                addAction(doc, TaskActions.BACKTOAWAIT);
                addAction(doc, TaskActions.CANCEL)
                        .display = true;
                addAction(doc, TaskActions.SUCCESS);
                addAction(doc, TaskActions.ERROR);

                // add states
                //  - new
                final State newState = addState(doc, BuiltInStates.NEW);
                newState.update = newState.view = fieldsViewUpdateList(BuiltInFieldsGroups.UPDATABLE);
                addTransition(newState, CrudActions.CREATE, TaskStates.AWAIT);

                //  - await
                final State awaitState = addState(doc, TaskStates.AWAIT);
                awaitState.view = fieldsViewUpdateList(BuiltInFieldsGroups.NONRESULT);
                addTransition(awaitState, TaskActions.STARTJOB, TaskStates.RUNNING);
                addTransition(awaitState, TaskActions.CANCEL, TaskStates.ERROR);

                // - running
                final State runningState = addState(doc, TaskStates.RUNNING);
                runningState.view = fieldsViewUpdateList(BuiltInFieldsGroups.NONRESULT);
                addTransition(runningState, TaskActions.BACKTOAWAIT, TaskStates.AWAIT);
                addTransition(runningState, TaskActions.SUCCESS, TaskStates.SUCCESS);
                addTransition(runningState, TaskActions.ERROR, TaskStates.ERROR);
                addTransition(runningState, TaskActions.CANCEL, TaskStates.ERROR.toString());

                // - canceled
                final State canceledState = addState(doc, TaskStates.CANCELED);
                canceledState.view = fieldsViewUpdateList(BuiltInFieldsGroups.NONRESULT);
                addTransition(canceledState, TaskActions.SUCCESS, TaskStates.SUCCESS);
                addTransition(canceledState, TaskActions.ERROR, TaskStates.ERROR);

                // -success
                final State successState = addState(doc, TaskStates.SUCCESS);
                successState.view = fieldsViewUpdateList(BuiltInFieldsGroups.ALL, BuiltInFields.RESULT);

                // - error
                final State errorState = addState(doc, TaskStates.ERROR);
                errorState.view = fieldsViewUpdateList(BuiltInFieldsGroups.NONRESULT, BuiltInFields.RESULT);

            } else { // regular document (not task)

                // States:
                //  - new
                final State newState = addState(doc, BuiltInStates.NEW, true);
                if (newState.update == null)
                    newState.update = fieldsViewUpdateList(BuiltInFieldsGroups.UPDATABLE);

                // Rule: NEW state must be first in yaml states
                for (State state : doc.states.values()) {
                    if (state != newState)
                        result.addMsg(YamlMessages.error_DocumentNewStateMustComeFirst, doc.name, state.name, BuiltInStates.NEW.toString());
                    break;
                }

                // - persisted, if none persisted states
                State firstPersistedState = null;
                if (doc.states.size() < 2) {
                    firstPersistedState = addState(doc, BuiltInStates.PERSISTED);
                    firstPersistedState.view = fieldsViewUpdateList(BuiltInFieldsGroups.ALL);
                    if (!doc.simple)
                        firstPersistedState.update = firstPersistedState.view;
                } else {
                    int p = 0;
                    for (State state : doc.states.values()) {
                        if (p == 1) {
                            firstPersistedState = state;
                            break;
                        }
                        p++;
                    }
                }

                // Rule: NEW state has one and only one transtion CREATE to first persisted state
                if (newState.transitions == null || newState.transitions.size() == 0) {
                    addTransition(newState, CrudActions.CREATE, firstPersistedState.name);
                } else if (newState.transitions.size() > 1 || newState.transitions.get(CrudActions.CREATE.name()) == null) {
                    result.addMsg(YamlMessages.error_DocumentNewStateCanOnlyHasCreateTransitions, doc.name,
                            BuiltInStates.NEW.toString(), CrudActions.CREATE.toString());
                }

                if (doc.states.size() == 2) {
                    // Rule: In two states document all non-service actions are transitions to persisted state
                    firstPersistedState.transitions = new LinkedHashMap<String, Transition>();
                    for (Action action : doc.actions.values())
                        // Note: This happends priod building doc.actionsArray.  So Update is not marked as implicit action yet
                        if (!action.service && !CrudActions.UPDATE.name().equals(action.name.toUpperCase())) {
                            final Transition transition = new Transition();
                            transition.name = action.name;
                            transition.endState = firstPersistedState.name;
                            firstPersistedState.transitions.put(transition.name.toUpperCase(), transition);
                        }
                } else { // any state documents must have 'rev' to coordinate state transitions in a parallel environment
                    doc.rev = true;
                }
            }

            // detailed fields validation. builds Entities. adds implicit fields.
            final Entity entity = new Entity();
            entity.name = doc.name;
            entity.tableName = "doc_" + NamesUtil.wordsToUnderscoreSeparated(doc.name);
            entity.type = doc.report ? EntityType.REPORT : (doc.light ? EntityType.LIGHT_DOCUMENT :
                    (doc.simple ? EntityType.SIMPLE_DOCUMENT : EntityType.DOCUMENT));
            entity.document = doc;
            doc.entities.add(entity);
            indexFieldsAtLevel(docflowConfig, doc, entity, null, "", doc.fields, 0, result);

            if (entity.type == EntityType.DOCUMENT) {
                doc.historyTableName = "doc_" + NamesUtil.wordsToUnderscoreSeparated(doc.name) + "_history";
                doc.historyEntityName = doc.name + "History";
            }

            doc.textField = doc.fieldByFullname.get(BuiltInFields.TEXT.name());
            if (doc.textField == null) {
                if (doc.textSourceField != null)
                    result.addMsg(YamlMessages.error_FieldDocTypeDoesNotSupportText, doc.name, doc.textSourceField.fullname);
            } else {
                doc.textField.calculated = !doc.blendText;
                if (doc.blendText && doc.textSourceField != null)
                    result.addMsg(YamlMessages.error_FieldFieldTextInBlendTextDoc, doc.name, doc.textSourceField.fullname);
            }

            docflowConfig.documentByTable.put(entity.tableName.toUpperCase(), doc);
        }
    }

    private static Action addAction(DocType doc, Enum action) {
        if (doc.actions == null)
            doc.actions = new LinkedHashMap<String, Action>();
        Action res = doc.actions.get(action.name());
        if (res == null) {
            res = new Action();
            res.name = action.toString();
            doc.actions.put(action.name(), res);
        }
        return res;
    }

    private static LinkedHashMap<String, Item> fieldsViewUpdateList(Enum... fieldOrFieldsGroups) {
        final LinkedHashMap<String, Item> res = new LinkedHashMap<String, Item>();
        for (Enum fieldOrFieldsGroup : fieldOrFieldsGroups)
            if (fieldOrFieldsGroup instanceof BuiltInFieldsGroups) {
                final Item updatableFields = new Item("_" + fieldOrFieldsGroup.toString());
                res.put("_" + fieldOrFieldsGroup.name(), updatableFields);
            } else {
                final Item updatableFields = new Item(fieldOrFieldsGroup.toString());
                res.put(fieldOrFieldsGroup.name(), updatableFields);
            }
        return res;
    }

    private static boolean hasState(DocType doc, Enum state) {
        return doc.states.containsKey(state.name());
    }

    private static State addState(DocType doc, Enum state) {
        return addState(doc, state, false);
    }

    private static State addState(DocType doc, Enum state, boolean unshift) {
        State res = doc.states.get(state.name());
        if (res == null) {
            res = new State();
            res.name = state.toString();
            res.transitions = new LinkedHashMap<String, Transition>();
            if (unshift && doc.states.size() > 0) {
                final LinkedHashMap<String, State> newStatesList = new LinkedHashMap<String, State>();
                newStatesList.put(state.name(), res);
                newStatesList.putAll(doc.states);
                doc.states = newStatesList;
            } else
                doc.states.put(state.name(), res);
        }
        return res;
    }

    private static Transition addTransition(State state, Enum action, Enum endState) {
        return addTransition(state, action, endState.toString(), null);
    }

    private static Transition addTransition(State state, Enum action, String endState) {
        return addTransition(state, action, endState, null);
    }

    private static Transition addTransition(State state, Enum action, Enum endState, Enum precondition) {
        return addTransition(state, action, endState.toString(), precondition);
    }

    private static Transition addTransition(State state, Enum action, String endState, Enum precondition) {
        if (state.transitions == null)
            state.transitions = new LinkedHashMap<String, Transition>();
        Transition res = new Transition();
        res.name = action.toString();
        res.endState = endState;
        if (precondition != null)
            res.preconditions = new String[] {precondition.toString()};

        final String key = TransitionCompositeKeyHandler.INSTANCE.key(res);
        final Transition existing = state.transitions.get(key);
        if (existing != null)
            return existing;

        state.transitions.put(key, res);
        return res;
    }

    private static int indexFieldsAtLevel(DocflowConfig docflowConfig, DocType doc, Entity entity, FieldStructure structure, String namePrefix, LinkedHashMap<String, Field> fields, int index, Result result) {
        switch (entity.type) {
            case LIGHT_DOCUMENT:
            case SIMPLE_DOCUMENT:
                index = processField(docflowConfig, doc, entity, structure, namePrefix, buildBuiltInField(doc, structure, BuiltInFields.ID, doc.implicitFields), index, result);
                if (doc.rev)
                    index = processField(docflowConfig, doc, entity, structure, namePrefix, buildBuiltInField(doc, structure, BuiltInFields.REV, doc.implicitFields), index, result);
                index = processField(docflowConfig, doc, entity, structure, namePrefix, buildBuiltInField(doc, structure, BuiltInFields.TEXT, doc.implicitFields), index, result);
                break;
            case DOCUMENT:
                index = processField(docflowConfig, doc, entity, structure, namePrefix, buildBuiltInField(doc, structure, BuiltInFields.ID, doc.implicitFields), index, result);
                index = processField(docflowConfig, doc, entity, structure, namePrefix, buildBuiltInField(doc, structure, BuiltInFields.REV, doc.implicitFields), index, result);
                index = processField(docflowConfig, doc, entity, structure, namePrefix, buildBuiltInField(doc, structure, BuiltInFields.TEXT, doc.implicitFields), index, result);
                break;
            case SUBTABLE:
                index = processField(docflowConfig, doc, entity, structure, namePrefix, buildBuiltInField(doc, structure, BuiltInFields.ID, doc.implicitFields), index, result);
                index = processField(docflowConfig, doc, entity, structure, namePrefix, buildBuiltInField(doc, structure, BuiltInFields.I, doc.implicitFields), index, result);
                entity.fkField = buildBuiltInField(doc, structure, BuiltInFields.FK, doc.implicitFields);
                index = processField(docflowConfig, doc, entity, structure, namePrefix, entity.fkField, index, result);
                break;
        }

        if (entity.type != EntityType.SUBTABLE && entity.type != EntityType.STRUCTURE && entity.type != EntityType.REPORT_STRUCTURE) {
            if (entity.document.linkedDocument) // Rule: Any document might be flagged as linked
                index = processField(docflowConfig, doc, entity, structure, namePrefix, buildBuiltInField(doc, structure, BuiltInFields.SUBJ, doc.implicitFields), index, result);
            if (doc.states.size() > 2) // Rule: Only two state are a new-state and one persisted state, when there is no need in the STATE field
                index = processField(docflowConfig, doc, entity, structure, namePrefix, buildBuiltInField(doc, structure, BuiltInFields.STATE, doc.implicitFields), index, result);
        }

        for (Field fld : fields.values()) {
            if (structure != null) {
                fld.derived |= structure.derived;
                fld.calculated |= structure.calculated;
            }

            boolean isReservedName = false;
            final String upperCasedName = fld.name.toUpperCase();
            try {
                BuiltInFields fldName = BuiltInFields.valueOf(upperCasedName);
                switch (entity.type) {
                    case SIMPLE_DOCUMENT:
                        switch (fldName) {
                            case CREATOR:
                            case CREATED:
                            case TEXT:
                                isReservedName = true;
                        }
                        // fallthru
                    case LIGHT_DOCUMENT:
                        switch (fldName) {
                            case ID:
                            case SUBJ:
                            case REV:
                            case STATE:
                            case TEXT:
                                isReservedName = true;
                        }
                        break;
                    case DOCUMENT:
                        switch (fldName) {
                            case ID:
                            case REV:
                            case SUBJ:
                            case STATE:
                            case TEXT:
                            case TEXT_STORAGE:
                            case MODIFIED:
                            case CREATED:
                            case DELETED:
                                isReservedName = true;
                        }
                        break;
                    case REPORT:
                        switch (fldName) {
                            case STATE:
                            case TEXT:
                                isReservedName = true;
                        }
                        break;
                    case SUBTABLE:
                        switch (fldName) {
                            case ID:
                            case I:
                            case FK:
                            case TEXT_STORAGE:
                            case TEXT:
                                isReservedName = true;
                        }
                        break;
                }
            } catch (IllegalArgumentException e) {
                // it's BuiltInFields.valueOf(upperCasedName) failed, and this is expected behaviour
            }

            if (isReservedName) {
                result.addMsg(YamlMessages.error_FieldHasReservedName, doc.name, namePrefix + fld.name);
                continue;
            }

            if (fld.textstorage)
                entity.hasTextStorage = true;

            fld.template = (structure != null ? structure.template : ("_" + doc.name)) + "_" + fld.name;

            if (!doc.udt) { // _udt document fields was already processed in previouse steps.

                // apply UDType, if that's the case
                if (fld.type == null) {
                    final Field fldType = docflowConfig.fieldTypes.get(fld.udtType.toUpperCase());
                    if (fldType == null) {
                        result.addMsg(YamlMessages.error_FieldHasUnknownType, doc.name, namePrefix + fld.name, fld.udtType);
                        continue;
                    }
                    if (!(fldType instanceof FieldSimple)) {
                        result.addMsg(YamlMessages.error_FieldHasNotSimpleType, doc.name, namePrefix + fld.name, fld.udtType);
                        continue;
                    }
                    fldType.mergeTo(fld);
                }

                // apply named enum type
                if (fld.type == BuiltInTypes.ENUM)
                    if (fld.udtType != null) {
                        Field fldType = docflowConfig.fieldTypes.get(fld.udtType.toUpperCase());
                        if (fldType == null) {
                            result.addMsg(YamlMessages.error_FieldHasUnknownType, doc.name, namePrefix + fld.name, fld.udtType);
                            continue;
                        }
                        if (!(fldType instanceof FieldEnum)) {
                            result.addMsg(YamlMessages.error_FieldNotAnEnumType, doc.name, namePrefix + fld.name, fld.udtType);
                            continue;
                        }
                        fldType.mergeTo(fld);
                    } else {
                        FieldEnum fieldEnum = (FieldEnum) fld;
                        fieldEnum.enumTypeName = (structure != null ? structure.entity.getClassName() : doc.getClassName()) +
                                "$" + NamesUtil.turnFirstLetterInUpperCase(fld.name);
                    }

                // apply named structure type
                if (fld.type == BuiltInTypes.STRUCTURE)
                    if (fld.udtType != null) {
                        Field fldType = docflowConfig.fieldTypes.get(fld.udtType.toUpperCase());
                        if (fldType == null) {
                            result.addMsg(YamlMessages.error_FieldHasUnknownType, doc.name, namePrefix + fld.name, fld.udtType);
                            continue;
                        }
                        if (!(fldType instanceof FieldStructure) || fldType.type != BuiltInTypes.STRUCTURE) {
                            result.addMsg(YamlMessages.error_FieldNotAStructureType, doc.name, namePrefix + fld.name, fld.udtType);
                            continue;
                        }
                        fldType.deepCopy().mergeTo(fld);
                    }

                // apply named subtable type
                if (fld.type == BuiltInTypes.SUBTABLE)
                    if (fld.udtType != null) {
                        Field fldType = docflowConfig.fieldTypes.get(fld.udtType.toUpperCase());
                        if (fldType == null) {
                            result.addMsg(YamlMessages.error_FieldHasUnknownType, doc.name, namePrefix + fld.name, fld.udtType);
                            continue;
                        }
                        if (!(fldType instanceof FieldStructure) || fldType.type != BuiltInTypes.SUBTABLE) {
                            result.addMsg(YamlMessages.error_FieldNotASubtableType, doc.name, namePrefix + fld.name, fld.udtType);
                            continue;
                        }
                        fldType.deepCopy().mergeTo(fld);
                    }
            }

            index = processField(docflowConfig, doc, entity, structure, namePrefix, fld, index, result);

            validateFieldType(doc, fld, result);

            if ((fld instanceof FieldSimple) && ((FieldSimple) fld).text) {
                if (doc.entities.get(0) != entity)
                    result.addMsg(YamlMessages.error_FieldFieldTextCannotBeInASubtable, doc.name, namePrefix + fld.name);
                else if (doc.textSourceField != null)
                    result.addMsg(YamlMessages.error_FieldAnotherFieldIsText, doc.name, namePrefix + fld.name);
                else
                    doc.textSourceField = fld;
            }

            if (fld.type == BuiltInTypes.JAVA) {
                if (!doc.report && !fld.calculated)
                    result.addMsg(YamlMessages.error_FieldMustBeCalculated, doc.name, namePrefix + fld.name, BuiltInTypes.JAVA.toString().toLowerCase());
            } else if (fld.type == BuiltInTypes.REFERS) {
                final String docName = ((FieldReference) fld).refDocument;
                final DocType document = DocflowConfig.instance.documents.get(docName.toUpperCase());
                if (document == null)
                    result.addMsg(YamlMessages.error_FieldRefersUndefinedDocument, doc.name, namePrefix + fld.name, docName);
            } else if (fld.type == BuiltInTypes.POLYMORPHIC_REFERS) {
                final FieldPolymorphicReference fpr = (FieldPolymorphicReference) fld;
                if (fpr.refDocuments != null) {
                    fpr.refDocumentsNames = new TreeSet<String>();
                    for (int i = 0; i < fpr.refDocuments.length; i++) {
                        String docName = fpr.refDocuments[i];
                        fpr.refDocumentsNames.add(docName.toUpperCase());
                        final DocType document = DocflowConfig.instance.documents.get(docName.toUpperCase());
                        if (document == null)
                            result.addMsg(YamlMessages.error_FieldRefersUndefinedDocument, doc.name, namePrefix + fld.name, docName);
                    }
                }
            }
        }

        if (entity.type == EntityType.DOCUMENT) {
            index = processField(docflowConfig, doc, entity, structure, namePrefix, buildBuiltInField(doc, structure, BuiltInFields.CREATED, doc.implicitFields), index, result);
            index = processField(docflowConfig, doc, entity, structure, namePrefix, buildBuiltInField(doc, structure, BuiltInFields.MODIFIED, doc.implicitFields), index, result);
            index = processField(docflowConfig, doc, entity, structure, namePrefix, buildBuiltInField(doc, structure, BuiltInFields.DELETED, doc.implicitFields), index, result);
            if (entity.hasTextStorage)
                index = processField(docflowConfig, doc, entity, structure, namePrefix, buildBuiltInField(doc, structure, BuiltInFields.TEXT_STORAGE, doc.implicitFields), index, result);
        } else {
            if (entity.type == EntityType.SIMPLE_DOCUMENT) {
                index = processField(docflowConfig, doc, entity, structure, namePrefix, buildBuiltInField(doc, structure, BuiltInFields.CREATOR, doc.implicitFields), index, result);
                index = processField(docflowConfig, doc, entity, structure, namePrefix, buildBuiltInField(doc, structure, BuiltInFields.CREATED, doc.implicitFields), index, result);
            }
        }

        return index;
    }

    private static int processField(DocflowConfig docflowConfig, DocType doc, Entity entity, FieldStructure structure, String namePrefix, Field field, int index, Result result) {
        Field fld = field;
        fld.document = doc;
        fld.entity = entity;
        fld.fullname = namePrefix + fld.name;
        fld.structure = structure;
        fld.index = index++;
        fld.endIndex = index;
        entity.fields.add(field);
        doc.fieldByFullname.put(fld.fullname.toUpperCase(), fld);
        doc.allFields.add(fld);
        if (fld instanceof FieldStructure) {
            final FieldStructure fldStructure = (FieldStructure) fld;
            entity = new Entity();
            entity.parent = structure != null ? structure.entity : doc.entities.get(0);
            entity.outerStructure = entity.parent.type == EntityType.STRUCTURE ? entity.parent.outerStructure : entity.parent;
            entity.name = entity.parent.name + "_" + NamesUtil.turnFirstLetterInUpperCase(fld.name);
            entity.structureField = fld;
            entity.tableName = "doc_" + NamesUtil.wordsToUnderscoreSeparated(entity.name);
            entity.type = doc.report ? EntityType.REPORT_STRUCTURE : (fldStructure.single ? EntityType.STRUCTURE : EntityType.SUBTABLE);
            entity.document = doc;
            doc.entities.add(entity);
            fldStructure.entity = entity;
            fld.endIndex = index = indexFieldsAtLevel(docflowConfig, doc, entity, fldStructure, fld.fullname + ".", fldStructure.fields, index, result);
        }
        return index;
    }

    private static void validateFieldType(DocType doc, Field fld, Result result) {
        if (fld.calculated) // then it's implicitly 'derived'
            fld.derived = true;
        // Rule: Enforce Java style of field naming
        fld.name = NamesUtil.turnFirstLetterInLowerCase(fld.name);
        for (int j = 0; j < fld.type.required.length; j++) {
            String attr = fld.type.required[j];
            if (!fld.accessedFields.contains(attr.toUpperCase()))
                result.addMsg(YamlMessages.error_FieldMustHaveGivenAttributeSpecified, doc.name, fld.fullname, fld.type.toString(), attr);
        }
        if (fld instanceof FieldSimple) {
            FieldSimple fieldSimple = (FieldSimple) fld;
            for (int j = 0; j < FieldSimple.typeAttrs.length; j++) {
                String attr = FieldSimple.typeAttrs[j];
                if (fld.accessedFields.contains(attr.toUpperCase())) {
                    boolean found = false;
                    for (int k = 0; k < fld.type.required.length; k++)
                        if (attr.equals(fld.type.required[k])) {
                            found = true;
                            break;
                        }
                    if (!found)
                        for (int k = 0; k < fld.type.optional.length; k++)
                            if (attr.equals(fld.type.optional[k])) {
                                found = true;
                                break;
                            }
                    if (!found) {
                        result.addMsg(YamlMessages.error_FieldMustNotHaveGivenAttributeSpecified, doc.name, fld.fullname, fld.type.toString(), attr);
                    }
                }
            }

            if (fld.accessedFields.contains("TEXT"))
                if (fld.type != BuiltInTypes.STRING && fld.type != BuiltInTypes.TEXT)
                    result.addMsg(YamlMessages.error_FieldTextIsOnlyApplicableToString, doc.name, fld.fullname);

            boolean isMaxLengthAssignedFromLength = false;
            if (fld.accessedFields.contains("LENGTH"))
                if (fld.accessedFields.contains("MAXLENGTH")) {
                    if (fieldSimple.maxLength > fieldSimple.length)
                        result.addMsg(YamlMessages.error_FieldMustHasMaxLengthAttrBiggerThenLength, doc.name, fld.fullname);
                } else {
                    // by default maxLength same as length
                    isMaxLengthAssignedFromLength = true;
                    fieldSimple.maxLength = fieldSimple.length;
                    fld.accessedFields.add("MAXLENGTH");
                }
            if (fld.accessedFields.contains("MINLENGTH") && (isMaxLengthAssignedFromLength || fld.accessedFields.contains("MAXLENGTH")))
                if (fieldSimple.minLength > fieldSimple.maxLength)
                    result.addMsg(isMaxLengthAssignedFromLength ?
                            YamlMessages.error_FieldMustHasMinLengthAttrBiggerThenLength :
                            YamlMessages.error_FieldMustHasMinLengthAttrBiggerThenMaxLength, doc.name, fld.fullname);
            if (fld.accessedFields.contains("MIN") && fld.accessedFields.contains("MAX"))
                if (fieldSimple.min > fieldSimple.max)
                    result.addMsg(YamlMessages.error_FieldMustHasMinAttrBiggerThenMax, doc.name, fld.fullname);
        }
    }

    /**
     * Creates document instance of Field for given implicit field, based on hardcoded rules.  Adds new field to implicitFields list.
     *
     * @return New instance of the field.
     */
    private static Field buildBuiltInField(DocType doc, FieldStructure structure, BuiltInFields implicitFieldType, ArrayList<Field> implicitFields) {
        Field field = null;
        switch (implicitFieldType) {
            case ID:
                field = new FieldSimple();
                field.type = BuiltInTypes.LONG;
                break;

            case REV:
                field = new FieldSimple();
                field.type = BuiltInTypes.INT;
                break;

            case I:
                field = new FieldSimple();
                field.type = BuiltInTypes.INT;
                break;

            case FK:
                field = new FieldSimple();
                field.type = BuiltInTypes.REFERS;
                break;

            case CREATED:
            case MODIFIED:
                field = new FieldSimple();
                field.type = BuiltInTypes.DATETIME;
                break;

            case DELETED:
                field = new FieldSimple();
                field.type = BuiltInTypes.BOOL;
                break;

            case STATE:
                field = new FieldSimple();
                field.type = BuiltInTypes.STRING;
                ((FieldSimple) field).length = ((FieldSimple) field).maxLength = 100;
                break;

            case TEXT_STORAGE:
                field = new FieldSimple();
                field.type = BuiltInTypes.TEXT;
                break;

            case TEXT:
                FieldSimple textField = new FieldSimple();
                field = textField;
                field.derived = true;
                field.type = BuiltInTypes.STRING;
                ((FieldSimple) field).length = ((FieldSimple) field).maxLength = 200;
                break;

            case SUBJ:
                final FieldPolymorphicReference subjField = new FieldPolymorphicReference();
                field = subjField;
                subjField.type = BuiltInTypes.POLYMORPHIC_REFERS;
                subjField.refDocuments = new String[0];
                break;

            case CREATOR:
                field = new FieldSimple();
                field.type = BuiltInTypes.STRING;
                ((FieldSimple) field).length = ((FieldSimple) field).maxLength = 100;
                break;
        }

        field.name = implicitFieldType.toString();
        field.implicitFieldType = implicitFieldType;
        field.required = (implicitFieldType != BuiltInFields.TEXT_STORAGE);
        field.nullable = (implicitFieldType == BuiltInFields.RESULT || implicitFieldType == BuiltInFields.TEXT_STORAGE);

        field.hidden = true;
        field.template = (structure != null ? structure.template : ("_" + doc.name)) + "_" + field.name;

        implicitFields.add(field);
        return field;
    }
}
