package code.docflow.templateModel;

import code.docflow.model.DocumentFilter;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class TmplFilter {

    /**
     * Title in form for localization.
     */
    String title;

    /**
     * Name of a filter rule.
     */
    String name;

    public static final String FILTER_ROOT = "filter.";
    public static final String FILTER_LINK = "." + FILTER_ROOT;

    public static TmplFilter buildFor(TmplDocument tmplDocument, DocumentFilter filter) {

        final TmplFilter res = new TmplFilter();
        res.title = tmplDocument.getTitle() + FILTER_LINK + filter.name;
        res.name = filter.name;

        return res;
    }

    public String getTitle() {
        return title;
    }

    public String getName() {
        return name;
    }
}
