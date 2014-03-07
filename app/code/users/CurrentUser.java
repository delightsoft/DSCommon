package code.users;

import code.controlflow.Result;
import code.docflow.DocflowConfig;
import code.models.Document;
import controllers.DocflowHttpController;
import play.exceptions.JavaExecutionException;
import play.mvc.After;
import play.mvc.Before;
import play.mvc.Controller;
import play.mvc.results.RenderTemplate;
import play.utils.Java;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class CurrentUser {

    public static final Document SYSTEM_USER = new SystemUser();

    private static ThreadLocal<CurrentUser> instance = new ThreadLocal<CurrentUser>() {
        @Override
        protected CurrentUser initialValue() {
            return new CurrentUser();
        }
    };

    public boolean inActionScope;

    private String roles;

    private Document user;

    /**
     * Base class for login controllers.  Child has to implement project specific user sign-in and reauthentication.
     */
    public static class Authentication extends Controller {
        static void signin(String returnPath) {
            throw new UnsupportedOperationException();
        }

        static void reauthenticate() {
            throw new UnsupportedOperationException();
        }
    }

    public static class CheckAccess extends Controller {

        @Before
        public static void checkAccess() throws Throwable {
            try {
                Java.invokeChildOrStatic(Authentication.class, "reauthenticate");
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            if (!CurrentUser.getInstance().isAuthenticated()) {
                if (request.path.toUpperCase().startsWith("/API")) {
                    Result notAuthenticated = new Result();
                    notAuthenticated.setCode(Result.NotAuthenticated);
                    throw new DocflowHttpController.ActionResult(null, null, null, null, notAuthenticated);
                }
                try {
                    Java.invokeChildOrStatic(Authentication.class, "signin", request.path);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        }

        @After
        public static void resetUser() {
            CurrentUser.getInstance().setUser(null, null);
        }
    }

    public interface Scope<T> {
        void call();
    }

    public static CurrentUser getInstance() {
        return instance.get();
    }

    public boolean isAuthenticated() {
        return user != null;
    }

    public String getUserRoles() {
        return roles;
    }

    public Document getUser() {
        return user;
    }

    public void setUser(Document user, String roles) {
        this.user = user;
        this.roles = roles != null ? roles : DocflowConfig.BuiltInRoles.ANONYMOUS.toString();
    }

    public static <T> T userScope(Document user, String roles, Callable<T> scope) {
        final CurrentUser currentUser = getInstance();
        final Document prevUser = currentUser.getUser();
        final String prevRoles = currentUser.getUserRoles();
        currentUser.setUser(user, roles);
        try {
            return scope.call();
        } catch (RenderTemplate tmpl) { // to be able call render(), renderJson etc. within scope, as Controller.action result
            throw tmpl;
        } catch (InvocationTargetException e) {
            throw new JavaExecutionException(e.getCause());
        } catch (Exception e) {
            throw new JavaExecutionException(e);
        } finally {
            currentUser.setUser(prevUser, prevRoles);
        }
    }

    public static <T> T systemUserScope(Callable<T> scope) {
        return userScope(SYSTEM_USER, DocflowConfig.BuiltInRoles.SYSTEM.toString(), scope);
    }

    public static <T> T inActionScope(Callable<T> scope) {
        return inActionScope(true, scope);
    }

    public static <T> T inActionScope(boolean inActionMode, Callable<T> scope) {
        final CurrentUser currentUser = CurrentUser.getInstance();
        final boolean prevInActionScopeValue = currentUser.inActionScope;
        currentUser.inActionScope = inActionMode;
        try {
            return scope.call();
        } catch (RenderTemplate tmpl) { // to be able call render(), renderJson etc. within scope, as Controller.action result
            throw tmpl;
        } catch (InvocationTargetException e) {
            throw new JavaExecutionException(e.getCause());
        } catch (Exception e) {
            throw new JavaExecutionException(e);
        } finally {
            currentUser.inActionScope = prevInActionScopeValue;
        }
    }
}
