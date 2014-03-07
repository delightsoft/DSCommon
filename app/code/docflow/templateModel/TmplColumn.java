package code.docflow.templateModel;

import code.docflow.collections.Item;
import code.docflow.model.DocType;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class TmplColumn {

    /**
     * Title in form for localization.
     */
    String title;

    /**
     * Full name of the action.
     */
    String name;

    public static TmplColumn buildFor(DocType document, Item column) {

        final TmplColumn res = new TmplColumn();

        res.title = "doc." + document.name + "." + column.name;
        res.name = column.name;

        return res;
    }

    public String getTitle() {
        return title;
    }

    public String getName() {
        return name;
    }
}
