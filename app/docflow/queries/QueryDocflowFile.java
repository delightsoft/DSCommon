package docflow.queries;

import code.docflow.queries.DocListBuilder;
import code.docflow.queries.QueryParams;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.utils.BitArray;
import models.DocflowFile;

public class QueryDocflowFile {
    public static void calculate(DocflowFile docflowFile, BitArray mask, DocumentAccessActionsRights rights) {
    }

    public static DocListBuilder list(final QueryParams params) {
        final DocListBuilder builder = new DocListBuilder(params);
        return builder;
    }
}
