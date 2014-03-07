package code.docflow.templateModel;

import code.docflow.model.TemplateTab;
import com.google.common.collect.ImmutableMap;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class TmplTab {

    /**
     * Title in form for localization.
     */
    String title;

    /**
     * Name of the tab.
     */
    String name;

    /**
     * Tab template.
     */
    TmplTemplate template;

    ImmutableMap<String, Object> options;

    String _docType;
    String _template;

    public static TmplTab buildFor(TmplTemplate tmpl, TemplateTab tab) {

        final TmplTab res = new TmplTab();

        res.title = tmpl.getTitle() + ".tab." + tab.name;
        res.name = tab.name;
        res._docType = tab.docType;
        res._template = tab.template;

        if (tab.options != null) {
            ImmutableMap.Builder<String, Object> optinosBuilder = ImmutableMap.builder();
            for (Map.Entry<String, Object> entry : tab.options.entrySet())
                if (entry.getValue() != null)
                    optinosBuilder.put(entry.getKey(), entry.getValue());
            res.options = optinosBuilder.build();
        }

        return res;
    }

    public void linkToTemplate(TmplRoot root, TmplDocument tmplDocument) {
        final TmplDocument doc = root.documentByName.get(_docType.toUpperCase());
        this.template = (doc != null) ? doc.templatesByName.get(_template.toUpperCase()) : null;
    }

    public String getTitle() {
        return title;
    }

    public String getName() {
        return name;
    }

    public TmplTemplate getTemplate() {
        return template;
    }

    public ImmutableMap<String, Object> getOptions() {
        return options;
    }
}
