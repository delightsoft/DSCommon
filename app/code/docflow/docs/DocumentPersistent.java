package code.docflow.docs;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.jsonBinding.RecordAccessor;

public abstract class DocumentPersistent extends Document {
    public <T extends DocumentPersistent> T _attached() {
        if (!_isPersisted())
            return (T) this;
        final RecordAccessor accessor = _docType().jsonBinder.recordAccessor;
        return (T) accessor.findById(accessor.getId(this));
    }
}
