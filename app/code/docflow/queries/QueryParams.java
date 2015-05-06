package code.docflow.queries;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.compiler.enums.BuiltInTemplates;
import code.docflow.controlflow.Result;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.model.DocType;
import code.docflow.queries.params.QueryPagingParameter;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.users.CurrentUser;
import code.docflow.utils.BooleanUtil;
import code.docflow.utils.PrimitiveType;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import docflow.DocflowMessages;
import org.joda.time.DateTime;
import play.exceptions.UnexpectedException;
import play.mvc.Scope;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.*;

public final class QueryParams {

    public static final String PARAM_TEXT_SEARCH = "x";
    public static final String PARAM_PAGING = "p";
    public static final String PARAM_FILTER = "f";
    public static final String PARAM_SORT_ORDER = "o";
    public static final String PARAM_DESC = "desc";

    public final DocType docType;

    private DocumentAccessActionsRights _fullRights;

    private final Result _result = new Result();

    private final boolean _isExport;

    private final Scope.Params _httpParams;

    private final TreeMap<String, Object> _params = new TreeMap<String, Object>();

    public QueryParams(final DocType docType) {
        this(docType, null);
    }

    public QueryParams(final DocType docType, final Scope.Params httpParams) {
        this(docType, false, httpParams, null);
    }

    public QueryParams(final DocType docType, boolean isExport, final Scope.Params httpParams, DocumentAccessActionsRights fullRights) {
        checkNotNull(docType, "docType");
        this.docType = docType;
        _isExport = isExport;
        _httpParams = httpParams;
        _fullRights = fullRights;
    }

    public Result getResult() {
        return _result;
    }

    public boolean isExport() {
        return _isExport;
    }

    public DocumentAccessActionsRights getFullRights() {
        if (_fullRights == null)
            _fullRights = RightsCalculator.instance.calculate(docType, CurrentUser.getInstance().getUserRoles());
        return _fullRights;
    }

    public QueryParams param(String name, Object value) {
        checkArgument(!Strings.isNullOrEmpty(name), "name");
        _params.put(name, value == null ? "" : value);
        return this;
    }

    public QueryPagingParameter getPaging(boolean force) {
        QueryPagingParameter res = null;
        if (_params.containsKey(PARAM_PAGING)) { // Note: This allows to keep 'null' as a value
            Object pagingObj = _params.get(PARAM_PAGING);
            if (pagingObj != null)
                if (pagingObj instanceof QueryPagingParameter)
                    res = (QueryPagingParameter) pagingObj;
                else {
                    _result.addMsg(DocflowMessages.error_QueryParamInvalidType_3, PARAM_PAGING, QueryPagingParameter.class.getSimpleName(), pagingObj.getClass().getSimpleName());
                    _params.remove(PARAM_PAGING);
                }
        } else if (_httpParams != null)
            _params.put(PARAM_PAGING, res = QueryPagingParameter.parseHttpRequestParams(_httpParams, force, _result));
        if (res == null && force)
            _params.put(PARAM_PAGING, res = new QueryPagingParameter());
        return res;
    }

    public String getString(String paramName, String defaultValue) {
        checkArgument(!Strings.isNullOrEmpty(paramName), "paramName");
        String res = null;
        if (_params.containsKey(paramName)) {
            final Object resObj = _params.get(paramName);
            if (resObj != null)
                if (resObj instanceof String)
                    res = (String) resObj;
                else
                    _result.addMsg(DocflowMessages.error_QueryParamInvalidType_3, paramName, String.class.getSimpleName(), resObj.getClass().getSimpleName());
        } else if (_httpParams != null) {
            final String value = _httpParams.get(paramName, String.class);
            if (value != null)
                _params.put(paramName, res = value);
        }
        if (res == null)
            _params.put(paramName, res = defaultValue);
        return res;
    }
    
