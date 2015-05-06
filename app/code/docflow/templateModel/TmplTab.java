package code.docflow.templateModel;

import code.docflow.collections.Item;
import code.docflow.model.Field;
import code.docflow.model.Template;
import code.docflow.model.TemplateTab;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class TmplTab {

    public static final String TAB_MAIN = "_main";

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

    ImmutableList<String> fields;

    String _docType;
    String _template;

    public static TmplTab buildMainTab(Template template, TmplTemplate tmpl) {

        final TmplTab res = new TmplTab();

        res.title = "tab.mainTab";
        res.name = TAB_MAIN;
        res._docType = tmpl.document.name;
        res._template = tmpl.name;

        if (template.mainTabFields != null) {
            ImmutableList.Builder<String> fieldsBuilder = ImmutableList.builder();
            for (Field fld : template.mainTabFields.values())
                fieldsBuilder.add(fld.name);
            res.fields = fieldsBuilder.build();
        }

        return res;
    }

    public static TmplTab buildFor(TmplTemplate tmpl, TemplateTab tab) {

        final TmplTab res = new TmplTab();

        res.title = tmpl.getTitle() + ".tab." + tab.name;
        res.name = tab.name;
        res._docType = tab.docType;
        res._template = tab.template;

        if (tab.fields != null) {
            ImmutableList.Builder<String> fieldsBuilder = ImmutableList.builder();
            for (Item fld : tab.fields.values())
                fieldsBuilder.add(fld.name);
            res.fields = fieldsBuilder.build();
        }

        if (tab.options != null) {
            ImmutableMap.Builder<String, Object> optionsBuilder = ImmutableMap.builder();
            for (Map.Entry<String, Object> entry : tab.options.entrySet())
                if (entry.getValue() != null)
                    optionsBuilder.put(entry.getKey(), entry.getValue());
            res.options = optionsBuilder.build();
        }

        return res;
    }

    public void linkToTemplate(TmplModel root) {
        final TmplDocument doc = root.documentByName.get(_docType.toUpperCase());
        // Note: Document might be missing due to limited access rights
        if (doc == null)
            return;

        this.template = doc.templatesByName.get(_template.toUpperCase());

        if (fields != null)
            this.template = TmplTemplate.buildTabTemplate(fields, this.template);
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

    public ImmutableList<String> getFields() {
        return fields;
    }

    public ImmutableMap<String, Object> getOptions() {
        return options;
    }
}
