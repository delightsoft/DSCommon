package code;

import code.docflow.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.messages.MessageContainerClass;
import code.docflow.messages.MessageTemplate;
import code.docflow.utils.TestUtil;
import com.google.common.base.Strings;
import play.Play;
import play.PlayPlugin;
import play.classloading.ApplicationClasses;
import play.db.jpa.JPAPlugin;
import play.exceptions.UnexpectedException;
import play.mvc.Http;
import play.mvc.Router;
import play.vfs.VirtualFile;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class DSCommonPlugin extends PlayPlugin {

    public static final String NG_APP_BASE = "ngApp.base";
    private boolean isDevMode;

    private static final String NO_BASEURL = "";
    private static String domain;
    private static Integer port;

    @Override
    public boolean rawInvocation(Http.Request request, Http.Response response) throws Exception {

        // Fix request domain and port values by values taken from 'application.baseUrl'
        if (domain == null) {
            String appBaseUrl = Play.configuration.getProperty("application.baseUrl", "");
            if (Strings.isNullOrEmpty(appBaseUrl))
                domain = NO_BASEURL;
            else {
                if (appBaseUrl.endsWith("/")) {
                    // remove the trailing slash
                    appBaseUrl = appBaseUrl.substring(0, appBaseUrl.length() - 1);
                }
                Pattern compile = Pattern.compile("https?://([^:]*)(:(.*))?/?");
                Matcher matcher = compile.matcher(appBaseUrl);
                if (matcher.find()) {
                    domain = matcher.group(1);
                    try {
                        port = Integer.parseInt(matcher.group(3));
                    } catch (NumberFormatException e) {
                        // nothing
                    }
                }
            }
        }
        if (domain != NO_BASEURL) {
            request.domain = domain;
            request.port = port == null ? 80 : port; // TODO: Add selection for HTTPS
        }

        return false;
    }


    @Override
    public void onRoutesLoaded() {
        if (Play.mode == Play.Mode.DEV) {
            Router.addRoute("GET", "/@fix", "DocflowHttpFixTextController.fixText");
            Router.addRoute("GET", "/@docflow", "ngCodeGen.DocflowGenerator.index");
            Router.addRoute("GET", "/@sql", "ngCodeGen.MSSQLScript.index");
            Router.addRoute("GET", "/@docflow/entity/{entityName}", "ngCodeGen.DocflowGenerator.entity");
            Router.addRoute("GET", "/@docflow/rightsByRoles", "ngCodeGen.DocflowRights.rightsByRoles");
            Router.addRoute("GET", "/@docflow/rightsByStates", "ngCodeGen.DocflowRights.rightsByStates");
            Router.addRoute("POST", "/@docflow/setAppPath", "ngCodeGen.DocflowGenerator.setAppPath");
            Router.addRoute("POST", "/@docflow/generate/entities", "ngCodeGen.DocflowGenerator.generateModels");
        }
        String ngAppBase = Play.configuration.getProperty(NG_APP_BASE, "");
        if (ngAppBase.length() > 0)
            ngAppBase = "/" + ngAppBase;
        Router.addRoute(Integer.MAX_VALUE, "GET", ngAppBase + "/?", "DocflowHttpIndexController.app", null, null);
        Router.addRoute(Integer.MAX_VALUE, "GET", ngAppBase + "/{<.*>rest}", "DocflowHttpIndexController.app", null, null);
    }

    @Override
    public void onApplicationReady() {
        if (!isDevMode)
            return;
        String protocol = "http";
        String port = "9000";
        if (Play.configuration.getProperty("https.port") != null) {
            port = Play.configuration.getProperty("https.port");
            protocol = "https";
        } else if (Play.configuration.getProperty("http.port") != null) {
            port = Play.configuration.getProperty("http.port");
        }
        System.out.println("~ Go to " + protocol + "://localhost:" + port + "/@docflow to for code generator and other docflow dev features");
        System.out.println("~");
    }

    @Override
    public void onApplicationStart() {

        if (Play.mode == Play.Mode.DEV)
            TestUtil.resetBeforeReloadDocflowConfig();
        countFiles();

        JPAPlugin.startTx(true); // Required by DocflowConfig to create sample objects

        final Result result;
        try {
            result = new Result();
            DocflowConfig.instance.load(result);
            if (result.isError())
                throw new DocflowConfigException(result);
        } finally {
            JPAPlugin.closeTx(true);
        }

        result.toLogger();

        // force static MessageTemplate messages to be instantiated
        final List<ApplicationClasses.ApplicationClass> messagesClasses = Play.classes.getAnnotatedClasses(MessageContainerClass.class);
        for (ApplicationClasses.ApplicationClass clazz : messagesClasses) {
            for (Field field : clazz.javaClass.getFields()) {
                final int mod = field.getModifiers();
                if (Modifier.isPublic(mod) && Modifier.isStatic(mod) && field.getType() == MessageTemplate.class) {
                    try {
                        field.get(null); // make sure static value is created, to be used by Result2Json.toResult(...)
                    } catch (IllegalAccessException e) {
                        throw new UnexpectedException(e);
                    }
                }
            }
        }

        lastLoading = System.currentTimeMillis();
    }

    public long lastLoading;
    public int lastFilesCount;

    @Override
    public void detectChange() {
        try {
            final int t = lastFilesCount;
            if (countFiles() || t != lastFilesCount)
                onApplicationStart();
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    private boolean countFiles() {
        lastFilesCount = 0;
        boolean r = false;
        for (VirtualFile vf : DocflowConfig.appPath != null ? DocflowConfig.appPath : Play.javaPath) {
            if (!vf.exists())
                continue;
            r |= traverseDir(vf.child(DocflowConfig.PATH_DOCFLOW).getRealFile());
            r |= traverseDir(vf.child(DocflowConfig.PATH_MODELS).getRealFile());
        }
        return r;
    }

    /**
     * True, if modification are found.
     */
    public boolean traverseDir(File dir) {
        boolean res = false;
        final File[] files = dir.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                if (file.isDirectory())
                    res |= traverseDir(file);
                else if (file.lastModified() > lastLoading)
                    res = true;
            }
            lastFilesCount += files.length;
        }
        return res;
    }
}
