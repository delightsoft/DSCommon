package controllers;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.DocflowConfig;
import code.docflow.model.*;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.models.Document;
import code.users.CurrentUser;
import play.mvc.Controller;

import java.util.concurrent.Callable;

public class DocflowRights extends Controller {

    public static class R {
        public String fieldAccess(Field field, Role role) {
            final DocumentAccessActionsRights rights = RightsCalculator.instance.calculate(field.document, role.name);
            String res = rights.viewMask.get(field.index) ? "v" : "";
            res += rights.updateMask.get(field.index) ? "u" : "";
            return res;
        }

        public String actionAccess(Action action, Role role) {
            final DocumentAccessActionsRights rights = RightsCalculator.instance.calculate(action.document, role.name);
            return rights.actionsMask.get(action.index) ? "+" : "";
        }

        public static class StateHandler extends Document {

            public State state;

            public static DocType doc = new DocType();

            @Override
            public DocType _docType() {
                return doc;
            }

            @Override
            public State _state() {
                return state;
            }

            @Override
            public String _fullId() {
                return null;
            }
        }

        static StateHandler stateHandler = new StateHandler();

        public String fieldAccess(final Field field, final State state) {
            return CurrentUser.systemUserScope(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    stateHandler.state = state;
                    final DocumentAccessActionsRights rights = RightsCalculator.instance.calculate(stateHandler, CurrentUser.getInstance());
                    String res = rights.viewMask.get(field.index) ? "v" : "";
                    res += rights.updateMask.get(field.index) ? "u" : "";
                    return res;
                }
            });
        }

        public String actionAccess(final Action action, final State state) {
            return CurrentUser.systemUserScope(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    stateHandler.state = state;
                    final DocumentAccessActionsRights rights = RightsCalculator.instance.calculate(stateHandler, CurrentUser.getInstance());
                    return rights.actionsMask.get(action.index) ? "+" : "";
                }
            });
        }
    }

    public static void rightsByRoles() {

        final DocflowConfig docflowConfig = DocflowConfig.instance;
        final R rights = new R();
        render(docflowConfig, rights);
    }

    public static void rightsByStates() {

        final DocflowConfig docflowConfig = DocflowConfig.instance;
        final R rights = new R();
        render(docflowConfig, rights);
    }


}
