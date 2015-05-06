package code.docflow.compiler;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.compiler.enums.BuiltInFieldsGroups;
import code.docflow.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.compiler.enums.BuiltInActionsGroups;
import code.docflow.compiler.enums.BuiltInRoles;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.model.*;
import code.docflow.yaml.YamlMessages;
import code.docflow.utils.BitArray;
import code.docflow.utils.EnumUtil;

import java.util.LinkedHashMap;
import java.util.TreeMap;

/**
 * 1. Checks that roles refers existing documents
 * 2. Calculates roles rights masks
 * 3. Collect relations menthined in rights in document.relations
 * 4. Calculates rights masks for relations
 */
public class Compiler310Roles {

    public static void doJob(DocflowConfig docflowConfig, Result result) {

        int rindex = 0;

        final Role system = docflowConfig.roles.get(BuiltInRoles.SYSTEM.name());
        if (system != null)
            result.addMsg(YamlMessages.error_RoleDocumentHasReservedName, system.name);
        else {
            final Role role = new Role();

            final RoleRight allFieldsGroup = new RoleRight();
            allFieldsGroup.name = "_" + BuiltInFieldsGroups.ALL.toString();
            final LinkedHashMap<String, RoleRight> allFieldsRight = new LinkedHashMap<String, RoleRight>();
            allFieldsRight.put("_" + BuiltInFieldsGroups.ALL.name(), allFieldsGroup);

            final RoleRight allActions = new RoleRight();
            allActions.name = BuiltInActionsGroups.ALL.toString();
            final LinkedHashMap<String, RoleRight> linkedDocAnyActionRight = new LinkedHashMap<String, RoleRight>();
            linkedDocAnyActionRight.put(allFieldsGroup.name.toUpperCase(), allActions);

            // Rule: System is allowed to 'delete', that should be specified separatly
            final RoleRight deleteAction = new RoleRight();
            deleteAction.name = CrudActions.DELETE.toString();
            linkedDocAnyActionRight.put(deleteAction.name.toUpperCase(), deleteAction);

            // Rule: System is allowed to 'create', that should be specified separatly
            LinkedHashMap<String, RoleRight> anyActionRight = new LinkedHashMap<String, RoleRight>(linkedDocAnyActionRight);
            final RoleRight createAction = new RoleRight();
            createAction.name = CrudActions.CREATE.toString();
            anyActionRight.put(createAction.name.toUpperCase(), createAction);

            role.system = true;
            role.name = BuiltInRoles.SYSTEM.toString();
            role.documents = new LinkedHashMap<String, RoleDocument>();
            for (DocType docType : docflowConfig.documents.values()) {
                final RoleDocument roleDocument = new RoleDocument();
                roleDocument.role = role;
                roleDocument.name = docType.name;
                roleDocument.view = roleDocument.update = allFieldsRight;
                roleDocument.actions = docType.linkedDocument ? linkedDocAnyActionRight : anyActionRight;
                role.documents.put(roleDocument.name.toUpperCase(), roleDocument);
            }

            final TreeMap<String, Role> newRoles = new TreeMap<String, Role>();
            newRoles.put(role.name.toUpperCase(), role);
            newRoles.putAll(docflowConfig.roles);
            docflowConfig.roles = newRoles;
        }

        for (Role role : docflowConfig.roles.values()) {
            role.index = rindex++;
            if (role.documents == null)
                role.documents = new LinkedHashMap<String, RoleDocument>(0);
            else
                for (RoleDocument roleDocument : role.documents.values()) {
                    roleDocument.role = role;
                    final DocType doc = docflowConfig.documents.get(roleDocument.name.toUpperCase());
                    if (doc == null) {
                        result.addMsg(YamlMessages.error_RoleDocumentContainsUndefinedDocument, role.name, roleDocument.name);
                        continue;
                    }
                    roleDocument.document = doc;
                    roleDocument.viewMask = new BitArray(doc.allFields.size());
                    roleDocument.updateMask = new BitArray(doc.allFields.size());
                    roleDocument.actionsMask = new BitArray(doc.actionsArray.length);

                    // Rule: role is allowed to retrieve document, if document is mentioned within role
                    if (roleDocument.actions == null || roleDocument.actions.get(CrudActions.RETRIEVE.name()) == null)
                        roleDocument.actionsMask.set(CrudActions.RETRIEVE.index, true);

                    for (Field field : doc.implicitFields)
                        roleDocument.viewMask.set(field.index, true);

                    if (doc.relations != null) {
                        roleDocument.relations = new Relation[doc.relations.size()];
                        int i = 0;
                        for (DocumentRelation docRelation : doc.relations.values()) {
                            Relation relation = new Relation();
                            relation.documentRelation = docRelation;
                            relation.viewMask = new BitArray(doc.allFields.size());
                            relation.updateMask = new BitArray(doc.allFields.size());
                            relation.actionsMask = new BitArray(doc.actionsArray.length);
                            relation.retrieveMask = new BitArray(doc.relations.size());
                            relation.retrieveMask.set(docRelation.index, true);
                            roleDocument.relations[i++] = relation;
                        }
                    }

                    if (roleDocument.view != null)
                        for (RoleRight viewRight : roleDocument.view.values()) {
                            String key = viewRight.name.toUpperCase();
                            if (key.startsWith("_")) {
                                final FieldsGroup fieldsGroup = doc.fieldsGroups.get(key.substring(1));
                                if (fieldsGroup == null) {
                                    result.addMsg(YamlMessages.error_RoleDocumentContainsUndefinedFieldsGroupInView, role.name, roleDocument.name, viewRight.name.substring(1));
                                    continue;
                                }
                                if (viewRight.relations != null)
                                    for (int i = 0; i < viewRight.relations.length; i++) {
                                        Relation relation = findRelationByName(roleDocument, doc, role, viewRight.relations[i], result);
                                        if (relation != null)
                                            relation.viewMask.add(fieldsGroup.mask);
                                    }
                                else
                                    roleDocument.viewMask.add(fieldsGroup.mask);
                            } else {
                                final Field field = doc.fieldByFullname.get(key);
                                if (field == null) {
                                    result.addMsg(YamlMessages.error_RoleDocumentContainsUndefinedFieldInView, role.name, roleDocument.name, viewRight.name);
                                    continue;
                                }
                                if (viewRight.relations != null)
                                    for (int i = 0; i < viewRight.relations.length; i++) {
                                        Relation relation = findRelationByName(roleDocument, doc, role, viewRight.relations[i], result);
                                        if (relation != null) {
                                            relation.viewMask.set(field.index, true);
                                            if (field instanceof FieldStructure) {
                                                // note: 'field.index + 2' skips ID field for subtables
                                                for (int j = field.index + (((FieldStructure) field).single ? 1 : 2); j < field.endIndex; j++)
                                                    relation.viewMask.set(j, true);
                                            }
                                            Field struct = field;
                                            while ((struct = struct.structure) != null)
                                                relation.viewMask.set(struct.index, true);
                                        }
                                    }
                                else {
                                    roleDocument.viewMask.set(field.index, true);
                                    if (field instanceof FieldStructure) {
                                        // note: 'field.index + 2' skips ID field
                                        for (int i = field.index + (((FieldStructure) field).single ? 1 : 2); i < field.endIndex; i++)
                                            roleDocument.viewMask.set(i, true);
                                    }
                                    Field struct = field;
                                    while ((struct = struct.structure) != null)
                                        roleDocument.viewMask.set(struct.index, true);
                                }
                            }
                        }

                    if (roleDocument.update != null)
                        for (RoleRight updateRight : roleDocument.update.values()) {
                            String key = updateRight.name.toUpperCase();
                            if (key.startsWith("_")) {
                                final FieldsGroup fieldsGroup = doc.fieldsGroups.get(key.substring(1));
                                if (fieldsGroup == null) {
                                    result.addMsg(YamlMessages.error_RoleDocumentContainsUndefinedFieldsGroupInUpdate, role.name, roleDocument.name, updateRight.name.substring(1));
                                    continue;
                                }
                                if (updateRight.relations != null)
                                    for (int i = 0; i < updateRight.relations.length; i++) {
                                        Relation relation = findRelationByName(roleDocument, doc, role, updateRight.relations[i], result);
                                        if (relation != null)
                                            relation.updateMask.add(fieldsGroup.mask);
                                    }
                                else
                                    roleDocument.updateMask.add(fieldsGroup.mask);
                            } else {
                                final Field field = doc.fieldByFullname.get(key);
                                if (field == null) {
                                    result.addMsg(YamlMessages.error_RoleDocumentContainsUndefinedFieldInUpdate, role.name, roleDocument.name, updateRight.name);
                                    continue;
                                }
                                if (updateRight.relations != null) {
                                    for (int i = 0; i < updateRight.relations.length; i++) {
                                        Relation relation = findRelationByName(roleDocument, doc, role, updateRight.relations[i], result);
                                        if (relation != null) {
                                            relation.updateMask.set(field.index, true);
                                            if (field instanceof FieldStructure) {
                                                // note: 'field.index + 2' skips ID field
                                                for (int j = field.index + (((FieldStructure) field).single ? 1 : 2); j < field.endIndex; j++)
                                                    relation.updateMask.set(j, true);
                                            }
                                            Field struct = field;
                                            while ((struct = struct.structure) != null)
                                                relation.updateMask.set(struct.index, true);
                                        }
                                    }
                                } else {
                                    roleDocument.updateMask.set(field.index, true);
                                    if (field instanceof FieldStructure) {
                                        // note: 'field.index + 2' skips ID field
                                        for (int i = field.index + (((FieldStructure) field).single ? 1 : 2); i < field.endIndex; i++)
                                            roleDocument.updateMask.set(i, true);
                                    }
                                    Field struct = field;
                                    while ((struct = struct.structure) != null)
                                        roleDocument.updateMask.set(struct.index, true);
                                }
                            }
                        }

                    if (roleDocument.actions != null)
                        for (RoleRight actionRight : roleDocument.actions.values()) {
                            if (EnumUtil.isEqual(BuiltInActionsGroups.ALL, actionRight.name)) {

                                BitArray allActions;
                                allActions = new BitArray(roleDocument.document.actionsArray.length);
                                allActions.inverse();

                                // Rule: DELETE and RECOVER actions are not included in 'all' actions group.
                                allActions.set(CrudActions.DELETE.index, false);
                                allActions.set(CrudActions.RECOVER.index, false);

                                // Rule: linkedDocument may not be created directly.  Only indirectly by assigning to a field of 'subj' document.
                                if (doc.linkedDocument)
                                    allActions.set(CrudActions.CREATE.index, false);

                                if (actionRight.relations != null)
                                    for (int i = 0; i < actionRight.relations.length; i++) {
                                        Relation relation = findRelationByName(roleDocument, doc, role, actionRight.relations[i], result);
                                        if (relation != null)
                                            relation.actionsMask.add(allActions);
                                    }
                                else
                                    roleDocument.actionsMask.add(allActions);

                            } else { // regular action
                                final Action action = doc.actions.get(actionRight.name.toUpperCase());
                                if (action == null) {
                                    result.addMsg(YamlMessages.error_RoleDocumentContainsUndefinedActionInActions, role.name, roleDocument.name, actionRight.name);
                                    continue;
                                }
                                final int index = action.index;
                                boolean isRecoverAction = CrudActions.RECOVER.name().equals(actionRight.name.toUpperCase());
                                boolean isCreateAction = CrudActions.CREATE.name().equals(actionRight.name.toUpperCase());
                                if (isRecoverAction) {
                                    result.addMsg(YamlMessages.error_RoleDocumentMistakenlySpecifiesRecoverAction, role.name, roleDocument.name, CrudActions.RECOVER.toString(), CrudActions.DELETE.toString());
                                    continue;
                                }
                                if (isCreateAction && doc.linkedDocument) {
                                    result.addMsg(YamlMessages.error_RoleDocumentCreateActionNoAllowedForLinkedDocument, role.name, roleDocument.name, actionRight.name);
                                    continue;
                                }
                                if (actionRight.relations != null) {
                                    for (int i = 0; i < actionRight.relations.length; i++) {
                                        Relation relation = findRelationByName(roleDocument, doc, role, actionRight.relations[i], result);
                                        if (relation != null)
                                            relation.actionsMask.set(index, true);
                                    }
                                } else
                                    roleDocument.actionsMask.set(index, true);
                            }
                        }


                    if (roleDocument.relations == null) {
                        roleDocument.fullViewMask = roleDocument.viewMask;
                        roleDocument.fullUpdateMask = roleDocument.updateMask;
                        roleDocument.fullActionsMask = roleDocument.actionsMask;
                        // Rule: Updatable fields implicitly become viewable
                        roleDocument.viewMask.add(roleDocument.updateMask);
                    } else {
                        roleDocument.fullViewMask = roleDocument.viewMask.copy();
                        roleDocument.fullUpdateMask = roleDocument.updateMask.copy();
                        roleDocument.fullActionsMask = roleDocument.actionsMask.copy();
                        for (Relation relation : roleDocument.relations) {
                            roleDocument.fullViewMask.add(relation.viewMask);
                            roleDocument.fullUpdateMask.add(relation.updateMask);
                            roleDocument.fullActionsMask.add(relation.actionsMask);
                        }

                        roleDocument.retrieveMask = new BitArray(roleDocument.relations.length);
                        for (int i = 0; i < roleDocument.relations.length; i++) {
                            Relation relation = roleDocument.relations[i];
                            if (relation.actionsMask.get(CrudActions.RETRIEVE.index))
                                roleDocument.retrieveMask.set(i, true);
                        }

                        // Rule: Updatable fields implicitly become viewable
                        roleDocument.viewMask.add(roleDocument.updateMask);
                        for (Relation relation : roleDocument.relations)
                            relation.viewMask.add(relation.updateMask);
                    }

                    // Rule: If there is any updatable field, user implicitly allowed to update
                    if (!roleDocument.fullUpdateMask.isEmpty() && !roleDocument.fullActionsMask.get(CrudActions.UPDATE.index)) {
                        roleDocument.actionsMask.set(CrudActions.UPDATE.index, true);
                        roleDocument.fullActionsMask.set(CrudActions.UPDATE.index, true);
                    }
                }
        }

        // Rule: Any document expected to be mentioned at least in one role
        for (DocType docType : docflowConfig.documents.values()) {
            if (docType.udt)
                continue;
            boolean any = false;
            for (Role role : docflowConfig.roles.values())
                if (!role.system && role.documents.get(docType.name.toUpperCase()) != null) {
                    any = true;
                    break;
                }
            if (!any)
                result.addMsg(YamlMessages.error_DocumentIsNotMentionedInAnyRole, docType.name);
        }
    }

    private static Relation findRelationByName(RoleDocument roleDocument, DocType doc, Role role, String relationName, Result result) {
        DocumentRelation docRelation = doc.relations == null ? null : doc.relations.get(relationName.toUpperCase());
        if (docRelation == null) {
            result.addMsg(YamlMessages.error_RoleDocumentNotDeclaredRelation, role.name, doc.name, relationName);
            return null;
        }
        return roleDocument.relations[docRelation.index];
    }
}
