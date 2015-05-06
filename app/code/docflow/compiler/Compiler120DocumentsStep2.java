package code.docflow.compiler;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.compiler.enums.BuiltInFieldsGroups;
import code.docflow.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.collections.Item;
import code.docflow.compiler.enums.BuiltInActionsGroups;
import code.docflow.compiler.enums.BuiltInFields;
import code.docflow.compiler.enums.BuiltInTypes;
import code.docflow.model.*;
import code.docflow.utils.EnumUtil;
import code.docflow.yaml.YamlMessages;
import code.docflow.utils.BitArray;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * 1. Adds 'implicit' fields group.
 * 2. Builds fields groups rights masks.
 * 3. Builds level masks.
 */
public class Compiler120DocumentsStep2 {

    public static void doJob(DocflowConfig docflowConfig, Result result) {
        for (DocType doc : docflowConfig.documents.values()) {

            if (doc.fieldsGroups == null)
                doc.fieldsGroups = new LinkedHashMap<String, FieldsGroup>();

            // Creates 'implicit' and 'implicitTopLevel' fields groups.  Take list of fields from doc.implicitFields list.
            final FieldsGroup implicit = new FieldsGroup();
            implicit.implicit = true;
            implicit.name = BuiltInActionsGroups.IMPLICIT.toString();
            implicit.fields = new Item[doc.implicitFields.size()];
            final FieldsGroup implicit_top_level = new FieldsGroup();
            implicit_top_level.implicit = true;
            implicit_top_level.name = BuiltInActionsGroups.IMPLICIT.toString();
            final ArrayList<Item> implicitTopLevel = new ArrayList<Item>();
            for (int i = 0; i < doc.implicitFields.size(); i++) {
                final Item item = new Item();
                final Field field = doc.implicitFields.get(i);
                item.name = field.fullname; // it's correct: fullname must be assigned to Item.name in this case
                implicit.fields[i] = item;
                final String upCaseName = field.fullname.toUpperCase();
                if (EnumUtil.isEqual(BuiltInFields.ID, field.fullname) ||
                        EnumUtil.isEqual(BuiltInFields.SUBJ, field.fullname) ||
                        EnumUtil.isEqual(BuiltInFields.TEXT, field.fullname) ||
                        EnumUtil.isEqual(BuiltInFields.REV, field.fullname) ||
                        EnumUtil.isEqual(BuiltInFields.STATE, field.fullname) ||
                        EnumUtil.isEqual(BuiltInFields.MODIFIED, field.fullname) ||
                        EnumUtil.isEqual(BuiltInFields.CREATED, field.fullname) ||
                        EnumUtil.isEqual(BuiltInFields.DELETED, field.fullname) ||
                        EnumUtil.isEqual(BuiltInFields.TEXT_STORAGE, field.fullname))
                    implicitTopLevel.add(item);
            }
            implicit_top_level.fields = implicitTopLevel.toArray(new Item[0]);

            if (doc.fieldsGroups.put(BuiltInFieldsGroups.IMPLICIT.getUpperCase(), implicit) != null) {
                result.addMsg(YamlMessages.error_FieldsGroupHasReservedName, doc.name, implicit.name);
                continue;
            }

            if (doc.fieldsGroups.put(BuiltInFieldsGroups.IMPLICIT_TOP_LEVEL.getUpperCase(), implicit_top_level) != null) {
                result.addMsg(YamlMessages.error_FieldsGroupHasReservedName, doc.name, implicit_top_level.name);
                continue;
            }

            // Creates 'all' and 'updatable' fields groups.
            final FieldsGroup all = new FieldsGroup();
            all.implicit = true;
            all.name = BuiltInFieldsGroups.ALL.toString();

            final ArrayList<Item> updatableFields = new ArrayList<Item>();
            final ArrayList<Item> nonResultFields = new ArrayList<Item>();

            all.fields = new Item[doc.fields.size()];
            int v = 0;
            for (Field fld : doc.fields.values()) {
                final Item item = new Item();
                item.name = fld.fullname; // it's correct: fullname must be assigned to Item.name in this case
                all.fields[v++] = item;
                if (!fld.derived && fld.implicitFieldType == null)
                    updatableFields.add(item);
                if (item._groups == null || !item._groups.contains("_RESULT"))
                    nonResultFields.add(item);
            }

            boolean error = false;

            if (doc.fieldsGroups.put(BuiltInFieldsGroups.ALL.name(), all) != null) {
                result.addMsg(YamlMessages.error_FieldsGroupHasReservedName, doc.name, all.name);
                error = true;
            }

            final FieldsGroup updatable = new FieldsGroup();
            updatable.implicit = true;
            updatable.name = BuiltInFieldsGroups.UPDATABLE.toString();
            updatable.fields =  updatableFields.toArray(new Item[0]);
            if (doc.fieldsGroups.put(BuiltInFieldsGroups.UPDATABLE.name(), updatable) != null) {
                result.addMsg(YamlMessages.error_FieldsGroupHasReservedName, doc.name, all.name);
                error = true;
            }

            // TODO: This is temporary until will be implemented full support or groups mentioned within fields
            final FieldsGroup nonResultFldGroup = new FieldsGroup();
            nonResultFldGroup.implicit = true;
            nonResultFldGroup.name = BuiltInFieldsGroups.NONRESULT.toString();
            nonResultFldGroup.fields = updatableFields.toArray(new Item[0]);
            if (doc.fieldsGroups.put(BuiltInFieldsGroups.NONRESULT.name(), nonResultFldGroup) != null) {
                result.addMsg(YamlMessages.error_FieldsGroupHasReservedName, doc.name, all.name);
                error = true;
            }

            if (error)
                continue;

            // Builds masks of fields groups.  Checks that all field of the document were covered by groups.
            final BitArray docMask = new BitArray(doc.fieldByFullname.size());
            for (FieldsGroup group : doc.fieldsGroups.values()) {
                group.mask = new BitArray(doc.fieldByFullname.size());
                for (int i = 0; i < group.fields.length; i++) {
                    String fieldName = group.fields[i].name; // name, just string within Item

                    if (fieldName.equals(DocflowConfig.FIELD_SELF))
                        continue;

                    final Field field = doc.fieldByFullname.get(fieldName.toUpperCase());
                    if (field == null) {
                        result.addMsg(YamlMessages.error_FieldFromGroupNotFound, doc.name, group.name, fieldName);
                        continue;
                    }

                    for (int k = field.index; k < field.endIndex; k++)
                        group.mask.set(k, true);

                    for (FieldStructure s = field.structure; s != null; s = s.structure)
                        group.mask.set(s.index, true);
                }
                docMask.add(group.mask);
            }
            docMask.inverse();
            final BitArray.EnumTrueValues tv = docMask.getEnumTrueValues();
            for (int fi = tv.next(); fi != -1; fi = tv.next())
                result.addMsg(YamlMessages.warning_FieldNotMentionedInGroups, doc.name, doc.allFields.get(fi).fullname);

            // build level mask
            doc.levelMask = new BitArray(doc.allFields.size());
            doc.levelMask.inverse();

            for (Field field : doc.fields.values()) {
                if (field.type != BuiltInTypes.STRUCTURE && field.type != BuiltInTypes.SUBTABLE && field.type != BuiltInTypes.TAGS)
                    continue;
                FieldStructure fs = (FieldStructure) field;
                buildStructureLevelMask(fs, doc);
                doc.levelMask.subtract(fs.mask);
            }
        }
    }

    private static void buildStructureLevelMask(FieldStructure s, DocType doc) {
        s.mask = new BitArray(doc.allFields.size());
        s.mask = new BitArray(doc.allFields.size());
        for (int i = s.index + 1; i < s.endIndex; i++)
            s.mask.set(i, true);

        for (Field field : s.fields.values()) {
            if (field.type != BuiltInTypes.STRUCTURE && field.type != BuiltInTypes.SUBTABLE && field.type != BuiltInTypes.TAGS)
                continue;
            FieldStructure fs = (FieldStructure) field;
            buildStructureLevelMask(fs, doc);
            if (s.levelMask == null)
                s.levelMask = s.mask.copy();
            s.levelMask.subtract(fs.levelMask);
        }

        if (s.levelMask == null)
            s.levelMask = s.mask;
    }
}
