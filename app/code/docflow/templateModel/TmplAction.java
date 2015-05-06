package code.docflow.templateModel;

import code.docflow.DocflowConfig;
import code.docflow.compiler.enums.CrudActions;
import code.docflow.model.Action;
import code.docflow.model.DocType;
import code.docflow.model.Field;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class TmplAction {

    public static final String ACTION_ROOT = "action.";
    public static final String ACTION_LINK = "." + ACTION_ROOT;
    public static final String ACTION_PARAM_ROOT = "param.";
    public static final String ACTION_PARAM_LINK = "." + ACTION_PARAM_ROOT;

    /**
     * Document this field belongs to.
     */
    TmplDocument document;

    /**
     * True, if action should shown out of edit form.  Such actions suppose to start new work.
     */
    public boolean outOfForm;

    /**
     * True, if it's exceptional action.  Such actions remain in Other Actions list at the screen.
     */
    public boolean other;

    /**
     * True, if action prior update of the document.
     */
    public boolean update;

    /**
     * True, if action does not required data to be valid.
     */
    public boolean display;

    /**
     * Script what will be used within angular template of this Action button.
     */
    public String script;

    /**
     * Angular ng-if condition that will be applied to the action button.
     */
    public String ngif;

    /**
     * Angular ng-disabled condition that will be applied to the action button.
     */
    public String ngdisabled;

    /**
     * Title in form for localization.
     */
    String title;

    /**
     * Full name of the action.
     */
    String name;

    /**
     * Actinos parameters.
     */
    ImmutableList<TmplField> params;

    ImmutableMap<String, TmplField> paramsByName;

    public static TmplAction buildFor(TmplDocument tmplDoc, DocType docType, Action action) {

        checkNotNull(action);

        final TmplAction res = new TmplAction();
        res.document = tmplDoc;
        res.title = "doc." + docType.name + ACTION_LINK + action.name;
        res.name = action.name;
        res.outOfForm = action.outOfForm;
        res.other = action.other;
        res.update = action.update;
        res.display = action.display;
        res.script = action.script;
        res.ngif = action.ngif;
        res.ngdisabled = action.ngdisabled;

        tmplDoc.actionTitle.put(res.name.toUpperCase(), res.title);

        if (action.implicitAction != CrudActions.UPDATE && action.params != null) {
            final ImmutableList.Builder<TmplField> paramsListBuilder = ImmutableList.builder();
            final ImmutableMap.Builder<String, TmplField> paramsMapBuilder = ImmutableMap.builder();
            for (Field field : action.params.values()) {
                final TmplField parameter = TmplField.buildFor(tmplDoc, null, res, null, null, field, null, null, false, false, true);
                paramsListBuilder.add(parameter);
                paramsMapBuilder.put(parameter.name, parameter);
            }
            res.params = paramsListBuilder.build();
            res.paramsByName = paramsMapBuilder.build();
        }

        return res;
    }

    public TmplDocument getDocument() {
        return document;
    }

    public boolean getOutOfForm() {
        return outOfForm;
    }

    public boolean getOther() {
        return other;
    }

    public String getScript() {
        return script;
    }

    public String getNgif() {
        return ngif;
    }

    public String getNgdisabled() {
        return ngdisabled;
    }

    public String getTitle() {
        return title;
    }

    public String getName() {
        return name;
    }

    public ImmutableList<TmplField> getParams() {
        return params;
    }

    public TmplField getParamByName(String name) {
        return paramsByName.get(name.toUpperCase());
    }
}
