package code.docflow.jsonBinding.binders.doc;

import code.docflow.action.DocumentUpdateImpl;
import code.docflow.controlflow.Result;
import code.docflow.compiler.enums.BuiltInFields;
import code.docflow.compiler.enums.BuiltInTemplates;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.jsonBinding.RecordAccessorCommon;
import code.docflow.jsonBinding.SubrecordAccessor;
import code.docflow.types.DocumentRef;
import code.docflow.utils.BitArray;
import code.docflow.utils.EnumUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import docflow.DocflowMessages;
import play.db.jpa.GenericModel;
import play.db.jpa.JPA;
import play.exceptions.UnexpectedException;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

// TODO: Reuse deleted records

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class SubtableBinder extends JsonTypeBinder.FieldAccessor {

    public static final String UPDATE_FIELD = "u";
    public static final String ORDER_FIELD = "o";

    public final Class recordType;

    public final RecordAccessorCommon recordAccessorCommon;
    public final SubrecordAccessor subrecordAccessor;
    public JsonTypeBinder typeBinder;

    public SubtableBinder(Field fld, Method getter, Method setter, String fldName) {
        super(fld, getter, setter, fldName);

        final Class<?> fldType = fld.getType();

        final Type genericType = fld.getGenericType();
        if (!(genericType instanceof ParameterizedType)) {
            throw new UnexpectedException(String.format(
                    "Not specified generic element type for field '%2$s' in class '%1$s'.",
                    fld.getDeclaringClass().getName(), fldName));
        }
        Class et = (Class) ((ParameterizedType) genericType).getActualTypeArguments()[0];
        this.recordType = (et == null) ? fldType : et;

        this.subrecordAccessor = SubrecordAccessor.factory.get(recordType);
        this.recordAccessorCommon = this.subrecordAccessor;
        this.typeBinder = JsonTypeBinder.factory.get(recordType);
    }

    @Override
    public void copyFromJson(final Object obj, final JsonNode node,
                             DocumentAccessActionsRights rights, BitArray mask, final String fldPrefix,
                             DocumentUpdateImpl update, DocumentRef docId, Object outerStructure, final Result result) throws Exception {

        Preconditions.checkNotNull(update);
        final ArrayList<GenericModel> newRecords = update.newRecords;
        Template historyTemplate = null;

        final ObjectNode originalChangesNode = update != null ? update.changesNode : null;
        final ObjectNode originalUndoNode = update != null ? update.undoNode : null;
        final boolean originalWasUpdate = update != null ? update.wasUpdate : null;

        Preconditions.checkNotNull(newRecords);

        List<GenericModel> v = (List<GenericModel>) getter.invoke(obj);
        final short oldSize = v == null ? 0 : (short) v.size();
        short newRecordIndex = oldSize;

        if (node.isNull()) { // drop list and finish
            clearList(obj, v, update);
            return;
        }

        int[] newOrder = null;
        final List<ObjectNode> updatedRecordsHistory = (originalChangesNode != null) ? new ArrayList<ObjectNode>() : null;
        final List<ObjectNode> updatedRecordsUndo = (originalUndoNode != null) ? new ArrayList<ObjectNode>() : null;
        final List<ObjectNode> appendedRecordsHistory = (originalChangesNode != null) ? new ArrayList<ObjectNode>() : null;
        final List<ObjectNode> appendedRecordsUndo = (originalUndoNode != null) ? new ArrayList<ObjectNode>() : null;

        JsonNode u;
        JsonNode o = null;

        if (node.isArray()) { // simple update - all records in the listed. missing records are deleted. save in order of this list
            u = node;
            if (u.size() == 0) {
                clearList(obj, v, update);
                return;
            }
            newOrder = new int[u.size()];
        } else if (node.isObject()) { // optimized update - updates and new record in field 'u'; order changes (including deletions) in field 'o'
            u = node.get(UPDATE_FIELD);
            o = node.get(ORDER_FIELD);
            if (o != null && o.size() == 0) {
                clearList(obj, v, update);
                return;
            }
        } else {
            result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
            return;
        }

        final Result localResult = new Result();
        if (v == null)
            setter.invoke(obj, v = new ArrayList<GenericModel>());

        if (u != null) {
            final int uSize = u.size();
            for (int i = 0; i < uSize; i++) {
                final String pref = fldPrefix + fldName + "[" + i + "].";
                final JsonNode recordNode = u.get(i);
                if (!recordNode.isObject()) {
                    result.addMsg(DocflowMessages.error_ValidationJsonObjectExpected_2, pref, recordNode.toString());
                    continue;
                }
                final JsonNode indexNode = recordNode.get(BuiltInFields.I.toString());
                if (indexNode == null) { // new record
                    final GenericModel newRecord = subrecordAccessor.newRecord();
                    localResult.clear();
                    final List<GenericModel> newNewRecords = new ArrayList<GenericModel>();
                    update.undoNode = null;
                    update.changesNode = null;
                    localResult.clear();
                    typeBinder.fromJson(newRecord, (ObjectNode) recordNode, rights, mask, pref, update, docId, outerStructure, localResult);
                    result.append(localResult);
                    if (localResult.isError())
                        continue;
                    final short oldIndex = newRecordIndex++; // skips wrong values for simplicity
                    newRecords.add(newRecord); // record will save() after containg document/structure. It's crutial for proper document creation
                    newRecords.addAll(newNewRecords); // records created withing this update should save only after this subtable
                    v.add(newRecord); // update current list
                    subrecordAccessor.fldFK.set(newRecord, obj);
                    subrecordAccessor.fldI.set(newRecord, oldIndex); // in case there will be no reording later
                    if (newOrder != null)
                        newOrder[i] = oldIndex;
                    if (appendedRecordsHistory != null) {
                        if (historyTemplate == null)
                            historyTemplate = docId.safeGetDocument()._docType().templates.get(BuiltInTemplates.HISTORY.getUpperCase());
                        appendedRecordsHistory.add((ObjectNode) typeBinder.toJson(newRecord, historyTemplate, rights, mask));
                    }
                } else { // update existing record
                    final Integer oldIndex = parseIndex(indexNode, oldSize);
                    if (oldIndex == null) {
                        result.addMsg(DocflowMessages.error_ValidationSubtableWrongIndexValue_2, pref + BuiltInFields.I.toString(), indexNode.asText());
                        continue;
                    }
                    if (!(0 <= oldIndex && oldIndex < oldSize)) {
                        result.addMsg(DocflowMessages.error_ValidationSubtableIndexOutOfRange_3, pref + BuiltInFields.I.toString(), indexNode.asText(), oldSize);
                        continue;
                    }
                    final GenericModel record = v.get(oldIndex);
                    if (update != null) {
                        update.changesNode = (appendedRecordsHistory != null) ? JsonNodeFactory.instance.objectNode() : null;
                        update.undoNode = (appendedRecordsUndo != null) ? JsonNodeFactory.instance.objectNode() : null;
                        update.wasUpdate = false;
                    }
                    localResult.clear();
                    typeBinder.fromJson(record, (ObjectNode) recordNode, rights, mask, pref, update, docId, outerStructure, localResult);
                    result.append(localResult);
                    if (localResult.isError()) {
                        continue;
                    }
                    if (update != null && update.changesNode != null && update.wasUpdate) {
                        record._save();
                        if (updatedRecordsUndo != null)
                            updatedRecordsUndo.add(update.undoNode);
                        if (updatedRecordsHistory != null)
                            updatedRecordsHistory.add(update.changesNode);
                    }
                    if (newOrder != null)
                        newOrder[i] = oldIndex;
                }
            }
        }

        if (o != null) {
            localResult.clear();
            newOrder = extractNewOrderFromNode(o, result, fldPrefix);
            result.append(localResult);
            if (!localResult.isError())
                return;
            Preconditions.checkState(newOrder != null && newOrder.length > 0);
        }

        if (result.isError())
            return;

        boolean anyReorder = false;
        if (newOrder != null) { // might be null, if it's optimized udpate without 'o' field
            final ArrayList<GenericModel> oldList = new ArrayList<GenericModel>(v);
            v.clear();
            for (int i = 0; i < newOrder.length; i++) {
                final int oldIndex = newOrder[i];
                final GenericModel record = oldList.get(oldIndex);
                Preconditions.checkState(record != null); // otherwise someting wrong with order - uses same positions
                v.add(record);
                if ((Short) subrecordAccessor.fldI.get(record) != i) {
                    anyReorder = true;
                    subrecordAccessor.fldI.set(record, (short) i);
                    record._save(); // TODO: Combine this save with save above.  It can be done while work on reusable deleted records
                }
                oldList.set(oldIndex, null);
            }
            for (int i = 0; i < oldList.size(); i++) {
                final GenericModel record = oldList.get(i);
                if (record != null) {
                    anyReorder = true;
                    JPA.em().remove(record); // this way do not extra increments 'rev', instead of 'record._delete();'
                }
            }
        }

        if (originalChangesNode != null) {

            final boolean anyUpdate = updatedRecordsHistory.size() > 0 || appendedRecordsHistory.size() > 0;
            if (anyUpdate || anyReorder) {
                update.wasUpdate = true;
                ObjectNode hNode = JsonNodeFactory.instance.objectNode();
                if (anyUpdate) {
                    final ArrayNode uNode = JsonNodeFactory.instance.arrayNode();
                    for (ObjectNode cNode : updatedRecordsHistory)
                        uNode.add(cNode);
                    for (ObjectNode cNode : appendedRecordsHistory)
                        uNode.add(cNode);
                    hNode.put(UPDATE_FIELD, uNode);
                }
                if (anyReorder) {
                    final ArrayNode oNode = JsonNodeFactory.instance.arrayNode();
                    // writes new sequence with compression by groups
                    final int newSize = newOrder.length;
                    for (int i = 0; i < newSize; ) {
                        int shift = newOrder[i] - i;
                        int j = i + 1;
                        for (; j < newSize; j++)
                            if (shift != (newOrder[j] - j))
                                break;
                        oNode.add(newOrder[i]); // index
                        oNode.add(j - i); // number of consequent pairs
                        i = j;
                    }
                    hNode.put(ORDER_FIELD, oNode);
                }
                originalChangesNode.put(fldName, hNode);
                // TODO: Think of UNDO logic
            }
        }

        if (update != null) {
            update.wasUpdate |= originalWasUpdate;
            update.changesNode = originalChangesNode;
            update.undoNode = originalUndoNode;
        }
    }

    private void clearList(Object obj, List<GenericModel> v, DocumentUpdateImpl update) throws IllegalAccessException, InvocationTargetException, IOException {

        if (v == null || v.size() == 0) return;

        if (update != null) {
            if (update.undoNode != null) {
                // TODO: Implement redo
            }
            if (update.changesNode != null) {
                final ObjectNode histNode = JsonNodeFactory.instance.objectNode();
                // nothing in the list, since all records are removed
                histNode.put(ORDER_FIELD, JsonNodeFactory.instance.arrayNode());
                update.changesNode.put(fldName, histNode);
            }
        }

        for (int i = 0; i < v.size(); i++) {
            final GenericModel item = v.get(i);
            if (item != null) subrecordAccessor.delete(item);
        }
        v.clear();
    }

    private Integer parseIndex(JsonNode indexNode, int size) {
        try {
            return Integer.parseInt(indexNode.asText());
        } catch (NumberFormatException e) {
        }
        return null;
    }

    private int[] extractNewOrderFromNode(JsonNode orderListNode, Result result, String fldPrefix) {
        final int orderListSize = orderListNode.size();
        if (orderListSize % 2 != 0) {
            result.addMsg(DocflowMessages.error_ValidationSubtableIncorrectOrder_1, fldPrefix + fldName);
            return null;
        }
        final int[] orderList = new int[orderListSize];
        for (int i = 0; i < orderListSize; i++) {
            final JsonNode index = orderListNode.get(i);
            if (!index.isNumber()) {
                result.addMsg(DocflowMessages.error_ValidationSubtableIncorrectOrder_1, fldPrefix + fldName);
                return null;
            }
            orderList[i] = index.asInt();
        }
        int listSize = 0;
        for (int i = 0; i < orderList.length; i += 2) {
            int index = orderList[i];
            int count = orderList[i + 1];
            if (index < 0 || count < 1) {
                result.addMsg(DocflowMessages.error_ValidationSubtableIncorrectOrder_1, fldPrefix + fldName);
                return null;
            }
            if (index + count > listSize)
                listSize = index + count;
        }
        if (listSize > Short.MAX_VALUE) { // it's obviously too much
            result.addMsg(DocflowMessages.error_ValidationSubtableIncorrectOrder_1, fldPrefix + fldName);
            return null;
        }
        final BitArray touchedRecords = new BitArray(listSize);
        final int[] newOrder = new int[listSize];
        int p = 0;
        for (int i = 0; i < orderList.length; i += 2) {
            int index = orderList[i];
            int count = orderList[i + 1];
            int j = 0;
            for (; j < count; j++) {
                newOrder[p + j] = index + j;
                touchedRecords.set(index + j, true);
            }
            p += count;
        }
        touchedRecords.inverse();
        if (!touchedRecords.isEmpty()) { // some fields are missing in the order
            result.addMsg(DocflowMessages.error_ValidationSubtableIncorrectOrder_1, fldPrefix + fldName);
            return null;
        }
        return newOrder;
    }

    public void copyToJson(Object obj, Template template, ObjectNode out, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {

        mode &= ~(JsonTypeBinder.GENERATE__A | JsonTypeBinder.GENERATE__N);

        final List<GenericModel> rec = (List<GenericModel>) getter.invoke(obj);

        if (template != null && EnumUtil.isEqual(BuiltInTemplates.HISTORY, template.name)) { // this code is only for new objects
            final ObjectNode hNode = JsonNodeFactory.instance.objectNode();
            if (rec == null || rec.size() == 0)
                hNode.putNull(ORDER_FIELD);
            else {
                final ArrayNode oNode = JsonNodeFactory.instance.arrayNode();
                for (Object o : rec)
                    oNode.add(typeBinder.toJson(o, template, stack, mode, rights, mask));
                hNode.put(UPDATE_FIELD, oNode);
            }
            out.put(fldName, hNode);
        } else {
            final ArrayNode aNode = JsonNodeFactory.instance.arrayNode();
            if (rec != null)
                for (Object o : rec)
                    aNode.add(typeBinder.toJson(o, template, stack, mode, rights, mask));
            out.put(fldName, aNode);
        }
    }
}
