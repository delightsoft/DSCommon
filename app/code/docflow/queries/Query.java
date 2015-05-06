package code.docflow.queries;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.DocflowConfig;
import code.docflow.model.DocType;
import code.docflow.model.FieldReference;
import code.docflow.model.FieldStructure;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.jsonBinding.OptionalValuesAccessor;
import code.docflow.jsonBinding.annotations.doc.JsonTemplate;
import code.docflow.jsonBinding.annotations.field.JsonAccessor;
import code.docflow.jsonBinding.annotations.field.JsonExclude;
import code.docflow.docs.Document;
import code.docflow.docs.DocumentPersistent;
import code.docflow.types.DocumentRef;
import code.docflow.users.CurrentUser;
import code.docflow.utils.*;
import static com.google.common.base.Preconditions.*;
import com.google.common.base.Strings;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import play.db.jpa.JPA;
import play.mvc.Http;

import javax.persistence.TypedQuery;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Base class Query&lt;Class&gt; class.  Constains static helpers
 */

// TODO: Remove this class after 3/1/15 - since it replaced by more sophisticated classes

public abstract class Query {

    public static final int LAST_PAGE = -1;
    public static final String DESC = " DESC";

    public static class Result<D extends Document> {

        public QueryParamsOld query;

        public List<D> list;

        @JsonExclude
        public CsvFormatter<D> csvFormatter;
    }

    public abstract static class CsvFormatter<D extends Document> {
        public String filename;

        public abstract void writeHeader(CsvWriter csvWriter, DocumentAccessActionsRights fullRights);

        public abstract void writeLine(D line, CsvWriter csvWriter, DocumentAccessActionsRights fullRights);
    }

    public static class NoValue {
        public String id = "";
        public String text;

        public NoValue(String text) {
            this.text = text;
        }
    }

    public static class QueryParamsOld {
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

        @JsonAccessor(OptionalValuesAccessor.class)
        @JsonTemplate("dict")
        public Map<String, Object> params = new HashMap<String, Object>();
    }

    public static class Paging {
        public int p;
        public int s;
        public int c;
    }

    public static class FulltextSearch extends Builder {

        public boolean nothingToSearch;

        public StringBuilder sbFrom = new StringBuilder();
        public StringBuilder sbWhere = new StringBuilder();

        String userRoles;
        Class entityClass;

        protected FulltextSearch(String userRoles, Class entityClass) {
            this.userRoles = userRoles;
            this.entityClass = entityClass;
        }

        @Override
        protected void init() {
            DocType docType = DocflowConfig.getDocumentTypeByClass(entityClass);
            DocumentAccessActionsRights fullRights = RightsCalculator.instance.calculate(docType, userRoles);
            BitArray mask = fullRights.viewMask.copy();
            mask.intersect(docType.levelMask);
            if (!buildFulltextSearchQuery(sbFrom, sbWhere, "doc", docType, userRoles, fullRights, mask)) {
                nothingToSearch = true;
                sbFrom = sbWhere = null;
            }
        }

        public <T extends DocumentPersistent> Result<T> listEntitiesWithFulltextSearch(String what, String from, String where, TreeMap<String, Object> params, String searchText, String orderBy, Class entityClass, Paging paging, String from2, String calculatedField) {
            from = from + sbFrom.toString();
            where = "(" + where + ")" + " AND (" + sbWhere.toString() + ")";
            if (params == null)
                params = new TreeMap<String, Object>();
            params.put("x", "%" + searchText.toUpperCase() + "%");
            return listEntities(what, from, where, params, orderBy, entityClass, from2, paging, calculatedField);
        }
    }

    public static class FulltextSearchByType extends Builder {

        TypeBuildersFactory<FulltextSearch> factory;
        String userRoles;

        protected FulltextSearchByType(String userRoles) {
            this.userRoles = userRoles;
        }

        @Override
        protected void init() {
            factory = new TypeBuildersFactory<FulltextSearch>() {
                @Override
                public FulltextSearch newInstance(TypeDescription typeDesc) {
                    checkArgument(typeDesc.parameters == null);
                    return new FulltextSearch(userRoles, typeDesc.type);
                }
            };
        }
    }

    public static final TemplatesBuildersFactory<FulltextSearchByType> factory = new TemplatesBuildersFactory<FulltextSearchByType>() {
        @Override
        public FulltextSearchByType newInstance(String roles) {
            return new FulltextSearchByType(roles);
        }
    };

