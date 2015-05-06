package code.docflow.queries;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.DocflowConfig;
import code.docflow.compiler.enums.BuiltInTypes;
import code.docflow.docs.DocumentPersistent;
import code.docflow.jsonBinding.RecordAccessor;
import code.docflow.model.DocType;
import code.docflow.model.Field;
import code.docflow.model.FieldReference;
import code.docflow.model.FieldStructure;
import code.docflow.queries.builders.SqlBuilder;
import code.docflow.queries.builders.WhereBuilder;
import code.docflow.queries.params.QueryPagingParameter;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.users.CurrentUser;
import code.docflow.utils.BitArray;
import com.google.common.base.Strings;
import org.apache.commons.lang.StringUtils;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import play.db.jpa.GenericModel;
import play.db.jpa.JPA;

import javax.persistence.TypedQuery;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.*;

public class DocListBuilder extends SqlBuilder {

    protected final QueryParams _params;

    private FiltersEnum _filter;
    private boolean _filterOn = true;

    private SortOrdersEnum _sortOrder;
    private boolean _sortOrderOn = true;

    private boolean _fullTextSearchOn = true;
    private ConcurrentHashMap<String, String> fixedOrders = new ConcurrentHashMap<String, String>();

    public DocListBuilder(final QueryParams params) {
        checkArgument(params != null, "params");
        _params = params;
        from(params.docType, "doc");
    }

    public String pageSelectStatement() {
        defaultParams();
        final StringBuilder sb = new StringBuilder();
        sb.append("SELECT doc.id FROM ").append(_from);
        if (_where.length() > 0) sb.append(" WHERE ").append(_where);
        if (_order.length() > 0) sb.append(" ORDER BY ").append(_order);
        return sb.toString();
    }

    public String countStatement() {
        defaultParams();
        final StringBuilder sb = new StringBuilder();
        sb.append("SELECT Count(*) FROM ").append(_from);
        if (_where.length() > 0) sb.append(" WHERE ").append(_where);
        return sb.toString();
    }

    /**
     * Sets filter from the set of standard filters, or turns filtering off by assinging a null.
     */
    public final DocListBuilder filter(final Enum filter) {
        checkArgument(filter == null || filter instanceof FiltersEnum);
        if (filter == null) {
            _filter = null;
            _filterOn = false;
        } else {
            _filter = (FiltersEnum) filter;
            _filterOn = true;
        }
        return this;
    }

    /**
     * Sets filter from the set of standard filters, or turns filtering off by assinging a null.
     */
    public final DocListBuilder sortOrder(final Enum sortOrder) {
        checkArgument(sortOrder == null || sortOrder instanceof SortOrdersEnum);
        if (sortOrder == null) {
            _sortOrder = null;
            _sortOrderOn = false;
        } else {
            _sortOrder = (SortOrdersEnum) sortOrder;
            _sortOrderOn = true;
        }
        return this;
    }

    /**
     * Casting text fields to varchar string.  Otherwise MS SQL returns error, if we use text fields for sorting.
     * Ref: http://www.sqlteam.com/forums/topic.asp?TOPIC_ID=43610
     *
     * Note: This code is written with assumption that fields in 'order by' clause are fields from main docType used
     * by this builder.
     */
    public String fixOrderForMSSQL(String expr) {
        // TODO: Keep source docType from from(...) calls, so this method could process remain fields after doc.
        String res = fixedOrders.get(expr);
        if (res == null) {
            String[] args = expr.split(",");
            boolean anyChange = false;
            for (int i = 0; i < args.length; i++) {
                String pos = args[i].trim();
                if (pos.startsWith(DOC)) {
                    final boolean isDesc = pos.toUpperCase().endsWith(DESC);
                    final String fieldName = isDesc ? pos.substring(0, pos.length() - DESC.length()).trim() : pos;
                    final Field field = _params.docType.fieldByFullname.get(fieldName.substring(DOC.length()).toUpperCase());
                    if (field != null && (field.type == BuiltInTypes.TEXT || field.type == BuiltInTypes.RESULT)) {
                        args[i] = "SUBSTRING(" + fieldName + ",0,1000)" + (isDesc ? DESC : "");
                        // TODO: Consider doing the same for Postgres SQL - but UPPER_CASE instead, to make ordering caseinsensetive
                        anyChange = true;
                    }
                }
            }
            fixedOrders.put(expr, res = (anyChange ? StringUtils.join(args, ',') : expr));
        }
        return res;
    }

