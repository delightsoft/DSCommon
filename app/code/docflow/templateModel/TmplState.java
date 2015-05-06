package code.docflow.templateModel;

import code.docflow.model.State;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class TmplState {

    public static final String STATE_ROOT = "state.";
    public static final String STATE_LINK = "." + STATE_ROOT;

    /**
     * Title in form for localization.
     */
    String title;

    /**
     * Name of the state.
     */
    String name;

    /**
     * Color to be used as item header background.
     */
    String color;

    @SuppressWarnings("unchecked")
    public static TmplState buildFor(TmplModel root, TmplDocument document, State state) {

        final TmplState res = new TmplState();
        res.name = state.name;
        res.title = "doc." + document.name + STATE_LINK + state.name;
        res.color = state.color;

        return res;
    }

    public String getTitle() {
        return title;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }
}