    public Boolean getBoolean(String paramName, Boolean defaultValue) {
        checkArgument(!Strings.isNullOrEmpty(paramName), "paramName");
        Boolean res = null;
        if (_params.containsKey(paramName)) {
            final Object resObj = _params.get(paramName);
            if (resObj != null)
                if (resObj instanceof Boolean)
                    res = (Boolean) resObj;
                else
                    _result.addMsg(DocflowMessages.error_QueryParamInvalidType_3, paramName, Boolean.class.getSimpleName(), resObj.getClass().getSimpleName());
        } else if (_httpParams != null) {
            final Boolean resObj = _httpParams.get(paramName, Boolean.class);
            if (resObj != null)
                _params.put(paramName, res = resObj);

            final String value = _getHttpParam(paramName);
            if (value != null)
                res = BooleanUtil.parse(value);
        }
        if (res == null)
            _params.put(paramName, res = defaultValue);
        return res;
    }
    
    public Integer getInteger(String paramName, Integer defaultValue) {
        checkArgument(!Strings.isNullOrEmpty(paramName), "paramName");
        Integer res = null;
        if (_params.containsKey(paramName)) {
            final Object resObj = _params.get(paramName);
            if (resObj != null)
                if (resObj instanceof Integer)
                    res = (Integer) resObj;
                else
                    _result.addMsg(DocflowMessages.error_QueryParamInvalidType_3, paramName, Integer.class.getSimpleName(), resObj.getClass().getSimpleName());
        } else if (_httpParams != null) {
            final String value = _getHttpParam(paramName);
            if (value != null)
                try {
                    _params.put(paramName, res = Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    _result.addMsg(DocflowMessages.error_QueryParamInvalidValue_2, paramName, value);
                }
        }
        if (res == null)
            _params.put(paramName, res = defaultValue);
        return res;
    }

    private String _getHttpParam(final String paramName) {
        final String[] v = _httpParams.data.get(paramName);
        return v == null ? null : v[0].trim();
    }

    public final SortOrdersEnum getSortOrder() {
        checkNotNull(docType, "docType");
        SortOrdersEnum res = null;
        if (_params.containsKey(PARAM_SORT_ORDER)) {
            Object sortOrderObj = _params.get(PARAM_SORT_ORDER);
            if (sortOrderObj != null)
                if (sortOrderObj instanceof String) {
                    _params.put(PARAM_SORT_ORDER, res = docType.sortOrderEnums.get(((String) sortOrderObj).toUpperCase()));
                    if (res == null)
                        _result.addMsg(DocflowMessages.error_QueryParamInvalidValue_2, PARAM_SORT_ORDER, (String) sortOrderObj);
                } else if (sortOrderObj instanceof SortOrdersEnum)
                    res = (SortOrdersEnum) sortOrderObj;
                else
                    _result.addMsg(DocflowMessages.error_QueryParamInvalidType_3, PARAM_SORT_ORDER, SortOrdersEnum.class.getSimpleName(), sortOrderObj.getClass().getSimpleName());
        } else if (_httpParams != null) {
            final String paramValue = _httpParams.get(PARAM_SORT_ORDER);
            if (!Strings.isNullOrEmpty(paramValue)) {
                _params.put(PARAM_SORT_ORDER, res = docType.sortOrderEnums.get(paramValue.toUpperCase()));
                if (res == null)
                    _result.addMsg(DocflowMessages.error_QueryParamInvalidValue_2, PARAM_SORT_ORDER, paramValue);
            }
        }
        if (res == null)
            _params.put(PARAM_SORT_ORDER, res = (SortOrdersEnum) docType.defaultSortOrderEnum);
        return res;
    }

    public final FiltersEnum getFilter() {
        checkNotNull(docType, "docType");
        FiltersEnum res = null;
        if (_params.containsKey(PARAM_FILTER)) {
            Object filterObj = _params.get(PARAM_FILTER);
            if (filterObj != null)
                if (filterObj instanceof String) {
                    _params.put(PARAM_FILTER, res = docType.filterEnums.get(((String) filterObj).toUpperCase()));
                    if (res == null)
                        _result.addMsg(DocflowMessages.error_QueryParamInvalidValue_2, PARAM_FILTER, (String) filterObj);
                } else if (filterObj instanceof FiltersEnum)
                    res = (FiltersEnum) filterObj;
                else
                    _result.addMsg(DocflowMessages.error_QueryParamInvalidType_3, PARAM_FILTER, FiltersEnum.class.getSimpleName(), filterObj.getClass().getSimpleName());
        } else if (_httpParams != null) {
            final String paramValue = _httpParams.get(PARAM_FILTER);
            if (!Strings.isNullOrEmpty(paramValue)) {
                _params.put(PARAM_FILTER, res = docType.filterEnums.get(paramValue.toUpperCase()));
                if (res == null)
                    _result.addMsg(DocflowMessages.error_QueryParamInvalidValue_2, PARAM_FILTER, paramValue);
            }
        }
        if (res == null)
            _params.put(PARAM_FILTER, res = (FiltersEnum) docType.defaultFilterEnum);
        return res;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("QueryParams[");
        boolean isFirst = true;
        for (Map.Entry<String, Object> entry : _params.entrySet()) {
            if (isFirst) isFirst = false;
            else sb.append(", ");
            sb.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        sb.append(']');
        return sb.toString();
    }

    public ObjectNode toJson() {
        final ObjectNode res = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) _params).entrySet())
            if (entry.getValue() == null)
                res.putNull(entry.getKey());
            else {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key.equals(PARAM_PAGING)) {
                    if (!(value instanceof QueryPagingParameter)) // could be, if paging was not required
                        continue;
                    final QueryPagingParameter paging = (QueryPagingParameter) value;
                    res.put(QueryPagingParameter.PARAM_PAGE_NUMBER, paging.p);
                    res.put(QueryPagingParameter.PARAM_PAGE_SIZE, paging.s);
                    res.put(QueryPagingParameter.PARAM_LOOK_TO_PAGE, paging.c);
                    continue;
                }
                PrimitiveType valueType = PrimitiveType.get(value.getClass());
                switch (valueType) {
                    case BooleanType:
                        res.put(key, (Boolean) value);
                        break;
                    case IntegerType:
                        res.put(key, (Integer) value);
                        break;
                    case LongType:
                        res.put(key, (Long) value);
                        break;
                    case FloatType:
                        res.put(key, (Float) value);
                        break;
                    case DoubleType:
                        res.put(key, (Double) value);
                        break;
                    case DateTimeType:
                        res.put(key, ((DateTime) value).getMillis());
                        break;
                    case NotPrimitiveOrPrimitiveWrapper:
                        res.put(key, JsonTypeBinder.factory.get(value.getClass()).toJson(value, BuiltInTemplates.DICT.toString()));
                        break;
                    default:
                        res.put(key, value.toString());
                }
            }
        return res;
    }

    public String toQueryString() {
        final StringBuilder res = new StringBuilder();
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) _params).entrySet())
            if (entry.getValue() == null) {
                if (res.length() > 0) res.append("&");
                res.append(entry.getKey());
            } else {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key.equals(PARAM_PAGING)) {
                    if (!(value instanceof QueryPagingParameter)) // could be, if paging was not required
                        continue;
                    final QueryPagingParameter paging = (QueryPagingParameter) value;
                    if (res.length() > 0) res.append("&");
                    res.append(QueryPagingParameter.PARAM_PAGE_NUMBER).append("=").append(paging.p).append("&");
                    res.append(QueryPagingParameter.PARAM_PAGE_SIZE).append("=").append(paging.s).append("&");
                    res.append(QueryPagingParameter.PARAM_LOOK_TO_PAGE).append("=").append(paging.c);
                    continue;
                }
                if (value instanceof Boolean) {
                    if (res.length() > 0) res.append("&");
                    res.append(key).append("=").append((Boolean) value ? "1" : "0");
                } else {
                    final String strValue = value.toString();
                    if (!Strings.isNullOrEmpty(strValue)) {
                        if (res.length() > 0) res.append("&");
                        try {
                            res.append(key).append("=").append(URLEncoder.encode(strValue, "UTF-8"));
                        } catch (UnsupportedEncodingException e) {
                            throw new UnexpectedException(e);
                        }
                    }
                }
            }
        return res.toString();
    }
}