    private void defaultParams() {
        if (_filterOn) {
            if (_filter == null) {
                _filter = _params.getFilter();
                if (_filter == null)
                    _filter = _params.docType.defaultFilterEnum;
            }
            if (_filter != null && !Strings.isNullOrEmpty(_filter.getWhere()))
                and(_filter.getWhere());
            _filterOn = false;
        }
        if (_sortOrderOn) {
            if (_sortOrder == null) {
                _sortOrder = _params.getSortOrder();
                if (_sortOrder == null)
                    _sortOrder = _params.docType.defaultSortOrderEnum;
            }
            if (_sortOrder != null)
                order(isNotMSSQL ? _sortOrder.getOrderBy() : fixOrderForMSSQL(_sortOrder.getOrderBy()),
                        _params.getBoolean(QueryParams.PARAM_DESC, false));
            _sortOrderOn = false;
        }
        if (_fullTextSearchOn) {
            final String x = _params.getString(QueryParams.PARAM_TEXT_SEARCH, "");
            if (!Strings.isNullOrEmpty(x)) {
                if (isNotMSSQL)
                    param(QueryParams.PARAM_TEXT_SEARCH, "%" + x.toUpperCase() + "%");
                else
                    param(QueryParams.PARAM_TEXT_SEARCH, "%" + x + "%");
                buildFulltextSearchQuery();
            }
            _fullTextSearchOn = false;
        }
    }

