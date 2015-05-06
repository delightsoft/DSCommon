package models;

import code.docflow.DocflowConfig;
import code.docflow.compiler.enums.BuiltInStates;
import code.docflow.model.DocType;
import code.docflow.model.State;
import code.docflow.docs.Document;
import code.docflow.types.DocumentRef;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import javax.persistence.*;

public class BuiltInUser extends Document {

    private final String docFullId;

    public String roles;

    public BuiltInUser(String docFullId, String roles) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(docFullId), "docFullId");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(roles), "roles");
        this.docFullId = docFullId;
        this.roles = roles;
    }

    private static DocType _type;

    public static DocType _type() {
        if (_type == null) {
            _type = DocflowConfig.instance.documents.get("BUILTINUSER");
            _resetQueue.add(new ResetForTest() { @Override public void reset() { _type = null; }});
        }
        return _type;
    }

    @Override
    public DocType _docType() {
        return _type();
    }

    @Transient
    public State _state() {
        if (_state == null)
            _state = _docType().statesArray[BuiltInStates.PERSISTED.index];
        return _state;
    }

    @Override
    public String _fullId() {
        return docFullId;
    }

    @Override
    public DocumentRef _ref() {
        return DocumentRef.NULL;
    }
}
