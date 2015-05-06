package code.docflow;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

import code.docflow.controlflow.Result;
import code.docflow.compiler.*;
import code.docflow.model.*;
import code.docflow.yaml.YamlMessages;
import code.docflow.docs.Document;
import play.Play;
import play.exceptions.UnexpectedException;
import play.vfs.VirtualFile;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

import static com.google.common.base.Preconditions.checkState;

/**
 * Singletone root for Documents Flow & Rights Management mechanism.
 */
public class DocflowConfig {

    public static final String GENERATED_JAVA_FILE_FINGERPRINT = "// Generate by DocflowGenerator";

    public static final String PATH_DOCFLOW = "docflow";
    public static final String PATH_MODELS = "models";

    public static final String PATH_CONFIG = "docflow/config.yaml";
    public static final String PATH_DOCUMENTS = "docflow/documents";
    public static final String PATH_ROLES = "docflow/roles";
    public static final String PATH_FIELD_TYPES = "docflow/fieldTypes.yaml";

    public static final String MESSAGES_FILE_SUFFIX = ".messages.yaml";

    /**
     * Special case field.  Servs to say that in this table column should be show object itself.
     */
    public static final String FIELD_SELF = "_self";
    public static final String DEFAULT_FIELDS_TEMPLATE = "_default";
    public static final String UDT_DOCUMENT = "_udt";

    public static DocflowConfig instance = new DocflowConfig();

    public static List<VirtualFile> appPath = null;

    public DocflowModule MODULE_DSCOMMON;

    public DocflowModule[] modules;

    public DocflowModule currentModule;

    public LinkedHashMap<String, Field> fieldTypes = new LinkedHashMap<String, Field>();
    public DocType udtTypes;
    public LinkedHashMap<String, Message> messages;
    public LinkedHashMap<String, LinkedHashMap<String, Message>> messagesByFiles;
    public final TreeMap<String, DocType> documents = new TreeMap<String, DocType>();
    public TreeMap<String, Role> roles = new TreeMap<String, Role>();
    public int globalStatesCount;

    public DocType[] documentsArray;

    public TreeMap<String, DocType> documentByTable = new TreeMap<String, DocType>();

    public static void _resetForTest() {
        checkState(Play.mode == Play.Mode.DEV);
        instance = new DocflowConfig();
        docTypeMap.clear();
    }

    public void load(Result result) {
        loadCompileAndLinkToCode(result);
        if (result.isError())
            result.addMsg(YamlMessages.error_FailedToLoadDocflowConfig);
    }

    private void loadCompileAndLinkToCode(Result result) {

        checkState(documents.size() == 0); // this method should work only once

        Compiler010LoadConfig.doJob(this, result);

        Compiler020LoadMessages.doJob(this, result);

        Compiler030LoadFieldTypes.doJob(this, result);

        Compiler040LoadDocuments.doJob(this, result);
        Compiler050LoadRoles.doJob(this, result);
        if (result.isError())
            return;

        Compiler060PrepareMessages.doJob(this, result);

        Compiler070PrepareFieldTypes.doJob(this, result);

        Compiler110DocumentsStep1.doJob(this, result);
        if (result.isError())
            return;
        Compiler120DocumentsStep2.doJob(this, result);
        if (result.isError())
            return;
        Compiler130DocumentsStep3.doJob(this, result);
        if (result.isError())
            return;
        Compiler140DocumentsStep4.doJob(this, result);
        if (result.isError())
            return;

        Compiler210TemplatesStep1.doJob(this, result);
        if (result.isError())
            return;
        Compiler220TemplatesStep2.doJob(this, result);
        if (result.isError())
            return;

        Compiler310Roles.doJob(this, result);
        if (result.isError())
            return;

        if (appPath == null) { // not a code generator
            Compiler410LinkToCode.doJob(this, result);
            if (result.isError())
                return;
        }

        result.addMsg(YamlMessages.debug_DocflowConfigLoadedSuccessfully);
    }

    public DocflowModuleYamlConfig sampleConfigField;

    public static ConcurrentSkipListMap<String, DocType> docTypeMap = new ConcurrentSkipListMap<String, DocType>();

    /**
     * Returns document type by model class name.
     */
    public static DocType getDocumentTypeByClass(Class type) {
        final String typeName = type.getName();
        DocType res = docTypeMap.get(typeName);
        if (res == null)
            docTypeMap.putIfAbsent(typeName, res = _getDocumentTypeByClass(typeName));
        return res == DocType.NOT_A_DOCUMENT ? null : res;
    }

    private static DocType _getDocumentTypeByClass(String typeName) {
        if (typeName.startsWith(DocType.MODELS_PACKAGE)) {
            // Note: different version of javassis (they linked to specific play versions) have different postfix for a classnames
            final int proxyPostfix = typeName.indexOf(Play.version.compareTo("1.3") > -1 ? "_$$_jvst" : "_$$_javassist_");
            if (proxyPostfix > 0) {
                final String n = typeName.substring(DocType.MODELS_PACKAGE.length(), proxyPostfix);
                final DocType doc = DocflowConfig.instance.documents.get(n.toUpperCase());
                return doc == null ? DocType.NOT_A_DOCUMENT : doc;
            }
        }
        final Class type = Play.classloader.getClassIgnoreCase(typeName);
        if (type == null) // this is possible, since getClassIgnoreCase() knows only classes defined within the project
            return DocType.NOT_A_DOCUMENT;
        if (!Document.class.isAssignableFrom(type))
            return DocType.NOT_A_DOCUMENT;

        final Method typeMethod;
        try {
            typeMethod = type.getMethod("_type");
            final int mod = typeMethod.getModifiers();
            checkState(Modifier.isPublic(mod), typeName);
            checkState(Modifier.isStatic(mod), typeName);
            checkState(typeMethod.getReturnType() == DocType.class, typeName);
        } catch (NoSuchMethodException e) {
            checkState(false, "Type '%s' extends Document, but is not defined thru yaml declaration.", typeName);
            return null;
        }

        try {
            return (DocType) typeMethod.invoke(null);
        } catch (IllegalAccessException e) {
            throw new UnexpectedException(e);
        } catch (InvocationTargetException e) {
            throw new UnexpectedException(e);
        }
    }
}
