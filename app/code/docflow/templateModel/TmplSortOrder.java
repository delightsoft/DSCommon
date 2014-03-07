package code.docflow.templateModel;

import code.docflow.model.DocumentSortOrder;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class TmplSortOrder {

    /**
     * Title in form for localization.
     */
    String title;

    /**
     * Name of a sort order.
     */
    String name;

    public static final String SORTORDER_ROOT = "sortorder.";
    public static final String SORTORDER_LINK = "." + SORTORDER_ROOT;

    public static TmplSortOrder buildFor(TmplDocument tmplDocument, DocumentSortOrder sortOrder) {

        final TmplSortOrder res = new TmplSortOrder();
        res.title = tmplDocument.getTitle() + SORTORDER_LINK + sortOrder.name;
        res.name = sortOrder.name;

        return res;
    }

    public String getTitle() {
        return title;
    }

    public String getName() {
        return name;
    }
}
