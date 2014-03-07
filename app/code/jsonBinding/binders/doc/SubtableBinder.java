package code.jsonBinding.binders.doc;

import code.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.action.DocumentUpdate;
import code.docflow.model.Template;
import code.docflow.rights.DocumentAccessActionsRights;
import code.jsonBinding.*;
import code.types.PolymorphicRef;
import code.utils.BitArray;
import code.utils.EnumUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import docflow.DocflowMessages;
import play.db.jpa.JPA;
import play.db.jpa.JPABase;
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
                             DocumentUpdate update, PolymorphicRef docId, Object outerStructure, final Result result) throws Exception {

        Preconditions.checkNotNull(update);
        final ArrayList<JPABase> newRecords = update.newRecords;
        final JsonGenerator changes = update.changesGenerator;
        Preconditions.checkNotNull(newRecords);

        List<JPABase> v = (List<JPABase>) getter.invoke(obj);
        final short oldSize = v == null ? 0 : (short) v.size();
        short newRecordIndex = oldSize;

        if (node.isNull()) { // drop list and finish
            clearList(obj, v, changes);
            return;
        }

        int[] newOrder = null;
        final List<JsonBindingChanges.ChangesAttempt> updatedRecordsHistory = (changes == null) ? null : new ArrayList<JsonBindingChanges.ChangesAttempt>();
        final List<JsonBindingChanges.ChangesAttempt> appendsRecordsHistory = (changes == null) ? null : new ArrayList<JsonBindingChanges.ChangesAttempt>();
        JsonNode u;
        JsonNode o = null;

        if (node.isArray()) { // simple update - all records in the listed. missing records are deleted. save in order of this list
            u = node;
            if (u.size() == 0) {
                clearList(obj, v, changes);
                return;
            }
            newOrder = new int[u.size()];
        } else if (node.isObject()) { // optimized update - updates and new record in field 'u'; order changes (including deletions) in field 'o'
            u = node.get(UPDATE_FIELD);
            o = node.get(ORDER_FIELD);
            if (o != null && o.size() == 0) {
                clearList(obj, v, changes);
                return;
            }
        } else {
            result.addMsg(DocflowMessages.error_ValidationFieldIncorrectValue_2, fldPrefix + fldName, node.asText());
            return;
        }

        final Result localResult = new Result();
        if (v == null)
            setter.invoke(obj, v = new ArrayList<JPABase>());

        if (u != null) {
            final int uSize = u.size();
            final JsonGenerator outerGenerator = update.changesGenerator;
            for (int i = 0; i < uSize; i++) {
                final String pref = fldPrefix + fldName + "[" + i + "]";
                final JsonNode recordNode = u.get(i);
                final JsonNode indexNode = recordNode.get(DocflowConfig.ImplicitFields.I.toString());
                if (indexNode == null) { // new record
                    final JPABase newRecord = subrecordAccessor.newRecord();
                    localResult.clear();
                    final List<JPABase> newNewRecords = new ArrayList<JPABase>();
                    localResult.clear();
                    update.changesGenerator = null;
                    typeBinder.fromJson(newRecord, recordNode, rights, mask, pref, update, docId, outerStructure, localResult);
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
                    if (changes != null) {
                        final JsonBindingChanges.ChangesAttempt changesAttempt = new JsonBindingChanges.ChangesAttempt(null);
                        final Template historyTemplate = docId.getDocument()._docType().templates.get(DocflowConfig.BuiltInTemplates.HISTORY.getUpperCase());
                        typeBinder.toJson(newRecord, historyTemplate, changesAttempt.getJsonGenerator(), rights, mask);
                        appendsRecordsHistory.add(changesAttempt);
                    }
                } else { // update existing record
                    final Integer oldIndex = parseIndex(indexNode, oldSize);
                    if (oldIndex == null) {
                        result.addMsg(DocflowMessages.error_ValidationSubtableWrongIndexValue_2, pref + "." + DocflowConfig.ImplicitFields.I.toString(), indexNode.asText());
                        continue;
                    }
                    if (!(0 <= oldIndex && oldIndex < oldSize)) {
                        result.addMsg(DocflowMessages.error_ValidationSubtableIndexOutOfRange_3, pref + "." + DocflowConfig.ImplicitFields.I.toString(), indexNode.asText(), oldSize);
                        continue;
                    }
                    final JsonBindingChanges.ChangesAttempt changesAttempt = (changes == null) ? null : new JsonBindingChanges.ChangesAttempt(oldIndex);
                    final JPABase record = v.get(oldIndex);
                    localResult.clear();
                    update.changesGenerator = changesAttempt == null ? null : changesAttempt.getJsonGenerator();
                    typeBinder.fromJson(record, recordNode, rights, mask, pref, update, docId, outerStructure, localResult);
                    result.append(localResult);
                    if (localResult.isError()) {
                        changesAttempt.close();
                        continue;
                    }
                    if (changesAttempt != null)
                        if (changesAttempt.anyChange()) {
                            record._save();
                            updatedRecordsHistory.add(changesAttempt);
                        } else
                            changesAttempt.close(); // unfortunatly JsonBindingChanges.ChangesAttempt are not reusalbe, due to Jackson limitations
                    if (newOrder != null)
                        newOrder[i] = oldIndex;
                }
            }
            update.changesGenerator = outerGenerator;
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
            final ArrayList<JPABase> oldList = new ArrayList<JPABase>(v);
            v.clear();
            for (int i = 0; i < newOrder.length; i++) {
                final int oldIndex = newOrder[i];
                final JPABase record = oldList.get(oldIndex);
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
                final JPABase record = oldList.get(i);
                if (record != null) {
                    anyReorder = true;
                    JPA.em().remove(record); // this way do not extra increments 'rev', instead of 'record._delete();'
                }
            }
        }

        if (changes != null) {
            final boolean anyUpdate = updatedRecordsHistory.size() > 0 || appendsRecordsHistory.size() > 0;
            if (anyUpdate || anyReorder) {
                changes.writeFieldName(fldName);
                changes.writeStartObject();
                if (anyUpdate) {
                    changes.writeFieldName(UPDATE_FIELD);
                    changes.writeStartArray();
                    for (JsonBindingChanges.ChangesAttempt changesAttempt : updatedRecordsHistory) {
                        changesAttempt.serialize(changes);
                        changesAttempt.close();
                    }
                    for (JsonBindingChanges.ChangesAttempt changesAttempt : appendsRecordsHistory) {
                        changesAttempt.serialize(changes);
                        changesAttempt.close();
                    }
                    changes.writeEndArray();
                }
                if (anyReorder) {
                    changes.writeFieldName(ORDER_FIELD);
                    changes.writeStartArray();
                    // writes new sequence with compression by groups
                    final int newSize = newOrder.length;
                    for (int i = 0; i < newSize; ) {
                        int shift = newOrder[i] - i;
                        int j = i + 1;
                        for (; j < newSize; j++)
                            if (shift != (newOrder[j] - j))
                                break;
                        changes.writeNumber(newOrder[i]); // index
                        changes.writeNumber(j - i); // number of consequent pairs
                        i = j;
                    }
                    changes.writeEndArray();
                }
                changes.writeEndObject();
            }
        }
    }

    private void clearList(Object obj, List<JPABase> v, JsonGenerator changes) throws IllegalAccessException, InvocationTargetException, IOException {
        if (v == null || v.size() == 0)
            return;
        for (int i = 0; i < v.size(); i++) {
            final JPABase item = v.get(i);
            if (item != null) subrecordAccessor.delete(item);
        }
        v.clear();
        if (changes != null) {
            changes.writeFieldName(fldName);
            changes.writeStartObject();
            changes.writeFieldName(ORDER_FIELD);
            changes.writeStartArray();
            // nothing, since all records are removed
            changes.writeEndArray();
            changes.writeEndObject();
        }
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

    public void copyToJson(Object obj, Template template, JsonGenerator generator, Stack<String> stack, int mode, DocumentAccessActionsRights rights, BitArray mask) throws Exception {

        mode &= ~(JsonTypeBinder.GENERATE_$A | JsonTypeBinder.GENERATE_$R | JsonTypeBinder.GENERATE_$N);

        final List<JPABase> rec = (List<JPABase>) getter.invoke(obj);

        if (template != null && EnumUtil.isEqual(DocflowConfig.BuiltInTemplates.HISTORY, template.name)) { // this code is only for new objects
            generator.writeObjectFieldStart(fldName);
            if (rec == null || rec.size() == 0)
                generator.writeNullField(ORDER_FIELD);
            else {
                generator.writeFieldName(UPDATE_FIELD);
                final List list = (List) getter.invoke(obj);
                generator.writeStartArray();
                for (int i = 0; i < list.size(); i++) {
                    final Object o = list.get(i);
                    typeBinder.toJson(o, template, generator, stack, mode, rights, mask);
                }
                generator.writeEndArray();
            }
            generator.writeEndObject();
        } else {
            if (rec == null) {
                generator.writeArrayFieldStart(fldName);
                generator.writeEndArray();
            } else {
                generator.writeFieldName(fldName);
                final List list = (List) getter.invoke(obj);
                generator.writeStartArray();
                for (int i = 0; i < list.size(); i++) {
                    final Object o = list.get(i);
                    typeBinder.toJson(o, template, generator, stack, mode, rights, mask);
                }
                generator.writeEndArray();
            }
        }
    }
}