    public static <T extends Document> T dictParam(String paramName, Class<T> dictClass, Http.Request request) {
        final String paramValue = request.params.get(paramName);
        if (!Strings.isNullOrEmpty(paramValue)) {
            DocumentRef ref = DocumentRef.parse(paramValue);
            if (ref != DocumentRef.NULL) {
                DocType docType = DocflowConfig.getDocumentTypeByClass(dictClass);
                checkState(docType != null);
                if (ref.type.equalsIgnoreCase(docType.name)) {
                    return (T) ref.getDocumentUnsafe();
                }
            }
        }
        return null;
    }

    public static <T extends Enum<T>> T enumParam(String paramName, Class<T> enumClass, Http.Request request) {
        final String paramValue = request.params.get(paramName);
        T res = null;
        if (!Strings.isNullOrEmpty(paramValue))
            try {
                res = Enum.valueOf(enumClass, paramValue.toUpperCase());
            } catch (IllegalArgumentException e) {
                // proceed
            }
        if (res == null)
            res = enumClass.getEnumConstants()[0];
        return res;
    }

    public static <T extends Enum<T>> T enumParam(String paramName, EnumCaseInsensitiveIndex<T> index, Http.Request request) {
        final String paramValue = request.params.get(paramName);
        T res = null;
        if (!Strings.isNullOrEmpty(paramValue))
            try {
                res = index.get(paramValue.toUpperCase());
            } catch (IllegalArgumentException e) {
                // proceed
            }
        if (res == null)
            res = index.getDefaultValue();
        return res;
    }

    /**
     * Extracts boolean parameter.
     */
    public static boolean boolParam(String paramName, Http.Request request) {
        final String paramValue = request.params.get(paramName);
        return paramValue != null && "1".equals(paramValue);
    }

    /**
     * Inverses sort order by adding DESC after each sort column.
     */
    public static String setDirection(String sortOrder, boolean sortOrderDesc) {
        if (!sortOrderDesc || Strings.isNullOrEmpty(sortOrder))
            return sortOrder;
        String[] args = sortOrder.split(",");
        if (args.length < 2)
            return sortOrder + DESC;
        else {
            StringBuilder sb = new StringBuilder();
            for (String arg : args) {
                if (sb.length() > 0)
                    sb.append(',');
                sb.append(arg).append(DESC);
            }
            return sb.toString();
        }
    }

    /**
     * Converts boolean value to url parameter string value.
     */
    public static String toString(boolean value) {
        return value ? "1" : "0";
    }

    public static Paging pagingParams(Http.Request request) {

        final Paging res = new Paging();

        try {
            res.p = Integer.parseInt(request.params.get("p"));
            if (res.p < 1 && res.p != LAST_PAGE)
                res.p = 1;
        } catch (NumberFormatException e) {
            res.p = 1;
        }

        try {
            res.s = Integer.parseInt(request.params.get("s"));
            if (!(1 <= res.s && res.s <= 10000))
                res.s = 15;
        } catch (NumberFormatException e) {
            res.s = 15;
        }

        try {
            res.c = Integer.parseInt(request.params.get("c"));
            if (res.c < res.p)
                res.c = res.p;
        } catch (NumberFormatException e) {
            res.c = res.p;
        }

        return res;
    }

    /**
     * Select document with fulltext search basic implementation.
     */
    public static <T extends DocumentPersistent> Result<T> listEntities(String what, String from, String where, TreeMap<String, Object> params, String searchText, String orderBy, Class entityClass, String from2, Paging paging) {
        return listEntities(what, from, where, params, searchText, orderBy, entityClass, from2, paging, null);
    }

    public static <T extends DocumentPersistent> Result<T> listEntities(String what, String from, String where, TreeMap<String, Object> params, String searchText, String orderBy, Class entityClass, String from2, Paging paging, String calculatedField) {
        if (searchText != null) {
            searchText = searchText.trim();
            if (searchText != "") {
                FulltextSearch fulltextSearch = factory.get(CurrentUser.getInstance().getUserRoles()).factory.get(entityClass);
                if (!fulltextSearch.nothingToSearch)
                    return fulltextSearch.listEntitiesWithFulltextSearch(what, from, where, params, searchText, orderBy, entityClass, paging, from2, calculatedField);
            }
        }
        return listEntities(what, from, where, params, orderBy, entityClass, from2, paging, calculatedField);
    }

