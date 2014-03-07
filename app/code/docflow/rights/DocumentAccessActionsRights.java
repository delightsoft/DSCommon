package code.docflow.rights;

import code.docflow.model.DocType;
import code.utils.BitArray;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class DocumentAccessActionsRights {
    public DocType docType;
    public BitArray viewMask;
    public BitArray updateMask;
    public BitArray actionsMask;
    public BitArray retrieveMask;
}
