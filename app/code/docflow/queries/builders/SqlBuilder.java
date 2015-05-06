package code.docflow.queries.builders;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.model.DocType;
import com.google.common.base.Strings;
import org.apache.commons.lang.StringUtils;
import play.Play;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class SqlBuilder {

    protected static final boolean isNotMSSQL;

    static {
        final String jpaDialect = Play.configuration.getProperty("jpa.dialect");
        isNotMSSQL = jpaDialect != null && !"org.hibernate.dialect.SQLServerDialect".equals(jpaDialect);
    }

    protected final StringBuilder _select = new StringBuilder();
    protected final StringBuilder _from = new StringBuilder();
    protected final StringBuilder _where = new StringBuilder();
    protected final StringBuilder _order = new StringBuilder();

    protected final TreeMap<String, Object> _sqlParams = new TreeMap<String, Object>();

    private static ConcurrentHashMap<String, String> _inversedOrders = new ConcurrentHashMap<String, String>();

    public final SqlBuilder select(final String expr) {
        checkArgument(!Strings.isNullOrEmpty(expr), "expr");
        if (_select.length() > 0) _select.append(',');
        _select.append(expr);
        return this;
    }

    public final SqlBuilder select(final String expr, final String alias) {
        select(expr);
        if (Strings.isNullOrEmpty(alias)) _select.append(' ').append(alias);
        return this;
    }

    private int aliasIndex = 0;

    public final String nextAlias() {
        return "s" + (++aliasIndex);
    }

    public final SqlBuilder from(final String table, final String alias) {
        checkArgument(!Strings.isNullOrEmpty(table), "table");
        checkArgument(!Strings.isNullOrEmpty(alias), "alias");
        if (_from.length() > 0) _from.append(',');
        _from.append(table).append(" ").append(alias);
        return this;
    }

    public class Position {
        public int order;
        public int where;
    }

    public final Position savePosition() {
        final Position res = new Position();
        res.order = _order.length();
        res.where = _where.length();
        return res;
    }

    public final SqlBuilder revertPosition(Position pos) {
        _order.setLength(pos.order);
        _where.setLength(pos.where);
        return this;
    }

    public final SqlBuilder leftJoin(final String field, final String alias) {
        checkArgument(!Strings.isNullOrEmpty(field), "field");
        checkArgument(!Strings.isNullOrEmpty(alias), "alias");
        _from.append(" LEFT JOIN ").append(field).append(" AS ").append(alias);
        return this;
    }

    public final SqlBuilder from(final DocType docType, final String alias) {
        checkNotNull(docType, "docType");
        return from(docType.entities.get(0).tableName, alias);
    }

    public final int whereLength() {
        return _where.length();
    }

    public final SqlBuilder and(final String expr) {
        checkArgument(!Strings.isNullOrEmpty(expr), "expr");
        if (_where.length() > 0) _where.append("AND");
        _where.append('(').append(expr).append(')');
        return this;
    }

    public final SqlBuilder and(final WhereBuilder builder) {
        checkNotNull(builder, "builder");
        if (_where.length() > 0) _where.append("AND");
        _where.append('(').append(builder.whereExpr().toString()).append(')');
        return this;
    }
    
    public final WhereBuilder<SqlBuilder> orExists(final String from, final String alias) {
        if (_where.length() > 0) _where.append("OR ");
        _where.append("EXISTS(");
        return new WhereBuilder(this, _where, from, alias);
    }

    public final WhereBuilder<SqlBuilder> andExists(final String from, final String alias) {
        if (_where.length() > 0) _where.append("AND ");
        _where.append("EXISTS(");
        return new WhereBuilder(this, _where, from, alias);
    }

    public final WhereBuilder<SqlBuilder> andNotExists(final String from, final String alias) {
        if (_where.length() > 0) _where.append("AND ");
        _where.append("NOT EXISTS(");
        return new WhereBuilder(this, _where, from, alias);
    }

    public final WhereBuilder<SqlBuilder> and() {
        if (_where.length() > 0) _where.append("AND");
        return new WhereBuilder<SqlBuilder>(this, _where);
    }

    public final SqlBuilder or(final String expr) {
        checkArgument(!Strings.isNullOrEmpty(expr), "expr");
        if (_where.length() > 0) _where.append("OR");
        _where.append('(').append(expr).append(')');
        return this;
    }

    public final SqlBuilder or(final WhereBuilder builder) {
        if (_where.length() > 0) _where.append("OR");
        _where.append('(').append(builder.whereExpr().toString()).append(')');
        return this;
    }
    
    public final WhereBuilder<SqlBuilder> or() {
        if (_where.length() > 0) _where.append("OR");
        return new WhereBuilder<SqlBuilder>(this, _where);
    }

    public final SqlBuilder order(final String expr) {
        return order(expr, false);
    }

    public static final String DOC = "doc.";
    public static final String DESC = " DESC";

    public final SqlBuilder order(final String expr, final boolean inverseDirection) {
        checkArgument(!Strings.isNullOrEmpty(expr), "expr");
        if (_order.length() > 0) _order.append(",");
        if (!inverseDirection)
            _order.append(expr);
        else {
            String res = _inversedOrders.get(expr);
            if (res == null) {
                String[] args = expr.split(",");
                for (int i = 0; i < args.length; i++) {
                    String pos = args[i].trim();
                    if (pos.toUpperCase().endsWith(DESC))
                        args[i] = pos.substring(0, pos.length() - DESC.length());
                    else
                        args[i] = pos + DESC;
                }
                _inversedOrders.put(expr, res = StringUtils.join(args, ','));
            }
            _order.append(res);
        }
        return this;
    }

    public final SqlBuilder param(final String name, final Object value) {
        checkArgument(!Strings.isNullOrEmpty(name), "name");
        _sqlParams.put(name, value);
        return this;
    }

    public String selectStatement() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SELECT ").append(_select).append(" FROM ").append(_from);
        if (_where.length() > 0) sb.append(" WHERE ").append(_where);
        if (_order.length() > 0) sb.append(" ORDER BY ").append(_order);
        return sb.toString();
    }

    public void setParams(org.hibernate.Query query) {
        for (Map.Entry<String, Object> param : _sqlParams.entrySet())
            query.setParameter(param.getKey(), param.getValue());
    }

    @Override
    public String toString() {
        return "SqlBuilder[" + selectStatement() + "]";
    }
}