    public <T extends DocumentPersistent> List<T> getPage() {
        final Session session = (Session) JPA.em().getDelegate();

        final org.hibernate.Query pageLookup = session.createQuery(pageSelectStatement());
        setParams(pageLookup);

        final QueryPagingParameter paging = _params.getPaging(true);

        final long[] pageIdes = new long[paging.s];

        pageLookup.setFetchSize(1000);

        if (paging.p != QueryPagingParameter.LAST_PAGE)
            pageLookup.setMaxResults(
                    paging.c == paging.p ?
                            paging.p * paging.s :
                            (paging.c - 1) * paging.s + 1);

        final ScrollableResults scroll = pageLookup.scroll(ScrollMode.FORWARD_ONLY);
        List res = null;
        int lines = 0;
        int currentPage = 1;
        int extra = 0;
        while (scroll.next()) {
            if (lines == paging.s) {
                if (currentPage == paging.p) {
                    extra = 1;
                    break;
                }
                currentPage++;
                lines = 0;
            }
            pageIdes[lines++] = (Long) scroll.get(0);
        }
        while (scroll.next()) {
            extra++;
        }

        if (lines == 0) {
            paging.p = 0;
        } else {

            final TreeMap<Long, Integer> idToIndex = new TreeMap<Long, Integer>();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines; i++) {
                if (i > 0)
                    sb.append(',');
                sb.append(pageIdes[i]);
                idToIndex.put(pageIdes[i], i);
            }

            final TypedQuery q = JPA.em().createQuery
                    ("SELECT doc FROM " + _params.docType.entities.get(0).tableName + " doc WHERE doc.id in (" + sb.toString() + ")",
                            _params.docType.jsonBinder.recordAccessor.type);
            res = q.getResultList();

            // make list order match order of ides
            final RecordAccessor recordAccessor = _params.docType.jsonBinder.recordAccessor;
            Collections.sort(res, new Comparator<GenericModel>() {
                @Override
                public int compare(GenericModel o1, GenericModel o2) {
                    return Long.compare(idToIndex.get(recordAccessor.getId(o1)), idToIndex.get(recordAccessor.getId(o2)));
                }
            });

            paging.p = currentPage;
        }
        paging.s = paging.s;
        paging.c = paging.p + (extra + (paging.s - 1)) / paging.s;
        return res;
    }

    public long count() {
        final Session session = (Session) JPA.em().getDelegate();

        final org.hibernate.Query pageLookup = session.createQuery(countStatement());

        if (_params != null)
            for (Map.Entry<String, Object> param : _sqlParams.entrySet())
                pageLookup.setParameter(param.getKey(), param.getValue());

        return (Long) pageLookup.uniqueResult();
    }

    // TODO: Leave in fulltext search only fields that are visible to user in current template
    // TODO: Use link of calculated fields to their source fields ...I should find a way to keep such info in the code ...maybe annotation
    // TODO: Consider keeping rights+template caching of fulltext part of request
    // TODO: Optimize from - use same alias for same table
    // TODO: Think of next generation of SqlBuilder - at the moment it's combersome to created multi-level request (See Boiron.docflow.queries.QueryCar)

    private void buildFulltextSearchQuery() {
        final Position position = savePosition();
        final WhereBuilder<SqlBuilder> and = and();
        if (!buildFulltextSearchQuery(false, "doc", _params.docType, and, _params.getFullRights(), _params.docType.levelMask, 0))
            revertPosition(position);
        else
            and.end();
    }

    private boolean buildFulltextSearchQuery(
            boolean useFullname, String prefix, DocType docType, WhereBuilder andBuilder,
            DocumentAccessActionsRights fullRights, BitArray levelMask, int level) {
        BitArray.EnumTrueValues tv = levelMask.getEnumTrueValues();
        int i = 0;
        int len = whereLength();
        while ((i = tv.next()) != -1) {
            code.docflow.model.Field field = docType.allFields.get(i);
            if (field.implicitFieldType != null || field.calculated)
                continue;
            switch (field.type) {
                case TAGS:
                case STRUCTURE:
                case SUBTABLE:
                    FieldStructure fieldStructure = (FieldStructure) field;
                    BitArray structureLevelMask = fullRights.viewMask.copy();
                    structureLevelMask.intersect(fieldStructure.levelMask);
                    if (((FieldStructure) field).single) {
                        buildFulltextSearchQuery(true, prefix, docType, andBuilder, fullRights, structureLevelMask, level);
                    } else {
                        String structureAlias = nextAlias();
                        final Position position = savePosition();
                        final WhereBuilder<SqlBuilder> existsBuilder = andBuilder.orExists(prefix + "." + field.name, structureAlias);
                        if (!buildFulltextSearchQuery(false, structureAlias, docType, existsBuilder, fullRights, structureLevelMask, level))
                            revertPosition(position);
                        else
                            existsBuilder.end();
                    }
                    break;
                case REFERS:
                    if (field.implicitFieldType != null) // it's foreign key (FK)
                        continue;
                    if (level == 2) // it's already refered document
                        continue;
                    DocType refDocType = DocflowConfig.instance.documents.get(((FieldReference) field).refDocument.toUpperCase());
                    DocumentAccessActionsRights refDocFullRights = RightsCalculator.instance.calculate(refDocType, CurrentUser.getInstance().getUserRoles());
                    BitArray refDocLevelMask = refDocFullRights.viewMask.copy();
                    refDocLevelMask.intersect(refDocType.levelMask);
                    String docAlias = nextAlias();
                    final Position position = savePosition();
                    final WhereBuilder<SqlBuilder> existsBuilder = andBuilder.orExists(prefix + "." + field.name, docAlias);
                    if (!buildFulltextSearchQuery(false, docAlias, refDocType, existsBuilder, refDocFullRights, refDocLevelMask, level + 1))
                        revertPosition(position);
                    else
                        existsBuilder.end();
                    break;
                case STRING:
                case TEXT:
                    if (isNotMSSQL)
                        andBuilder.or("UPPER(" + prefix + "." + (useFullname ? field.fullname : field.name) + ") LIKE :x");
                    else
                        andBuilder.or(prefix + "." + (useFullname ? field.fullname : field.name) + " LIKE :x");
                    break;
            }
        }
        return whereLength() > len;
    }
}
