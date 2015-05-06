package controllers;

import code.DSCommonPlugin;
import code.docflow.users.CurrentUser;
import code.docflow.utils.HttpUtil;
import com.google.common.base.Strings;
import play.Play;
import play.db.jpa.Transactional;
import play.mvc.With;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

@With(CurrentUser.CheckAccess.class)
public class DocflowHttpIndexController extends DocflowControllerBase {

    public static final String DEFAULT_DOC_TYPE = "_default";

    @Transactional(readOnly = true) // Note: Transaction is required by controllers.ngAuth.Login.reauthenticate()
    public static void status() {
        render();
    }

    /**
     * Returns angular application.
     */
    @Transactional(readOnly = true)
    public static void app() {
        String baseUrl = Play.configuration.getProperty("application.baseUrl", "http://localhost:9000/");
        String ngAppBase = Play.configuration.getProperty(DSCommonPlugin.NG_APP_BASE, "");
        if (ngAppBase.length() > 0)
            baseUrl += ngAppBase + "/";
        render(baseUrl);
    }

    /**
     * Redirects requests to given path.  Supports script-redirect, since HTTP redirect gets danaged by IIS UrlRewrite.
     */
    @Transactional(readOnly = true)
    public static void redirect(String rest, String path) {
        if (!Strings.isNullOrEmpty(rest)) {
            if (!path.endsWith("/"))
                path = path + "/";
            path = path + rest;
        }
        HttpUtil.scriptRedirect(path);
    }

    @Transactional(readOnly = true)
    public static void options() {
        // Enable CORS (http://enable-cors.org/server.html)
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
    }
}