    /**
     * Selects entities with pagination.
     */
    private static <T extends DocumentPersistent> Result<T> listEntities(String what, String from, String where, TreeMap<String, Object> params, String orderBy, Class entityClass, String from2, Paging paging, String calculatedField) {

        checkArgument(!Strings.isNullOrEmpty(what), "what");
        checkArgument(!Strings.isNullOrEmpty(from), "from");
        checkArgument(!Strings.isNullOrEmpty(orderBy), "orderBy");
        checkArgument(!Strings.isNullOrEmpty(from2), "from2");
        checkNotNull(paging, "paging");

        final Session session = (Session) JPA.em().getDelegate();

        final org.hibernate.Query pageLookup = session.createQuery(
                "SELECT doc.id" + (Strings.isNullOrEmpty(calculatedField) ? "" : ", " + calculatedField) +
                        " FROM " + from + (Strings.isNullOrEmpty(where) ? "" : (" WHERE " + where)) + " ORDER BY " + orderBy);

        if (params != null)
            for (Map.Entry<String, Object> param : params.entrySet())
                pageLookup.setParameter(param.getKey(), param.getValue());

        final long[] pageIdes = new long[paging.s];

        pageLookup.setFetchSize(1000);

        if (paging.p != LAST_PAGE)
            pageLookup.setMaxResults(
                    paging.c == paging.p ?
                            paging.p * paging.s :
                            (paging.c - 1) * paging.s + 1);

        final ScrollableResults scroll = pageLookup.scroll(ScrollMode.FORWARD_ONLY);
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

        final Result<T> res = new Result<T>();
        res.query = new QueryParamsOld();

        if (lines == 0) {
            res.query.p = 0;
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines; i++) {
                if (i > 0)
                    sb.append(',');
                sb.append(pageIdes[i]);
            }
            final TypedQuery<T> q = JPA.em().createQuery
                    ("SELECT " + what + " FROM " + from2 + " WHERE doc.id in (" + sb.toString() + ") ORDER BY " + orderBy,
                            entityClass);
            res.list = q.getResultList();
            res.query.p = currentPage;
        }
        res.query.s = paging.s;
        res.query.c = res.query.p + (extra + (paging.s - 1)) / paging.s;

        return res;
    }

    private static class AliasIterator {
        private int n = 0;

        public String next() {
            return "s" + (++n);
        }
    }

    private static boolean buildFulltextSearchQuery(
            StringBuilder sbFrom, StringBuilder sbWhere, String prefix, DocType docType, String userRoles,
            DocumentAccessActionsRights fullRights, BitArray levelMask) {
        return buildFulltextSearchQuery(false, sbFrom, sbWhere, prefix, docType, userRoles, fullRights, levelMask, 0, new AliasIterator());
    }

    private static boolean buildFulltextSearchQuery(
            boolean useFullname, StringBuilder sbFrom, StringBuilder sbWhere, String prefix, DocType docType, String userRoles,
            DocumentAccessActionsRights fullRights, BitArray levelMask, int level, AliasIterator aliasIterator) {
        BitArray.EnumTrueValues tv = levelMask.getEnumTrueValues();
        int i = 0;
        int len = sbWhere.length();
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
                        buildFulltextSearchQuery(true, sbFrom, sbWhere, prefix, docType, userRoles, fullRights, structureLevelMask, level, aliasIterator);
                    } else {
                        String structureAlias = aliasIterator.next();
                        int lengthWithoutStructure = sbFrom.length();
                        sbFrom.append(" LEFT JOIN ").append(prefix + "." + field.name).append(" AS ").append(structureAlias);
                        if (!buildFulltextSearchQuery(false, sbFrom, sbWhere, structureAlias, docType, userRoles, fullRights, structureLevelMask, level, aliasIterator))
                            sbFrom.setLength(lengthWithoutStructure);
                    }
                    break;
                case REFERS:
                    if (field.implicitFieldType != null) // it's foreign key (FK)
                        continue;
                    if (level == 2) // it's already refered document
                        continue;
                    DocType refDocType = DocflowConfig.instance.documents.get(((FieldReference) field).refDocument.toUpperCase());
                    DocumentAccessActionsRights refDocFullRights = RightsCalculator.instance.calculate(refDocType, userRoles);
                    BitArray refDocLevelMask = refDocFullRights.viewMask.copy();
                    refDocLevelMask.intersect(refDocType.levelMask);
                    String docAlias = aliasIterator.next();
                    int lengthWithoutDoc = sbFrom.length();
                    sbFrom.append(" LEFT JOIN ").append(prefix + "." + field.name).append(" AS ").append(docAlias);
                    if (!buildFulltextSearchQuery(false, sbFrom, sbWhere, docAlias, refDocType, userRoles, refDocFullRights, refDocLevelMask, level + 1, aliasIterator))
                        sbFrom.setLength(lengthWithoutDoc);
                    break;
                case STRING:
                case TEXT:
                    if (sbWhere.length() > 0)
                        sbWhere.append(" OR ");
                    sbWhere.append("UPPER(").append(prefix + "." + (useFullname ? field.fullname : field.name)).append(") LIKE :x");
                    break;
            }
        }
        return sbWhere.length() > len;
    }

}
