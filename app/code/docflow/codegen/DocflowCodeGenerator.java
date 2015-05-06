package code.docflow.codegen;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.DocflowConfigException;
import code.docflow.DocflowConfig;
import code.docflow.compiler.enums.BuiltInTypes;
import code.docflow.controlflow.Result;
import code.docflow.model.*;
import code.docflow.utils.FileUtil;
import code.docflow.utils.NamesUtil;
import code.docflow.utils.TestUtil;
import com.google.common.base.Strings;
import org.apache.commons.lang.StringUtils;
import play.Play;
import play.exceptions.UnexpectedException;
import play.mvc.Util;
import play.templates.Template;
import play.templates.TemplateLoader;
import play.vfs.VirtualFile;

import java.io.*;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class DocflowCodeGenerator {

    private static final String[] YES_TRUE_OR_1 = new String[]{"true", "1", "yes"};

    public static void generate(String appPath, String targetDb) throws IOException {

        loadDocflowConfiguration(appPath);

        HashMap<String, File> modelsAllowedToBeUpdated = listOldUpdateableModels();

        HashMap<String, File> enumsAllowedToBeUpdated = listOldUpdateableEnums();

        createNewModels(modelsAllowedToBeUpdated, targetDb);

        createDefaultQueries();

        createDefaultActions();

        createDefaultTasks();

        createModelsHistories(modelsAllowedToBeUpdated, targetDb);

        createEnums(enumsAllowedToBeUpdated);

        createDocflowMessagesJava();

        // removes updateable files that were not updated
        for (File file : modelsAllowedToBeUpdated.values())
            file.delete();

        for (File file : enumsAllowedToBeUpdated.values())
            file.delete();
    }

    private static void createDocflowMessagesJava() {

        for (Map.Entry<String, LinkedHashMap<String, Message>> entry : DocflowConfig.instance.messagesByFiles.entrySet()) {

            final String msgFilename = entry.getKey();

            final String baseName = msgFilename.substring(0, msgFilename.length() - DocflowConfig.MESSAGES_FILE_SUFFIX.length());

            final int sp = baseName.lastIndexOf(File.separatorChar);
            final String className = baseName.substring(sp + 1);

            final String javaFilename = baseName + ".java";

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            FileUtil.OldLF oldLF = FileUtil.setUnixLF();
            try {
                final PrintWriter out = new PrintWriter(os);

                final Template tmpl = TemplateLoader.load("ngCodeGen/DocflowGenerator/docflowMessages.txt");

                TreeMap<String, Object> args = new TreeMap<String, Object>();
                args.put("className", className);
                args.put("msgMap", entry.getValue());
                args.put("fileKey", className.toUpperCase() + ".");
                out.print(tmpl.render(args));

                out.flush();

                FileUtil.saveFileIfChanged(VirtualFile.open(javaFilename), os);
            } finally {
                oldLF.restore();
            }
        }
    }

    private static void createNewModels(HashMap<String, File> modelsAllowedToBeUpdated, String targetDb) {
        for (DocType docType : DocflowConfig.instance.documents.values()) {
            if (docType.udt)
                continue;
            for (int i = 0; i < docType.entities.size(); i++) {
                Entity entity = docType.entities.get(i);
                String filename = "models/" + entity.name + ".java";
                final VirtualFile dst = docType.sourcePath.child(filename);
                String dstPath = null;
                try {
                    dstPath = dst.getRealFile().getCanonicalPath();
                } catch (IOException e) {
                    throw new UnexpectedException(e);
                }
                if (!dst.exists() || modelsAllowedToBeUpdated.containsKey(dstPath)) {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    generateEntityCode(entity, targetDb, os);
                    FileUtil.saveFileIfChanged(dst, os);
                    modelsAllowedToBeUpdated.remove(dstPath);
                }
            }
        }
    }

    private static void createDefaultQueries() {
        for (DocType docModel : DocflowConfig.instance.documents.values()) {
            if (docModel.udt)
                continue;
            final VirtualFile folder = docModel.sourcePath.child("docflow/queries");
            folder.getRealFile().mkdirs();
            final VirtualFile dst = folder.child("Query" + docModel.name + ".java");
            if (!dst.exists()) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                generateQueryCode(docModel, os);
                FileUtil.saveFileIfChanged(dst, os);
            }
        }
    }

    private static void createDefaultActions() {
        for (DocType docModel : DocflowConfig.instance.documents.values()) {
            if (docModel.udt)
                continue;
            final VirtualFile folder = docModel.sourcePath.child("docflow/actions");
            folder.getRealFile().mkdirs();
            final VirtualFile dst = folder.child("Actions" + docModel.name + ".java");
            if (!dst.exists()) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                generateActionsCode(docModel, os);
                FileUtil.saveFileIfChanged(dst, os);
            }
        }
    }

    private static void createDefaultTasks() {
        for (DocType docModel : DocflowConfig.instance.documents.values()) {
            if (docModel.udt || !docModel.task)
                continue;
            final VirtualFile folder = docModel.sourcePath.child("docflow/tasks");
            folder.getRealFile().mkdirs();
            final VirtualFile dst = folder.child("Task" + docModel.name + ".java");
            if (!dst.exists()) {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    generateTasksCode(docModel, os);
                FileUtil.saveFileIfChanged(dst, os);
            }
        }
    }

    private static void createModelsHistories(HashMap<String, File> modelsAllowedToBeUpdated, String targetDb) {
        for (DocType docType : DocflowConfig.instance.documents.values()) {
            if (docType.report || docType.simple  || docType.light || docType.udt)
                continue;
            String filename = "models/" + docType.historyEntityName + ".java";
            final VirtualFile dst = docType.sourcePath.child(filename);
            String dstPath = null;
            try {
                dstPath = dst.getRealFile().getCanonicalPath();
            } catch (IOException e) {
                throw new UnexpectedException(e);
            }
            if (!dst.exists() || modelsAllowedToBeUpdated.containsKey(dstPath)) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                generateHistoryCode(docType, targetDb, os);
                FileUtil.saveFileIfChanged(dst, os);
                modelsAllowedToBeUpdated.remove(dstPath);
            }
        }
    }

    private static void createEnums(HashMap<String, File> enumsAllowedToBeUpdated) {
        for (Field fld : DocflowConfig.instance.fieldTypes.values())
            if (fld.type == BuiltInTypes.ENUM) {
                String filename = "docflow/enums/" + NamesUtil.turnFirstLetterInUpperCase(fld.name) + ".java";
                final VirtualFile dst = fld.sourcePath.child(filename);
                String dstPath = null;
                try {
                    dstPath = dst.getRealFile().getCanonicalPath();
                } catch (IOException e) {
                    throw new UnexpectedException(e);
                }
                if (!dst.exists() || enumsAllowedToBeUpdated.containsKey(dstPath)) {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                        generateEnumCode((FieldEnum) fld, os);
                    FileUtil.saveFileIfChanged(dst, os);
                    enumsAllowedToBeUpdated.remove(dstPath);
                }
            }
    }

    private static HashMap<String, File> listOldUpdateableModels() {
        HashMap<String, File> res = new HashMap<String, File>();
        for (VirtualFile vf : DocflowConfig.appPath != null ? DocflowConfig.appPath : Play.javaPath) {
            final VirtualFile models = vf.child("models");
            if (models.exists()) {
                final File[] files = models.getRealFile().listFiles();
                for (int i = 0; i < files.length; i++) {
                    File file = files[i];
                    if (file.getName().toLowerCase().endsWith(".java")) {
                        BufferedReader rdr = null;
                        try {
                            rdr = new BufferedReader(new InputStreamReader(new FileInputStream(file.getPath())));
                            for (int k = 0; k < 10; k++) {
                                final String s = rdr.readLine();
                                // delete only files that contains fingerpring
                                if (s != null && s.trim().equalsIgnoreCase(DocflowConfig.GENERATED_JAVA_FILE_FINGERPRINT)) {
                                    rdr.close();
                                    rdr = null;
                                    res.put(file.getCanonicalPath(), file);
                                    break;
                                }
                            }
                        } catch (IOException e) {
                            throw new UnexpectedException(String.format("Failed on file '%s'.", file.getAbsolutePath()), e);
                        } finally {
                            if (rdr != null)
                                FileUtil.closeQuietly(rdr);
                        }
                    }
                }
            }
        }
        return res;
    }

    private static HashMap<String, File> listOldUpdateableEnums() {
        HashMap<String, File> res = new HashMap<String, File>();
        for (VirtualFile vf : DocflowConfig.appPath != null ? DocflowConfig.appPath : Play.javaPath) {
            final VirtualFile models = vf.child("docflow/enums");
            if (models.exists()) {
                final File[] files = models.getRealFile().listFiles();
                for (int i = 0; i < files.length; i++) {
                    File file = files[i];
                    if (file.getName().toLowerCase().endsWith(".java")) {
                        BufferedReader rdr = null;
                        try {
                            rdr = new BufferedReader(new InputStreamReader(new FileInputStream(file.getPath())));
                            for (int k = 0; k < 10; k++) {
                                final String s = rdr.readLine();
                                // delete only files that contains fingerpring
                                if (s != null && s.trim().equalsIgnoreCase(DocflowConfig.GENERATED_JAVA_FILE_FINGERPRINT)) {
                                    rdr.close();
                                    rdr = null;
                                    res.put(file.getCanonicalPath(), file);
                                    break;
                                }
                            }
                        } catch (IOException e) {
                            throw new UnexpectedException(String.format("Failed on file '%s'.", file.getAbsolutePath()), e);
                        } finally {
                            if (rdr != null)
                                FileUtil.closeQuietly(rdr);
                        }
                    }
                }
            }
        }
        return res;
    }

    @Util
    public static boolean isCodegenExternal() {
        final String codegetExternalConfigValue = (String) Play.configuration.get("codegen.external");
        if (codegetExternalConfigValue == null)
            return false;
        return StringUtils.indexOfAny(codegetExternalConfigValue.toString().toLowerCase(), YES_TRUE_OR_1) != -1;
    }

    @Util
    public static Result loadDocflowConfiguration(String appPath) {

        final Result result = new Result();

        if (!isCodegenExternal())
            return result;

        TestUtil.resetBeforeReloadDocflowConfig();

        if (Strings.isNullOrEmpty(appPath)) {
            DocflowConfig.instance = null;
            return result;
        }

        final ArrayList<VirtualFile> path = new ArrayList<VirtualFile>();
        if (appPath != null) {
            path.add(VirtualFile.open(appPath + "/app"));
            path.add(VirtualFile.open(appPath + "/conf"));
            path.add(VirtualFile.open(appPath + "/test"));
            // Take info from DSCommon as well

            final VirtualFile DSCommonApp = VirtualFile.open(Play.applicationPath + "/app");
            if (!path.get(0).equals(DSCommonApp))
                path.add(DSCommonApp);
        }

        DocflowConfig.appPath = path;
        DocflowConfig.instance.load(result);
        result.toLogger();
        if (result.isError())
            throw new DocflowConfigException(result);
        return result;
    }

    @Util
    public static void generateEntityCode(Entity entity, String targetDb, OutputStream os) {

        FileUtil.OldLF oldLF = FileUtil.setUnixLF();
        final PrintWriter out = new PrintWriter(os);

        final Template tmpl = TemplateLoader.load("ngCodeGen/DocflowGenerator/modelClass.txt");

        TreeMap<String, Object> args = new TreeMap<String, Object>();
        args.put("docflow", DocflowConfig.instance);
        args.put("entity", entity);
        args.put("fingerprint", DocflowConfig.GENERATED_JAVA_FILE_FINGERPRINT);
        args.put("targetDb", targetDb);
        out.print(tmpl.render(args));

        out.flush();
        oldLF.restore();
    }

    @Util
    public static void generateQueryCode(DocType docType, OutputStream os) {

        FileUtil.OldLF oldLF = FileUtil.setUnixLF();
        final PrintWriter out = new PrintWriter(os);

        final Template tmpl = TemplateLoader.load("ngCodeGen/DocflowGenerator/queryClass.txt");

        String defaultCalculatedText = null;
        for (int i = 0; i < docType.allFields.size(); i++) {
            Field fld = docType.allFields.get(i);
            if (fld.type == BuiltInTypes.STRING && fld.implicitFieldType == null) {
                defaultCalculatedText = fld.fullname;
                break;
            }
        }
        if (defaultCalculatedText == null)
            defaultCalculatedText = "_fullId()";

        TreeMap<String, Object> args = new TreeMap<String, Object>();
        args.put("docflow", DocflowConfig.instance);
        args.put("docType", docType);
        args.put("defaultCalculatedText", defaultCalculatedText);
        args.put("fingerprint", DocflowConfig.GENERATED_JAVA_FILE_FINGERPRINT);
        out.print(tmpl.render(args));

        out.flush();
        oldLF.restore();
    }

    @Util
    public static void generateActionsCode(DocType docType, OutputStream os) {

        FileUtil.OldLF oldLF = FileUtil.setUnixLF();
        final PrintWriter out = new PrintWriter(os);

        final Template tmpl = TemplateLoader.load("ngCodeGen/DocflowGenerator/actionsClass.txt");

        TreeMap<String, Object> args = new TreeMap<String, Object>();
        args.put("docflow", DocflowConfig.instance);
        args.put("docType", docType);
        args.put("fingerprint", DocflowConfig.GENERATED_JAVA_FILE_FINGERPRINT);
        out.print(tmpl.render(args));

        out.flush();
        oldLF.restore();
    }

    @Util
    public static void generateTasksCode(DocType docType, OutputStream os) {

        FileUtil.OldLF oldLF = FileUtil.setUnixLF();
        final PrintWriter out = new PrintWriter(os);

        final Template tmpl = TemplateLoader.load("ngCodeGen/DocflowGenerator/tasksClass.txt");

        TreeMap<String, Object> args = new TreeMap<String, Object>();
        args.put("docflow", DocflowConfig.instance);
        args.put("docType", docType);
        args.put("fingerprint", DocflowConfig.GENERATED_JAVA_FILE_FINGERPRINT);
        out.print(tmpl.render(args));

        out.flush();
        oldLF.restore();
    }

    @Util
    public static void generateHistoryCode(DocType docType, String targetDb, OutputStream os) {

        FileUtil.OldLF oldLF = FileUtil.setUnixLF();
        final PrintWriter out = new PrintWriter(os);

        final Template tmpl = TemplateLoader.load("ngCodeGen/DocflowGenerator/modelHistoryClass.txt");

        TreeMap<String, Object> args = new TreeMap<String, Object>();
        args.put("docflow", DocflowConfig.instance);
        args.put("docType", docType);
        args.put("fingerprint", DocflowConfig.GENERATED_JAVA_FILE_FINGERPRINT);
        args.put("targetDb", targetDb);
        out.print(tmpl.render(args));

        out.flush();
        oldLF.restore();
    }

    @Util
    public static void generateEnumCode(FieldEnum fieldEnum, OutputStream os) {

        FileUtil.OldLF oldLF = FileUtil.setUnixLF();
        final PrintWriter out = new PrintWriter(os);

        final Template tmpl = TemplateLoader.load("ngCodeGen/DocflowGenerator/enumClass.txt");

        TreeMap<String, Object> args = new TreeMap<String, Object>();
        args.put("docflow", DocflowConfig.instance);
        args.put("type", fieldEnum);
        args.put("fingerprint", DocflowConfig.GENERATED_JAVA_FILE_FINGERPRINT);
        out.print(tmpl.render(args));

        out.flush();
        oldLF.restore();
    }
}
