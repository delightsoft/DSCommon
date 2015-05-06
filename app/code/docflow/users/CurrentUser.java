package code.docflow.users;

import code.docflow.compiler.enums.BuiltInActionSource;
import code.docflow.compiler.enums.BuiltInRoles;
import code.docflow.docs.Document;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import models.BuiltInUser;
import play.exceptions.UnexpectedException;
import play.mvc.After;
import play.mvc.Before;
import play.mvc.Controller;
import play.mvc.results.RenderTemplate;
import play.mvc.results.Result;
import play.utils.Java;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.concurrent.Callable;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class CurrentUser {

    public static final BuiltInUser SYSTEM_USER = new BuiltInUser(BuiltInActionSource.SYSTEM.toString(), BuiltInRoles.SYSTEM.toString());

    public static final BuiltInUser ANONYMOUS_USER = new BuiltInUser(BuiltInActionSource.ANONYMOUS.toString(), BuiltInRoles.ANONYMOUS.toString());

    private static ThreadLocal<CurrentUser> instance = new ThreadLocal<CurrentUser>() {
        @Override
        protected CurrentUser initialValue() {
            return new CurrentUser();
        }
    };

    public boolean inActionScope;

    private String roles;

    public HashSet<String> rolesSet;

    private Document user;

    private Document demonstrator;

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
                if (!CurrentUser.getInstance().isAuthenticated()) {
                    final String upperRequest = request.path.toUpperCase();
                    // Rule: API and TMPL suppose to work with current authorization.  Other words, access to them will not enforce user authentication.
                    if (!upperRequest.startsWith("/API") && !upperRequest.startsWith("/TMPL"))
                        Java.invokeChildOrStatic(Authentication.class, "signin", request.path);
                    else {
                        // error(401, "Unauthorized"); - this requires presents of 401.txt template
                        response.status = 401;
                        renderText("Unauthorized");
                    }
                }
            } catch (InvocationTargetException e) {
                throw e.getCause();
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

    public String getUserRoles() { return roles != null ? roles : BuiltInRoles.ANONYMOUS.toString(); }

    public Document getUser() {
        return user;
    }

    public Document getDemonstrator() {
        return demonstrator;
    }

    public boolean hasRole(String role) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(role), "role");
        return rolesSet.contains(role.toUpperCase());
    }

    public void setUser(Document user, String roles) {
        setUser(user, roles, null);
    }

    public void setUser(Document user, String roles, Document demonstrator) {
        this.user = user;
        this.roles = roles;
        this.demonstrator = demonstrator;
        this.rolesSet = new HashSet<String>();
        if (roles != null)
            for (String role : roles.split(","))
                rolesSet.add(role.toUpperCase());
    }

    public static <T> T userScope(Document user, String roles, Callable<T> scope) {
        return userScope(user, roles, false, scope);
    }

    public static <T> T userScope(Document user, String roles, boolean inActionScope, Callable<T> scope) {
        final CurrentUser currentUser = getInstance();
        final Document prevUser = currentUser.getUser();
        final String prevRoles = currentUser.getUserRoles();
        final boolean initialInActionScope = currentUser.inActionScope;
        currentUser.setUser(user, roles);
        try {
            if (inActionScope)
                currentUser.inActionScope = true;
            return scope.call();
        } catch (Result tmpl) { // to be able call render(), renderJson etc. within scope, as Controller.action result
            throw tmpl;
        } catch (InvocationTargetException e) {
            throw new UnexpectedException(e.getCause());
        } catch (Exception e) {
            throw new UnexpectedException(e);
        } finally {
            currentUser.inActionScope = initialInActionScope;
            currentUser.setUser(prevUser, prevRoles);
        }
    }

    public static <T> T systemUserScope(Callable<T> scope) {
        return userScope(SYSTEM_USER, SYSTEM_USER.roles, scope);
    }

    public static <T> T systemUserScope(boolean inActionScope, Callable<T> scope) {
        return userScope(SYSTEM_USER, SYSTEM_USER.roles, inActionScope, scope);
    }

    public static <T> T inActionScope(Callable<T> scope) {
        return userScope(CurrentUser.getInstance().getUser(), CurrentUser.getInstance().getUserRoles(), true, scope);
    }

    public static <T> T inActionScope(boolean inActionScope, Callable<T> scope) {
        return userScope(CurrentUser.getInstance().getUser(), CurrentUser.getInstance().getUserRoles(), inActionScope, scope);
    }
}
