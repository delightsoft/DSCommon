package controllers;

import code.DocflowConfigException;
import code.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.collections.Item;
import code.docflow.model.*;
import code.docflow.templateModel.*;
import code.tests.TestUtil;
import code.users.CurrentUser;
import code.utils.NamesUtil;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import play.Play;
import play.exceptions.JavaExecutionException;
import play.mvc.Controller;
import play.mvc.Util;
import play.templates.Template;
import play.templates.TemplateLoader;
import play.vfs.VirtualFile;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class DocflowGenerator extends Controller {

    public static void index(String appPath, String targetDb) {
        Result result = loadDocflowConfiguration(appPath);
        final DocflowConfig docflow = DocflowConfig.instance;
        final boolean codegenExternal = isCodegenExternal();
        render(docflow, appPath, targetDb, codegenExternal, result);
    }

    public static void setAppPath(String appPath, String targetDb) {
        index(appPath, targetDb);
    }

    public static void entity(String entityName, String appPath, String targetDb) {
        loadDocflowConfiguration(appPath);
        Entity entity = findEntity(entityName);
        request.format = "txt";
        generateEntityCode(entity, targetDb, response.out);
    }

    public static void generateModels(String appPath, String targetDb) throws IOException {

        loadDocflowConfiguration(appPath);

        removeOldModels();

        createNewModels(targetDb);

        createDefaultQueries();

        createDefaultActions();

        createModelsHistories(targetDb);

        createEnums();

        createDocflowMessagesJava();

        final VirtualFile messagesFile = VirtualFile.search(DocflowConfig.appPath, "messages");

        // TODO: This happens then path to app is totally wrong.  Make proper diagnostics
        checkNotNull(messagesFile);

        new DocflowGenerator.UpdateMessages(messagesFile).process();

        DocflowGenerator.index(appPath, targetDb);

        index(appPath, targetDb);
    }

    private static void createDocflowMessagesJava() {

        for (Map.Entry<String, LinkedHashMap<String, Message>> entry : DocflowConfig.instance.messagesByFiles.entrySet()) {

            final String msgFilename = entry.getKey();

            final String baseName = msgFilename.substring(0, msgFilename.length() - DocflowConfig.MESSAGES_FILE_SUFFIX.length());

            final int sp = baseName.lastIndexOf(File.separatorChar);
            final String className = baseName.substring(sp + 1);

            final String javaFilename = baseName + ".java";

            OutputStream os = VirtualFile.open(javaFilename).outputstream();
            try {
                final PrintWriter out = new PrintWriter(os);

                final Template tmpl = TemplateLoader.load("DocflowGenerator/DocflowMessages.txt");

                TreeMap<String, Object> args = new TreeMap<String, Object>();
                args.put("className", className);
                args.put("msgMap", entry.getValue());
                args.put("fileKey", className.toUpperCase() + ".");
                out.print(tmpl.render(args));

                out.flush();
            } finally {
                try {
                    os.close();
                } catch (IOException e) {
                    error(e);
                }
            }
        }
    }

    private static void createNewModels(String targetDb) {
        for (DocType doc : DocflowConfig.instance.documents.values()) {
            if (doc.udt)
                continue;
            for (int i = 0; i < doc.entities.size(); i++) {
                Entity entity = doc.entities.get(i);
                String filename = "models/" + entity.name + ".java";
                final VirtualFile dst = doc.sourcePath.child(filename);
                final OutputStream os = dst.outputstream();
                try {
                    generateEntityCode(entity, targetDb, os);
                } finally {
                    try {
                        os.close();
                    } catch (IOException e) {
                        error(e);
                    }
                }
            }
        }
    }

    private static void createDefaultQueries() {
        for (DocType docModel : DocflowConfig.instance.documents.values()) {
            if (docModel.udt)
                continue;
            for (int i = 0; i < docModel.entities.size(); i++) {
                Entity entity = docModel.entities.get(i);
                String filename = "docflow/queries/Query" + docModel.name + ".java";
                final VirtualFile dst = docModel.sourcePath.child(filename);
                if (dst.exists()) // skip, if such file already exists
                    continue;
                final OutputStream os = dst.outputstream();
                try {
                    generateQueryCode(docModel, os);
                } finally {
                    try {
                        os.close();
                    } catch (IOException e) {
                        error(e);
                    }
                }
            }
        }
    }

    private static void createDefaultActions() {
        for (DocType docModel : DocflowConfig.instance.documents.values()) {
            if (docModel.udt)
                continue;
            for (int i = 0; i < docModel.entities.size(); i++) {
                Entity entity = docModel.entities.get(i);
                String filename = "docflow/actions/Actions" + docModel.name + ".java";
                final VirtualFile dst = docModel.sourcePath.child(filename);
                if (dst.exists()) // skip, if such file already exists
                    continue;
                final OutputStream os = dst.outputstream();
                try {
                    generateActionsCode(docModel, os);
                } finally {
                    try {
                        os.close();
                    } catch (IOException e) {
                        error(e);
                    }
                }
            }
        }
    }

    private static void createModelsHistories(String targetDb) {
        for (DocType doc : DocflowConfig.instance.documents.values()) {
            if (doc.report || doc.simple || doc.udt)
                continue;
            String filename = "models/" + doc.historyEntityName + ".java";
            final VirtualFile dst = doc.sourcePath.child(filename);
            final OutputStream os = dst.outputstream();
            try {
                generateHistoryCode(doc, targetDb, os);
            } finally {
                try {
                    os.close();
                } catch (IOException e) {
                    error(e);
                }
            }
        }
    }

    private static void createEnums() {

        for (Field fld : DocflowConfig.instance.fieldTypes.values()) {
            if (fld.type != Field.Type.ENUM)
                continue;

            String filename = "docflow/enums/" + NamesUtil.turnFirstLetterInUpperCase(fld.name) + ".java";
            final VirtualFile dst = fld.sourcePath.child(filename);
            final OutputStream os = dst.outputstream();
            try {
                generateEnumCode((FieldEnum) fld, os);
            } finally {
                try {
                    os.close();
                } catch (IOException e) {
                    error(e);
                }
            }
        }
    }

    private static void removeOldModels() {
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
                                    file.delete();
                                    break;
                                }
                            }
                        } catch (IOException e) {
                            throw new JavaExecutionException(String.format("Failed on file '%s'.", file.getAbsolutePath()), e);
                        } finally {
                            if (rdr != null)
                                try {
                                    rdr.close();
                                } catch (IOException e) {
                                }
                        }
                    }
                }
            }
        }
    }

    private static Entity findEntity(String entityName) {
        final DocflowConfig docflow = DocflowConfig.instance;
        Entity entity = null;
        for (DocType doc : docflow.documents.values()) {
            for (int i = 0; i < doc.entities.size(); i++) {
                Entity e = doc.entities.get(i);
                if (e.name.equals(entityName)) {
                    entity = e;
                    break;
                }
            }
        }
        if (entity == null)
            notFound(String.format("Entity %s", entityName));
        return entity;
    }

    @Util
    public static boolean isCodegenExternal() {
        final String codegetExternalConfigValue = (String) Play.configuration.get("codegen.external");
        if (codegetExternalConfigValue == null)
            return false;
        return StringUtils.indexOfAny(
                codegetExternalConfigValue.toString().toLowerCase(),
                new String[]{"true", "1", "yes"}) != -1;
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

            final VirtualFile DSCommon = VirtualFile.open(Play.applicationPath + "/app");
            if (!path.get(0).equals(DSCommon))
                path.add(DSCommon);
        }

        DocflowConfig.appPath = path;
        DocflowConfig.instance.prepare(result);
        result.toLogger();
        if (result.isError())
            throw new DocflowConfigException(result);
        return result;
    }

    /**
     * A' la tab.
     */
    private static final String T = "    ";

    @Util
    public static void generateEntityCode(Entity entity, String targetDb, OutputStream os) {

        final PrintWriter out = new PrintWriter(os);

        final Template tmpl = TemplateLoader.load("DocflowGenerator/modelClass.txt");

        TreeMap<String, Object> args = new TreeMap<String, Object>();
        args.put("docflow", DocflowConfig.instance);
        args.put("entity", entity);
        args.put("fingerprint", DocflowConfig.GENERATED_JAVA_FILE_FINGERPRINT);
        args.put("targetdb", targetDb);
        out.print(tmpl.render(args));

        out.flush();
    }

    @Util
    public static void generateQueryCode(DocType doc, OutputStream os) {

        final PrintWriter out = new PrintWriter(os);

        final Template tmpl = TemplateLoader.load("DocflowGenerator/queryClass.txt");

        String defaultCalculatedText = null;
        for (int i = 0; i < doc.allFields.size(); i++) {
            Field fld = doc.allFields.get(i);
            if (fld.type == Field.Type.STRING && fld.implicitFieldType == null) {
                defaultCalculatedText = fld.fullname;
                break;
            }
        }
        if (defaultCalculatedText == null)
            defaultCalculatedText = "_fullId()";

        TreeMap<String, Object> args = new TreeMap<String, Object>();
        args.put("docflow", DocflowConfig.instance);
        args.put("document", doc);
        args.put("defaultCalculatedText", defaultCalculatedText);
        args.put("fingerprint", DocflowConfig.GENERATED_JAVA_FILE_FINGERPRINT);
        out.print(tmpl.render(args));

        out.flush();
    }

    @Util
    public static void generateActionsCode(DocType doc, OutputStream os) {

        final PrintWriter out = new PrintWriter(os);

        final Template tmpl = TemplateLoader.load("DocflowGenerator/actionsClass.txt");

        TreeMap<String, Object> args = new TreeMap<String, Object>();
        args.put("docflow", DocflowConfig.instance);
        args.put("document", doc);
        args.put("fingerprint", DocflowConfig.GENERATED_JAVA_FILE_FINGERPRINT);
        out.print(tmpl.render(args));

        out.flush();
    }

    @Util
    public static void generateHistoryCode(DocType doc, String targetDb, OutputStream os) {

        final PrintWriter out = new PrintWriter(os);

        final Template tmpl = TemplateLoader.load("DocflowGenerator/modelHistoryClass.txt");

        TreeMap<String, Object> args = new TreeMap<String, Object>();
        args.put("docflow", DocflowConfig.instance);
        args.put("doc", doc);
        args.put("fingerprint", DocflowConfig.GENERATED_JAVA_FILE_FINGERPRINT);
        args.put("targetdb", targetDb);
        out.print(tmpl.render(args));

        out.flush();
    }

    @Util
    public static void generateEnumCode(FieldEnum fieldEnum, OutputStream os) {

        final PrintWriter out = new PrintWriter(os);

        final Template tmpl = TemplateLoader.load("DocflowGenerator/enumClass.txt");

        TreeMap<String, Object> args = new TreeMap<String, Object>();
        args.put("docflow", DocflowConfig.instance);
        args.put("type", fieldEnum);
        args.put("fingerprint", DocflowConfig.GENERATED_JAVA_FILE_FINGERPRINT);
        out.print(tmpl.render(args));

        out.flush();
    }

    public static class UpdateMessages {

        // 'messages' file
        private VirtualFile messagesFile;
        // initial sequence. contains only first elements of localization for whole Document, Named enum etc.
        private LinkedList<Element> seq;

        // source of localization information - it's exactly the same model to be used in templates generation.  it has
        // all elements, since generated with System rights (no limits)
        private TmplRoot root;


        private Element fieldsL18nRoot;
        private HashMap<String, Element> fieldsL18nElements;

        private Element actionsL18nRoot;
        private HashMap<String, Element> actionsL18nElements;

        private Element actionsParamsL18nRoot;
        private HashMap<String, Element> actionsParamsL18nElements;

        private Element filtersL18nRoot;
        private HashMap<String, Element> filterL18nElements;

        private Element sortOrdersL18nRoot;
        private HashMap<String, Element> sortOrderL18nElements;

        private Element statesL18nRoot;
        private HashMap<String, Element> statesL18nElements;

        private Element typesL18nRoot;
        private HashMap<String, Element> typesL18nElements;

        private HashMap<String, Element> enumsL18nRoots;
        private ListMultimap<String, Element> enumsL18nElements;

        private HashMap<String, Element> docsL18nRoots;
        private ListMultimap<String, Element> docsL18nElements;

        private HashMap<String, Element> structuresL18nRoots;
        private ListMultimap<String, Element> structuresL18nElements;

        public UpdateMessages(VirtualFile messagesFile) {
            this.messagesFile = messagesFile;
        }

        public static class Element {
            public String key;
            public ArrayList<String> lines = new ArrayList<String>(1);
            public ArrayList<Element> followers = new ArrayList<Element>(0);

            public Element() {
            }

            /**
             * Copies from given Element all lines that are comments, just leaving only the last line with key-value.
             */
            public Element(Element rootElement) {
                if (rootElement.lines.size() == 1) return;
                lines = rootElement.lines;
                rootElement.lines = new ArrayList<String>();
                rootElement.lines.add(lines.get(lines.size() - 1));
                lines.remove(lines.size() - 1);
            }

            /**
             * Contructs root element with given comment on top of it.
             */
            public Element(String title) {
                lines.add("");
                lines.add(title);
            }
        }

        public void process() throws IOException {

            this.root = CurrentUser.systemUserScope(new Callable<TmplRoot>() {
                @Override
                public TmplRoot call() throws Exception {
                    return TmplRoot.factoryWithUdtDocument.get(CurrentUser.getInstance().getUserRoles());
                }
            });

            seq = new LinkedList<Element>();

            fieldsL18nElements = new HashMap<String, Element>();
            actionsL18nElements = new HashMap<String, Element>();
            actionsParamsL18nElements = new HashMap<String, Element>();

            docsL18nRoots = new HashMap<String, Element>();
            enumsL18nRoots = new HashMap<String, Element>();
            structuresL18nRoots = new HashMap<String, Element>();

            docsL18nElements = LinkedListMultimap.create();
            enumsL18nElements = LinkedListMultimap.create();
            structuresL18nElements = LinkedListMultimap.create();

            filterL18nElements = new HashMap<String, Element>();
            sortOrderL18nElements = new HashMap<String, Element>();
            statesL18nElements = new HashMap<String, Element>();
            typesL18nElements = new HashMap<String, Element>();

            loadExistingL18n();

            updateActions();

            updateActionsParams();

            updateFields();

            updateTypes();

            updateFilters();

            updateSortOrders();

            updateStates();

            updateDocumentsL18n();

            updateStructures();

            updateEnums();

            saveL18n();
        }

        private void loadExistingL18n() throws IOException {

            // filter that excludes elements what already not in the docflow model. such information will be simply removed
            final String[] enumsPrefixes = collectEnumsPrefixes(root);
            final String[] structuresPrefixes = collectStructuresPrefixes(root);
            final String[] docsPrefixes = collectDocumentsPrefixes(root);

            InputStream in = messagesFile.inputstream();

            BufferedReader is = new BufferedReader(new InputStreamReader(in, "UTF-8"));

            // read whole messages file, with soring out document titles and other documents localizations
            Element element;
            while ((element = nextElement(is)) != null) {

                if (element.key.startsWith(TmplAction.ACTION_ROOT)) {
                    if (actionsL18nRoot == null)
                        seq.add(actionsL18nRoot = new Element(element));
                    actionsL18nElements.put(element.key.toUpperCase(), element);
                    continue;
                }

                if (element.key.startsWith(TmplAction.ACTION_PARAM_ROOT)) {
                    if (actionsParamsL18nRoot == null)
                        seq.add(actionsParamsL18nRoot = new Element(element));
                    actionsParamsL18nElements.put(element.key.toUpperCase(), element);
                    continue;
                }

                if (element.key.startsWith(TmplField.FIELD_ROOT)) {
                    if (fieldsL18nRoot == null)
                        seq.add(fieldsL18nRoot = new Element(element));
                    fieldsL18nElements.put(element.key.toUpperCase(), element);
                    continue;
                }

                if (element.key.startsWith(TmplField.TYPE_ROOT)) {
                    if (typesL18nRoot == null)
                        seq.add(typesL18nRoot = new Element(element));
                    typesL18nElements.put(element.key.toUpperCase(), element);
                    continue;
                }

                if (element.key.startsWith(TmplFilter.FILTER_ROOT)) {
                    if (filtersL18nRoot == null)
                        seq.add(filtersL18nRoot = new Element(element));
                    filterL18nElements.put(element.key.toUpperCase(), element);
                    continue;
                }

                if (element.key.startsWith(TmplSortOrder.SORTORDER_ROOT)) {
                    if (sortOrdersL18nRoot == null)
                        seq.add(sortOrdersL18nRoot = new Element(element));
                    sortOrderL18nElements.put(element.key.toUpperCase(), element);
                    continue;
                }

                if (element.key.startsWith(TmplState.STATE_ROOT)) {
                    if (statesL18nRoot == null)
                        seq.add(statesL18nRoot = new Element(element));
                    statesL18nElements.put(element.key.toUpperCase(), element);
                    continue;
                }

                String key = element.key.toUpperCase();

                if (element.key.startsWith(TmplDocument.DOCUMENT_ROOT)) {
                    for (int i = 0; i < docsPrefixes.length; i++) {
                        String docsPrefix = docsPrefixes[i];
                        if (key.equals(docsPrefix)) {
                            seq.add(element);
                            docsL18nRoots.put(docsPrefixes[i], element);
                            break;
                        } else if (key.startsWith(docsPrefix + ".")) {
                            docsL18nElements.put(docsPrefix, element);
                            break;
                        }
                    }
                    continue;
                }
                if (element.key.startsWith(TmplField.ENUM_ROOT)) {
                    for (int i = 0; i < enumsPrefixes.length; i++) {
                        String enumPrefix = enumsPrefixes[i];
                        if (key.equals(enumPrefix)) {
                            seq.add(element);
                            enumsL18nRoots.put(enumPrefix, element);
                            break;
                        } else if (key.startsWith(enumPrefix + ".")) {
                            enumsL18nElements.put(enumPrefix, element);
                            break;
                        }
                    }
                    continue;
                }

                if (element.key.startsWith(TmplField.STRUCT_ROOT) || element.key.startsWith(TmplField.SUBTABLE_ROOT)) {
                    for (int i = 0; i < structuresPrefixes.length; i++) {
                        String structurePrefix = structuresPrefixes[i];
                        if (key.equals(structurePrefix)) {
                            seq.add(element);
                            structuresL18nRoots.put(structurePrefix, element);
                            break;
                        } else if (key.startsWith(structurePrefix + ".")) {
                            structuresL18nElements.put(structurePrefix, element);
                            break;
                        }
                    }
                    continue;
                }

                seq.add(element); // general element
            }

            IOUtils.closeQuietly(in);
        }

        private void updateActions() {

            final Set<String> keys = new TreeSet<String>();

            for (TmplDocument tmplDocument : root.getDocuments())
                for (Action action : DocflowConfig.instance.documents.get(tmplDocument.getName().toUpperCase()).actionsArray)
                    if (tmplDocument.getActionTitle(action.name) != null)
                        keys.add(lastTwoPartsOfTheKey(tmplDocument.getActionTitle(action.name)));

            for (String action : keys) {
                if (actionsL18nRoot == null)
                    seq.add(actionsL18nRoot = new Element("# General actions L18n"));
                copyOrAddKey(actionsL18nRoot, action, lastPartOfTheKey(action), actionsL18nElements);
            }
        }

        private void updateActionsParams() {

            final Set<String> keys = new TreeSet<String>();

            for (TmplDocument tmplDocument : root.getDocuments()) {
                final TmplTemplate templateform = tmplDocument.getTemplateByName(DocflowConfig.BuiltInTemplates.FORM.getUpperCase());
                for (Action action : DocflowConfig.instance.documents.get(tmplDocument.getName().toUpperCase()).actionsArray) {
                    final TmplAction actionTmpl = templateform.getActionByName(action.name);
                    if (actionTmpl != null && actionTmpl.getParams() != null)
                        for (TmplField param : actionTmpl.getParams())
                            keys.add(lastTwoPartsOfTheKey(param.getTitle()));
                }
            }

            for (String action : keys) {
                if (actionsParamsL18nRoot == null)
                    seq.add(actionsParamsL18nRoot = new Element("# General actions parameters L18n"));
                copyOrAddKey(actionsParamsL18nRoot, action, lastPartOfTheKey(action), actionsParamsL18nElements);
            }
        }

        private void updateFields() {

            TmplDocument udt = root.getDocumentByName(DocflowConfig.UDT_DOCUMENT);

            final Set<String> keys = new TreeSet<String>();

            for (TmplDocument tmplDocument : root.getDocuments())
                if (tmplDocument != udt)
                    for (Field fld : DocflowConfig.instance.documents.get(tmplDocument.getName().toUpperCase()).allFields)
                        if (fld.udtType == null && tmplDocument.getFieldTitle(fld.fullname) != null)
                            keys.add(lastTwoPartsOfTheKey(tmplDocument.getFieldTitle(fld.fullname)));

            for (String fld : keys) {
                if (fieldsL18nRoot == null)
                    seq.add(fieldsL18nRoot = new Element("# General fields L18n"));
                copyOrAddKey(fieldsL18nRoot, fld, lastPartOfTheKey(fld), fieldsL18nElements);
            }
        }

        private void updateFilters() {

            final Set<String> commonFilters = new TreeSet<String>();

            for (TmplDocument tmplDoc : root.getDocuments())
                for (TmplFilter tmplFilter : tmplDoc.getFilters())
                    commonFilters.add(lastTwoPartsOfTheKey(tmplFilter.getTitle()));

            final HashMap<String, Element> sortFiltersByKey = new HashMap<String, Element>();
            for (Element el : filterL18nElements.values())
                sortFiltersByKey.put(el.key.toUpperCase(), el);

            for (String filter : commonFilters) {
                if (filtersL18nRoot == null)
                    seq.add(filtersL18nRoot = new Element("# Documents filtering rules"));
                copyOrAddKey(filtersL18nRoot, filter, lastPartOfTheKey(filter), sortFiltersByKey);
            }
        }

        private void updateSortOrders() {

            final Set<String> commonOrders = new TreeSet<String>();

            for (TmplDocument tmplDoc : root.getDocuments())
                for (TmplSortOrder tmplSortOrder : tmplDoc.getSortOrders())
                    commonOrders.add(lastTwoPartsOfTheKey(tmplSortOrder.getTitle()));

            final HashMap<String, Element> sortOrdersByKey = new HashMap<String, Element>();
            for (Element el : sortOrderL18nElements.values())
                sortOrdersByKey.put(el.key.toUpperCase(), el);

            for (String sortOrder : commonOrders) {
                if (sortOrdersL18nRoot == null)
                    seq.add(sortOrdersL18nRoot = new Element("# Documents sort orders"));
                copyOrAddKey(sortOrdersL18nRoot, sortOrder, lastPartOfTheKey(sortOrder), sortOrdersByKey);
            }
        }

        private void updateStates() {

            final Set<String> commonStates = new TreeSet<String>();

            for (TmplDocument tmplDoc : root.getDocuments())
                for (TmplState tmplState : tmplDoc.getStates())
                    commonStates.add(lastTwoPartsOfTheKey(tmplState.getTitle()));

            final HashMap<String, Element> statesByKey = new HashMap<String, Element>();
            for (Element el : statesL18nElements.values())
                statesByKey.put(el.key.toUpperCase(), el);

            for (String sortState : commonStates) {
                if (statesL18nRoot == null)
                    seq.add(statesL18nRoot = new Element("# Documents states"));
                copyOrAddKey(statesL18nRoot, sortState, lastPartOfTheKey(sortState), statesByKey);
            }
        }

        private void updateTypes() {

            final TmplDocument udt = root.getDocumentByName(DocflowConfig.UDT_DOCUMENT);
            final TmplTemplate udtFormTmpl = udt.getTemplateByName(DocflowConfig.BuiltInTemplates.FORM.getUpperCase());

            final HashMap<String, Element> typesByKey = new HashMap<String, Element>();
            for (Element el : typesL18nElements.values())
                typesByKey.put(el.key.toUpperCase(), el);

            final TreeSet<String> sortedTypes = new TreeSet<String>();
            for (TmplField type : udtFormTmpl.getFields()) {
                if (type.getFields() != null || type.getEnumValues() != null) continue;
                sortedTypes.add(type.getTitle());
            }

            for (String type : sortedTypes) {
                if (typesL18nRoot == null)
                    seq.add(typesL18nRoot = new Element("# Documents types"));
                copyOrAddKey(typesL18nRoot, type, lastPartOfTheKey(type), typesByKey);
            }
        }

        private void updateDocumentsL18n() {

            final ImmutableList<TmplDocument> tmplDocs = root.getDocuments();

            TmplDocument udt = root.getDocumentByName(DocflowConfig.UDT_DOCUMENT);
            final TmplTemplate udtFormTmpl = udt.getTemplateByName(DocflowConfig.BuiltInTemplates.FORM.getUpperCase());

            for (TmplDocument tmplDocument : tmplDocs) {

                if (tmplDocument == udt) continue;

                DocType docModel = DocflowConfig.instance.documents.get(tmplDocument.getName().toUpperCase());

                // get or create at the end document title key
                Element d = docsL18nRoots.get(tmplDocument.getTitle().toUpperCase());
                if (d == null) { // create new document element. since it was not found
                    d = new Element();
                    d.key = tmplDocument.getTitle();
                    d.lines.add("");
                    d.lines.add(String.format("# Document: %s", tmplDocument.getName()));
                    d.lines.add(tmplDocument.getTitle() + "=" + tmplDocument.getName());
                    seq.add(d);
                    docsL18nRoots.put(tmplDocument.getTitle().toUpperCase(), d);
                }

                // list to map
                final HashMap<String, Element> docElementByKey = new HashMap<String, Element>();
                for (Element el : docsL18nElements.get(tmplDocument.getTitle().toUpperCase()))
                    docElementByKey.put(el.key.toUpperCase(), el);

                // fields

                // Rule: Implicite field 'text' should be localized as well
                final String textKey = tmplDocument.getFieldTitle(DocflowConfig.ImplicitFields.TEXT.getUpperCase());
                if (textKey != null)
                    copyOrAddKey(d, textKey, lastTwoPartsOfTheKey(textKey), docElementByKey);

                localizeFields(d, docElementByKey, docModel.fields, udtFormTmpl, tmplDocument);

                // states
                final ImmutableList<TmplState> states = tmplDocument.getStates();
                for (int j = 0; j < states.size(); j++) {
                    final String key = states.get(j).getTitle();
                    copyOrAddKey(d, key, lastTwoPartsOfTheKey(key), docElementByKey);
                }

                // actions
                final TmplTemplate templateForm = tmplDocument.getTemplateByName(DocflowConfig.BuiltInTemplates.FORM.getUpperCase());
                for (Action action : docModel.actionsArray) {
                    final String key = tmplDocument.getActionTitle(action.name);
                    if (key == null) continue;
                    final String defaultTitle = lastTwoPartsOfTheKey(key);
                    copyOrAddKey(d, key, defaultTitle, docElementByKey);
                    final TmplAction actionTmpl = templateForm.getActionByName(action.name);
                    if (actionTmpl.getParams() != null)
                        for (TmplField param : actionTmpl.getParams()) {
                            final String title = param.getTitle();
                            final String defaultParamTitle = lastTwoPartsOfTheKey(title);
                            copyOrAddKey(d, title, defaultParamTitle, docElementByKey);
                        }
                }

                // templates
                final ImmutableList<TmplTemplate> tmplTemplates = tmplDocument.getTemplates();
                for (int j = 0; j < tmplTemplates.size(); j++) {
                    TmplTemplate tmplTemplate = tmplTemplates.get(j);
                    if (tmplTemplate.getScreen()) {
                        final String key = tmplTemplate.getTitle();
                        copyOrAddKey(d, key, tmplDocument.getName(), docElementByKey);
                        if (tmplTemplate.getTabs() != null)
                            for (TmplTab tab : tmplTemplate.getTabs())
                                copyOrAddKey(d, tab.getTitle(), tab.getTemplate().getTitle(), docElementByKey);
                    }
                }

                // filters
                for (TmplFilter tmplFilter : tmplDocument.getFilters())
                    copyOrAddKey(d, tmplFilter.getTitle(), lastTwoPartsOfTheKey(tmplFilter.getTitle()), docElementByKey);

                // sort orders
                for (TmplSortOrder tmplSortOrder : tmplDocument.getSortOrders())
                    copyOrAddKey(d, tmplSortOrder.getTitle(), lastTwoPartsOfTheKey(tmplSortOrder.getTitle()), docElementByKey);
            }
        }

        private void updateEnums() {

            TmplDocument udt = root.getDocumentByName(DocflowConfig.UDT_DOCUMENT);

            for (TmplField enumType : udt.getTemplateByName(DocflowConfig.BuiltInTemplates.FORM.getUpperCase()).getFields()) {
                if (enumType.getEnumValues() == null)
                    continue;

                // get or create at the end document title key
                Element d = enumsL18nRoots.get(enumType.getTitle().toUpperCase());
                if (d == null) { // create new document element. since it was not found
                    d = new Element();
                    d.key = enumType.getTitle();
                    d.lines.add("");
                    d.lines.add(String.format("# Enum: %s", enumType.getName()));
                    d.lines.add(enumType.getTitle() + "=" + enumType.getName());
                    seq.add(d);
                    enumsL18nRoots.put(enumType.getTitle().toUpperCase(), d);
                }

                // turn list of enums localization elements to map
                final List<Element> enumElements = enumsL18nElements.get(enumType.getTitle().toUpperCase());
                final HashMap<String, Element> enumElementByKey = new HashMap<String, Element>();
                for (int j = 0; j < enumElements.size(); j++) {
                    Element el = enumElements.get(j);
                    enumElementByKey.put(el.key.toUpperCase(), el);
                }

                // add enum values
                for (TmplEnumValue enumValue : enumType.getEnumValues())
                    copyOrAddKey(d, enumValue.getTitle(), enumValue.getTitle(), enumElementByKey);
            }
        }

        private void updateStructures() {
            final TmplDocument udt = root.getDocumentByName(DocflowConfig.UDT_DOCUMENT);
            final TmplTemplate udtFormTmpl = udt.getTemplateByName(DocflowConfig.BuiltInTemplates.FORM.getUpperCase());

            for (TmplField structureType : udt.getTemplateByName(DocflowConfig.BuiltInTemplates.FORM.getUpperCase()).getFields()) {
                if (structureType.getFields() == null) continue;
                // get or create at the end document title key
                Element d = structuresL18nRoots.get(structureType.getTitle().toUpperCase());
                if (d == null) { // create new document element. since it was not found
                    d = new Element();
                    d.key = structureType.getTitle();
                    d.lines.add("");
                    d.lines.add(String.format("# Structure: %s", structureType.getName()));
                    d.lines.add(structureType.getTitle() + "=" + structureType.getName());
                    seq.add(d);
                    structuresL18nRoots.put(structureType.getTitle().toUpperCase(), d);
                }
                // turn list of enums localization elements to map
                final List<Element> structureElements = structuresL18nElements.get(structureType.getTitle().toUpperCase());
                final HashMap<String, Element> structureElementByKey = new HashMap<String, Element>();
                for (int j = 0; j < structureElements.size(); j++) {
                    Element el = structureElements.get(j);
                    structureElementByKey.put(el.key.toUpperCase(), el);
                }
                localizeFieldsForStructure(d, structureElementByKey, structureType.getFields(), udtFormTmpl, udt);
            }
        }

        private void saveL18n() throws UnsupportedEncodingException {
            final OutputStreamWriter os = new OutputStreamWriter(messagesFile.outputstream(), "UTF-8");
            final PrintWriter pw = new PrintWriter(os);
            for (Element topElement : seq) {
                for (String line : topElement.lines)
                    pw.println(line);
                for (Element subElement : topElement.followers)
                    for (String line : subElement.lines)
                        pw.println(line);
            }
            IOUtils.closeQuietly(pw);
        }

        private void localizeFields(Element d, HashMap<String, Element> docElementByKey,
                                    LinkedHashMap<String, Field> fields,
                                    TmplTemplate udtFormTmpl, TmplDocument tmplDocument) {
            // NOTE: Attn this method MUST implement just the same logic as localizeFieldsForStructure() below
            for (Field fld : fields.values()) {
                final String key = tmplDocument.getFieldTitle(fld.fullname);
                if (key == null) continue;
                if (fld.udtType == null) {
                    copyOrAddKey(d, key, lastTwoPartsOfTheKey(key), docElementByKey);
                    if (fld.type == Field.Type.ENUM) {
                        FieldEnum fieldEnum = (FieldEnum) fld;
                        for (Item anEnum : fieldEnum.strValues.values())
                            copyOrAddKey(d, key + TmplField.ENUM_LINK + anEnum.name, anEnum.name, docElementByKey);
                    }
                    else if (fld.type == Field.Type.STRUCTURE || fld.type == Field.Type.SUBTABLE)
                        localizeFields(d, docElementByKey, ((FieldStructure) fld).fields, udtFormTmpl, tmplDocument);
                } else {
                    final TmplField udtType = udtFormTmpl.getFieldByName(fld.udtType);
                    copyOrAddKey(d, key, udtType.getTitle(), docElementByKey);
                }
            }
        }

        private void localizeFieldsForStructure(Element d, HashMap<String, Element> docElementByKey,
                                                ImmutableList<TmplField> fields,
                                                TmplTemplate udtFormTmpl, TmplDocument tmplDocument) {
            // NOTE: Attn this method MUST implement just the same logic as localizeFields() above
            for (TmplField fld : fields)
                if (fld.getTitle() != null) {
                    copyOrAddKey(d, fld.getTitle(), lastTwoPartsOfTheKey(fld.getTitle()), docElementByKey);
                    if (fld.getUdtType()) {
                        if (fld.getEnumValues() != null)
                            for (TmplEnumValue anEnum : fld.getEnumValues())
                                copyOrAddKey(d, anEnum.getTitle(), anEnum.getName(), docElementByKey);
                        if (fld.getFields() != null)
                            localizeFieldsForStructure(d, docElementByKey, fld.getFields(), udtFormTmpl, tmplDocument);
                    }
                }
        }

        private static String lastTwoPartsOfTheKey(String fldKey) {
            int p = fldKey.lastIndexOf('.');
            if (p > 0) p = fldKey.lastIndexOf('.', p - 1);
            return p > 0 ? fldKey.substring(p + 1) : fldKey;
        }

        private static String lastPartOfTheKey(String fldKey) {
            int p = fldKey.lastIndexOf('.');
            return p > 0 ? fldKey.substring(p + 1) : fldKey;
        }

        private void copyOrAddKey(Element element, String key, String value, HashMap<String, Element> elementByKey) {
            if (key == null)
                return;
            Element existingElement = elementByKey.get(key.toUpperCase());
            if (existingElement == null) {
                existingElement = new Element();
                existingElement.key = key;
                existingElement.lines.add(key + "=" + value);
            }
            element.followers.add(existingElement);
        }

        private String[] collectEnumsPrefixes(TmplRoot root) {
            TmplDocument udt = root.getDocumentByName(DocflowConfig.UDT_DOCUMENT);
            final ArrayList<String> prefixes = new ArrayList<String>();
            for (TmplField enumType : udt.getTemplateByName(DocflowConfig.BuiltInTemplates.FORM.getUpperCase()).getFields()) {
                if (enumType.getEnumValues() == null)
                    continue;
                prefixes.add(enumType.getTitle().toUpperCase());
            }
            return prefixes.toArray(new String[0]);
        }

        private String[] collectStructuresPrefixes(TmplRoot root) {
            TmplDocument udt = root.getDocumentByName(DocflowConfig.UDT_DOCUMENT);
            final ArrayList<String> prefixes = new ArrayList<String>();
            for (TmplField structureType : udt.getTemplateByName(DocflowConfig.BuiltInTemplates.FORM.getUpperCase()).getFields()) {
                if (structureType.getFields() == null) continue;
                prefixes.add(structureType.getTitle().toUpperCase());
            }
            return prefixes.toArray(new String[0]);
        }

        private String[] collectDocumentsPrefixes(TmplRoot root) {
            final ArrayList<String> prefixes = new ArrayList<String>();
            for (TmplDocument tmplDocument : root.getDocuments())
                prefixes.add(tmplDocument.getTitle().toUpperCase());
            return prefixes.toArray(new String[0]);
        }

        /**
         * @return 0 - comment string; n - index of key delimiter; -1 - empty string
         */
        public int isCommentOrEmpty(String line) {
            checkNotNull(line);
            int i = 0;
            for (; i < line.length(); i++)
                if (line.charAt(i) > (char) 32)
                    if (line.charAt(i) == '#')
                        return 0;
                    else
                        break;
            for (; i < line.length(); i++)
                if (line.charAt(i) == '=')
                    return i;
            return line.length();
        }

        public Element nextElement(BufferedReader reader) throws IOException {
            Element element = new Element();
            for (; ; ) {
                String s = reader.readLine();
                if (s == null)
                    if (element.lines.size() == 0) // after last
                        return null;
                    else { // last element
                        element.key = ""; // file ends without key-value
                        return element;
                    }
                int r = isCommentOrEmpty(s);
                element.lines.add(s);
                if (r < 1) // comment or empty
                    continue;
                // key = value
                element.key = StringUtils.strip(s.substring(0, r));
                return element;
            }
        }
    }
}
