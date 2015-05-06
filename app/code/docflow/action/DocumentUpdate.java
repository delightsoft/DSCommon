package code.docflow.action;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.docs.DocumentPersistent;
import code.docflow.utils.BitArray;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class DocumentUpdate {

    public DocumentPersistent doc;

    public String action;
    public ActionParams params;

    /**
     * True, if any field was changed, including derived fields that are not tracked in changesNode.
     */
    public boolean wasUpdate;

    /**
     * True, if there was action invoked on doc.
     */
    public boolean wasAction;

    public ObjectNode undoNode;
    public ObjectNode changesNode;
}
