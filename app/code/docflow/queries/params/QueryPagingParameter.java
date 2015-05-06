package code.docflow.queries.params;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.controlflow.Result;
import docflow.DocflowMessages;
import play.mvc.Scope;

/**
 * Paging control fields.
 */
public class QueryPagingParameter {
    /**
     * Page.
     */
    public int p;
    /**
     * Page size.
     */
    public int s;
    /**
     * Check to page.
     */
    public int c;

    public QueryPagingParameter() {
        this.p = FIRST_PAGE;
        this.s = DEFAULT_PAGE_SIZE;
        this.c = NO_PAGE_LOOKUP;
    }

    public QueryPagingParameter(int page, int pageSize) {
        this(page, pageSize, NO_PAGE_LOOKUP);
    }

    public QueryPagingParameter(int page, int pageSize, int pageLookup) {
        this.p = page;
        this.s = pageSize;
        this.c = pageLookup;
    }

    public static final int FIRST_PAGE = 1;
    public static final int LAST_PAGE = -1;
    public static final int NO_PAGE_LOOKUP = 0;

    public static final int DEFAULT_PAGE_SIZE = 15;
    public static final int MAX_PAGE_SIZE = 10000;

    public static final String PARAM_PAGE_NUMBER = "p";
    public static final String PARAM_PAGE_SIZE = "s";
    public static final String PARAM_LOOK_TO_PAGE = "c";

    public static QueryPagingParameter parseHttpRequestParams(final Scope.Params params, final boolean force, final Result result) {

        final String pParam = params.get(PARAM_PAGE_NUMBER);
        if (pParam == null && !force)
            return null;

        final QueryPagingParameter res = new QueryPagingParameter();

        if (pParam != null)
            try {
                res.p = Integer.parseInt(pParam);
                if (res.p < FIRST_PAGE && res.p != LAST_PAGE)
                    res.p = FIRST_PAGE;
            } catch (NumberFormatException e) {
                res.p = FIRST_PAGE;
                result.addMsg(DocflowMessages.error_QueryParamInvalidValue_2, PARAM_PAGE_NUMBER, pParam);
            }

        final String sParam = params.get(PARAM_PAGE_SIZE);
        if (sParam != null)
            try {
                res.s = Integer.parseInt(sParam);
                if (!(1 <= res.s && res.s <= MAX_PAGE_SIZE)) {
                    res.s = DEFAULT_PAGE_SIZE;
                }
            } catch (NumberFormatException e) {
                res.s = DEFAULT_PAGE_SIZE;
                result.addMsg(DocflowMessages.error_QueryParamInvalidValue_2, PARAM_PAGE_SIZE, sParam);
            }

        final String cParam = params.get(PARAM_LOOK_TO_PAGE);
        if (cParam != null)
            try {
                res.c = Integer.parseInt(cParam);
                if (res.c < res.p)
                    res.c = res.p;
            } catch (NumberFormatException e) {
                res.c = res.p;
                result.addMsg(DocflowMessages.error_QueryParamInvalidValue_2, PARAM_LOOK_TO_PAGE, cParam);
            }

        return res;
    }

    @Override
    public String toString() {
        return "QueryPaging[p: " + p + ", s: " + s + ", c: " + c + ']';
    }
}
