package code.docflow.queries;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.DocflowConfig;
import code.docflow.compiler.enums.BuiltInTemplates;
import code.docflow.model.DocType;
import code.docflow.model.Template;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import play.exceptions.UnexpectedException;
import play.i18n.Messages;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class CounterBuilder {

    public static final String FIELD_TITLE = "title";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_COUNT = "count";
    public static final String FIELD_REF = "ref";

    private final DocType _docType;
    private String _name;
    private String _title;
    private final Method _method;
    private final QueryParams _params;

    public CounterBuilder(final DocType docType) {
        this(docType, (String) null);
    }

    public CounterBuilder(final DocType docType, String template) {
        this(docType, findTemplate(docType, template));
    }

    public CounterBuilder(final DocType docType, final Template template) {
        checkNotNull(docType, "docType");
        checkNotNull(template, "template");
        checkArgument(docType == template.document, "Invalid pair of docType and template");

        _docType = docType;

        final String queryName = Strings.isNullOrEmpty(template.query) ? template.name : template.query;
        final String upperQueryName = queryName.toUpperCase();

        _method = docType.queryMethods.get(upperQueryName);
        if (_method == null)
            throw new UnexpectedException(String.format("DocType '%s': Missing query '%s'.", docType.name, queryName));

        _params = new QueryParams(docType);
    }

    private static Template findTemplate(DocType docType, String template) {
        checkNotNull(docType, "docType");
        if (Strings.isNullOrEmpty(template)) template = BuiltInTemplates.LIST.toString();
        final Template tmpl = docType.templates.get(template.toUpperCase());
        if (tmpl == null)
            throw new UnexpectedException(String.format("DocType '%s': Missing template '%s'.", docType.name, template));
        return tmpl;
    }

    public CounterBuilder name(final String name) {
        checkArgument(!Strings.isNullOrEmpty(name), "name");
        _name = name;
        return this;
    }

    public CounterBuilder name(final String name, final Object... args) {
        checkArgument(!Strings.isNullOrEmpty(name), "name");
        _name = String.format(name, args);
        return this;
    }

    public CounterBuilder title(final String title) {
        checkArgument(!Strings.isNullOrEmpty(title), "title");
        _title = Messages.get(title);
        return this;
    }

    public CounterBuilder title(final String title, final Object... args) {
        checkArgument(!Strings.isNullOrEmpty(title), "title");
        _title = Messages.get(title, args);
        return this;
    }

    public void param(final String name, final Object value) {
        _params.param(name, value);
    }

    public ObjectNode end() {
        final ObjectNode res = JsonNodeFactory.instance.objectNode();
        if (_name != null) res.put(FIELD_NAME, _name);
        if (_title != null) res.put(FIELD_TITLE, _title);
        final DocListBuilder builder;
        try {
            builder = (DocListBuilder) _method.invoke(null, _params);
        } catch (IllegalAccessException e) {
            throw new UnexpectedException(e);
        } catch (InvocationTargetException e) {
            throw new UnexpectedException(e.getCause());
        }
        res.put(FIELD_COUNT, builder.count());
        res.put(FIELD_REF, "/doc/" + _docType.name + "?" + _params.toQueryString());
        return res;
    }

    public static Pattern docPath = Pattern.compile("^/doc/([^/\\?]*)(?:/?\\?(.*))?$", Pattern.CASE_INSENSITIVE);

    public static CounterBuilder buildFromUrl(final String path) {

        final Matcher matcher = docPath.matcher(path);
        if (!matcher.find())
            throw new IllegalArgumentException("path: '" + path + "'");

        final String docTypeName = matcher.group(1);
        final DocType docType = DocflowConfig.instance.documents.get(docTypeName.toUpperCase());
        if (docType == null)
            throw new UnexpectedException(String.format("Invalid DocType '%s'.", docTypeName));

        String template = null;
        final String queryString = matcher.group(2);
        final TreeMap<String, String> params = new TreeMap();
        if (queryString != null)
            for (String param : queryString.split("&")) {
                int pos = param.indexOf("=");
                final String paramName = param.substring(0, pos);
                final String paramValue;
                try {
                    paramValue = pos == -1 ? "" : URLDecoder.decode(param.substring(pos + 1), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new UnexpectedException(e);
                }
                if (paramName.equals("t"))
                    template = paramValue;
                else
                    params.put(paramName, paramValue);
            }

        final CounterBuilder res = new CounterBuilder(docType, template);
        for (Map.Entry<String, String> pair : params.entrySet())
            res.param(pair.getKey(), pair.getValue());

        return res;
    }
}
