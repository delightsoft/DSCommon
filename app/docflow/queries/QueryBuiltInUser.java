package docflow.queries;

import code.docflow.queries.DocListBuilder;
import code.docflow.queries.QueryParams;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.utils.BitArray;
import models.BuiltInUser;
import org.apache.commons.lang.NotImplementedException;

public class QueryBuiltInUser {
    public static void calculate(BuiltInUser builtInUser, BitArray mask, DocumentAccessActionsRights rights) {
    }

    public static DocListBuilder list(final QueryParams params) {
        throw new NotImplementedException("Not applicable");
    }
}
