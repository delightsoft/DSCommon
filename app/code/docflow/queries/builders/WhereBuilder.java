package code.docflow.queries.builders;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import static com.google.common.base.Preconditions.*;
import com.google.common.base.Strings;

public class WhereBuilder<T> {

    private final T _context;
    private final StringBuilder _sb;
    private final int _p;

    public WhereBuilder(final T context, final StringBuilder sb) {
        _context = context;
        sb.append('(');
        _sb = sb;
        _p = sb.length();
    }

    public WhereBuilder(final T context, final StringBuilder sb, final String from, final String alias) {
        _context = context;
        sb.append("FROM ").append(from).append(" AS ").append(alias).append(" WHERE ");
        _sb = sb;
        _p = sb.length();
    }

    public final WhereBuilder<SqlBuilder> orExists(final String from, final String alias) {
        if (_sb.length() > _p) _sb.append("OR ");
        _sb.append("EXISTS(");
        return new WhereBuilder(this, _sb, from, alias);
    }

    public final WhereBuilder<SqlBuilder> andExists(final String from, final String alias) {
        if (_sb.length() > _p) _sb.append("AND ");
        _sb.append("EXISTS(");
        return new WhereBuilder(this, _sb, from, alias);
    }

    public WhereBuilder<T> and(final String expr) {
        checkArgument(!Strings.isNullOrEmpty(expr), "expr");
        if (_sb.length() > _p) _sb.append("AND");
        _sb.append('(').append(expr).append(')');
        return this;
    }

    public WhereBuilder<T> and(final WhereBuilder builder) {
        checkNotNull(builder, "builder");
        if (_sb.length() > _p) _sb.append("AND");
        _sb.append('(').append(builder.whereExpr()).append(')');
        return this;
    }

    public WhereBuilder<WhereBuilder<T>> and() {
        if (_sb.length() > _p) _sb.append("AND");
        return new WhereBuilder<WhereBuilder<T>>(this, _sb);
    }

    public WhereBuilder<T> or(final String expr) {
        checkArgument(!Strings.isNullOrEmpty(expr), "expr");
        if (_sb.length() > _p) _sb.append("OR");
        _sb.append('(').append(expr).append(')');
        return this;
    }

    public WhereBuilder<T> or(final WhereBuilder builder) {
        checkNotNull(builder, "builder");
        if (_sb.length() > _p) _sb.append("AND");
        _sb.append('(').append(builder.whereExpr()).append(')');
        return this;
    }

    public WhereBuilder<WhereBuilder<T>> or() {
        if (_sb.length() > _p) _sb.append("OR");
        return new WhereBuilder<WhereBuilder<T>>(this, _sb);
    }

    public T end() {
        checkState(_context != null); // otherwise, it was created by argumentless constructor
        _sb.append(')');
        return _context;
    }

    /**
     * It's only to be used for orSelect and andSelect builders.
     */
    public void endWithOrderBy(final String orderBy) {
        checkArgument(!Strings.isNullOrEmpty(orderBy), "orderBy");
        _sb.append(" ORDER BY ").append(orderBy).append(")");
    }

    StringBuilder whereExpr() {
        checkState(_context == null);
        return _sb;
    }

    @Override
    public String toString() {
        return "WhereBuilder[" + _sb.substring(_p) + "]";
    }
}
