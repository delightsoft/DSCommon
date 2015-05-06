package code.docflow.codegen;
//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.DocflowConfig;
import code.docflow.templateModel.*;
import code.docflow.users.CurrentUser;
import code.docflow.utils.FileUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import org.apache.commons.lang.StringUtils;
import play.exceptions.UnexpectedException;
import play.vfs.VirtualFile;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;

import static com.google.common.base.Preconditions.checkNotNull;

public class DocflowLocalizationGenerator {

    // 'messages' file
    private VirtualFile messagesFile;
    // initial sequence. contains only first elements of localization for whole Document, Named enum etc.
    private LinkedList<Element> seq;

    // source of localization information - it's exactly the same model to be used in templates generation.  it has
    // all elements, since generated with System rights (no limits)
    private TmplModel model;


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

    public static void generate() {
        final DocflowLocalizationGenerator docflowLocalizationGenerator = new DocflowLocalizationGenerator();
        docflowLocalizationGenerator.messagesFile = VirtualFile.search(DocflowConfig.appPath, "messages");
        // TODO: This happens then path to app is totally wrong.  Make proper diagnostics
        checkNotNull(docflowLocalizationGenerator.messagesFile);
        try {
            docflowLocalizationGenerator.process();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
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
         * Contructs model element with given comment on top of it.
         */
        public Element(String title) {
            lines.add("");
            lines.add(title);
        }
    }

    private void process() throws IOException {

        this.model = CurrentUser.systemUserScope(new Callable<TmplModel>() {
            @Override
            public TmplModel call() throws Exception {
                return TmplModel.factoryWithUdtDocument.get(CurrentUser.getInstance().getUserRoles());
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
        final String[] enumsPrefixes = collectEnumsPrefixes(model);
        final String[] structuresPrefixes = collectStructuresPrefixes(model);
        final String[] docsPrefixes = collectDocumentsPrefixes(model);

        InputStream in = messagesFile.inputstream();

        BufferedReader is = new BufferedReader(new InputStreamReader(in, "UTF-8"));

        // read whole messages file, with soring out document titles and other documents localizations
        Element element;
        while ((element = nextElement(is)) != null) {

            if (element.key.startsWith(TmplAction.ACTION_ROOT)) {
                if (actionsL18nRoot == null)
                    seq.add(actionsL18nRoot = new Element(element));
                actionsL18nElements.put(element.key, element);
                continue;
            }

            if (element.key.startsWith(TmplAction.ACTION_PARAM_ROOT)) {
                if (actionsParamsL18nRoot == null)
                    seq.add(actionsParamsL18nRoot = new Element(element));
                actionsParamsL18nElements.put(element.key, element);
                continue;
            }

            if (element.key.startsWith(TmplField.FIELD_ROOT)) {
                if (fieldsL18nRoot == null)
                    seq.add(fieldsL18nRoot = new Element(element));
                fieldsL18nElements.put(element.key, element);
                continue;
            }

            if (element.key.startsWith(TmplField.TYPE_ROOT)) {
                if (typesL18nRoot == null)
                    seq.add(typesL18nRoot = new Element(element));
                typesL18nElements.put(element.key, element);
                continue;
            }

            if (element.key.startsWith(TmplFilter.FILTER_ROOT)) {
                if (filtersL18nRoot == null)
                    seq.add(filtersL18nRoot = new Element(element));
                filterL18nElements.put(element.key, element);
                continue;
            }

            if (element.key.startsWith(TmplSortOrder.SORTORDER_ROOT)) {
                if (sortOrdersL18nRoot == null)
                    seq.add(sortOrdersL18nRoot = new Element(element));
                sortOrderL18nElements.put(element.key, element);
                continue;
            }

            if (element.key.startsWith(TmplState.STATE_ROOT)) {
                if (statesL18nRoot == null)
                    seq.add(statesL18nRoot = new Element(element));
                statesL18nElements.put(element.key, element);
                continue;
            }

            String key = element.key;

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

        FileUtil.closeQuietly(in);
    }

    private void updateActions() {

        final Set<String> keys = new TreeSet<String>();

        for (TmplDocument tmplDocument : model.getDocuments())
            if (tmplDocument.getName() != DocflowConfig.UDT_DOCUMENT)
                for (TmplAction tmplAction : tmplDocument.getActions())
                    keys.add(lastTwoPartsOfTheKey(tmplAction.getTitle()));

        for (String actionKey : keys) {
            if (actionsL18nRoot == null)
                seq.add(actionsL18nRoot = new Element("# General actions L18n"));
            copyOrAddKey(actionsL18nRoot, actionKey, lastPartOfTheKey(actionKey), actionsL18nElements);
        }
    }

    private void updateActionsParams() {

        final Set<String> keys = new TreeSet<String>();

        for (TmplDocument tmplDocument : model.getDocuments())
            if (tmplDocument.getName() != DocflowConfig.UDT_DOCUMENT)
                for (TmplAction tmplAction : tmplDocument.getActions())
                    if (tmplAction.getParams() != null)
                        for (TmplField param : tmplAction.getParams())
                            keys.add(lastTwoPartsOfTheKey(param.getTitle()));

        for (String actionKey : keys) {
            if (actionsParamsL18nRoot == null)
                seq.add(actionsParamsL18nRoot = new Element("# General actions parameters L18n"));
            copyOrAddKey(actionsParamsL18nRoot, actionKey, lastPartOfTheKey(actionKey), actionsParamsL18nElements);
        }
    }

    private void updateFields() {

        final Set<String> keys = new TreeSet<String>();

        for (TmplDocument tmplDocument : model.getDocuments())
            if (tmplDocument.getName() != DocflowConfig.UDT_DOCUMENT)
                for (TmplField tmplField : tmplDocument.getFields()) {
                    keys.add(lastTwoPartsOfTheKey(tmplField.getTitle()));
                    if (tmplField.getFields() != null)
                        updateSubfields(tmplField.getFields(), tmplDocument, keys);
                }

        for (String fld : keys) {
            if (fieldsL18nRoot == null)
                seq.add(fieldsL18nRoot = new Element("# General fields L18n"));
            copyOrAddKey(fieldsL18nRoot, fld, lastPartOfTheKey(fld), fieldsL18nElements);
        }
    }

    private void updateSubfields(final ImmutableList<TmplField> fields, TmplDocument tmplDocument, Set<String> keys) {
        for (TmplField tmplField : fields) {
            keys.add(lastTwoPartsOfTheKey(tmplField.getTitle()));
            if (tmplField.getFields() != null)
                updateSubfields(tmplField.getFields(), tmplDocument, keys);
        }
    }

    private void updateFilters() {

        final Set<String> commonFilters = new TreeSet<String>();

        for (TmplDocument tmplDocument : model.getDocuments())
            if (tmplDocument.getName() != DocflowConfig.UDT_DOCUMENT)
                for (TmplFilter tmplFilter : tmplDocument.getFilters())
                    commonFilters.add(lastTwoPartsOfTheKey(tmplFilter.getTitle()));

        final HashMap<String, Element> sortFiltersByKey = new HashMap<String, Element>();
        for (Element el : filterL18nElements.values())
            sortFiltersByKey.put(el.key, el);

        for (String filter : commonFilters) {
            if (filtersL18nRoot == null)
                seq.add(filtersL18nRoot = new Element("# Documents filtering rules"));
            copyOrAddKey(filtersL18nRoot, filter, lastPartOfTheKey(filter), sortFiltersByKey);
        }
    }

    private void updateSortOrders() {

        final Set<String> commonOrders = new TreeSet<String>();

        for (TmplDocument tmplDocument : model.getDocuments())
            if (tmplDocument.getName() != DocflowConfig.UDT_DOCUMENT)
                for (TmplSortOrder tmplSortOrder : tmplDocument.getSortOrders())
                    commonOrders.add(lastTwoPartsOfTheKey(tmplSortOrder.getTitle()));

        final HashMap<String, Element> sortOrdersByKey = new HashMap<String, Element>();
        for (Element el : sortOrderL18nElements.values())
            sortOrdersByKey.put(el.key, el);

        for (String sortOrder : commonOrders) {
            if (sortOrdersL18nRoot == null)
                seq.add(sortOrdersL18nRoot = new Element("# Documents sort orders"));
            copyOrAddKey(sortOrdersL18nRoot, sortOrder, lastPartOfTheKey(sortOrder), sortOrdersByKey);
        }
    }

    private void updateStates() {

        final Set<String> commonStates = new TreeSet<String>();

        for (TmplDocument tmplDocument : model.getDocuments())
            if (tmplDocument.getName() != DocflowConfig.UDT_DOCUMENT)
                for (TmplState tmplState : tmplDocument.getStates())
                    commonStates.add(lastTwoPartsOfTheKey(tmplState.getTitle()));

        final HashMap<String, Element> statesByKey = new HashMap<String, Element>();
        for (Element el : statesL18nElements.values())
            statesByKey.put(el.key, el);

        for (String sortState : commonStates) {
            if (statesL18nRoot == null)
                seq.add(statesL18nRoot = new Element("# Documents states"));
            copyOrAddKey(statesL18nRoot, sortState, lastPartOfTheKey(sortState), statesByKey);
        }
    }

    private void updateTypes() {

        final TmplDocument udt = model.getDocumentByName(DocflowConfig.UDT_DOCUMENT);

        final HashMap<String, Element> typesByKey = new HashMap<String, Element>();
        for (Element el : typesL18nElements.values())
            typesByKey.put(el.key, el);

        final TreeSet<String> sortedTypes = new TreeSet<String>();
        for (TmplField type : udt.getFields()) {
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

        final ImmutableList<TmplDocument> tmplDocs = model.getDocuments();

        TmplDocument udt = model.getDocumentByName(DocflowConfig.UDT_DOCUMENT);

        for (TmplDocument tmplDocument : tmplDocs) {

            if (tmplDocument == udt) continue;

            // get or create at the end document title key
            Element d = docsL18nRoots.get(tmplDocument.getTitle());
            if (d == null) { // create new document element. since it was not found
                d = new Element();
                d.key = tmplDocument.getTitle();
                d.lines.add("");
                d.lines.add(String.format("# Document: %s", tmplDocument.getName()));
                d.lines.add(tmplDocument.getTitle() + "=" + tmplDocument.getName());
                seq.add(d);
                docsL18nRoots.put(tmplDocument.getTitle(), d);
            }

            // list to map
            final HashMap<String, Element> docElementByKey = new HashMap<String, Element>();
            for (Element el : docsL18nElements.get(tmplDocument.getTitle()))
                docElementByKey.put(el.key, el);

            // fields
            localizeFields(d, docElementByKey, tmplDocument.getFields(), udt, tmplDocument);

            // states
            final ImmutableList<TmplState> states = tmplDocument.getStates();
            for (int j = 0; j < states.size(); j++) {
                final String key = states.get(j).getTitle();
                copyOrAddKey(d, key, lastTwoPartsOfTheKey(key), docElementByKey);
            }

            // actions
            for (TmplAction action : tmplDocument.getActions()) {
                final String key = tmplDocument.getActionTitle(action.getName());
                final String defaultTitle = lastTwoPartsOfTheKey(key);
                copyOrAddKey(d, key, defaultTitle, docElementByKey);
                if (action.getParams() != null)
                    for (TmplField param : action.getParams()) {
                        final String title = param.getTitle();
                        final String defaultParamTitle = lastTwoPartsOfTheKey(title);
                        copyOrAddKey(d, title, defaultParamTitle, docElementByKey);
                        if (param.getEnumValues() != null) // It's enum
                            for (TmplEnumValue anEnum : param.getEnumValues())
                                copyOrAddKey(d, param.getTitle() + TmplField.ENUM_LINK + anEnum.getName(), anEnum.getName(), docElementByKey);
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
                            if (tab.getName() != TmplTab.TAB_MAIN)
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

        for (TmplField enumType : model.getDocumentByName(DocflowConfig.UDT_DOCUMENT).getFields()) {
            if (enumType.getEnumValues() == null)
                continue;

            // get or create at the end document title key
            Element d = enumsL18nRoots.get(enumType.getTitle());
            if (d == null) { // create new document element. since it was not found
                d = new Element();
                d.key = enumType.getTitle();
                d.lines.add("");
                d.lines.add(String.format("# Enum: %s", enumType.getName()));
                d.lines.add(enumType.getTitle() + "=" + enumType.getName());
                seq.add(d);
                enumsL18nRoots.put(enumType.getTitle(), d);
            }

            // turn list of enums localization elements to map
            final List<Element> enumElements = enumsL18nElements.get(enumType.getTitle());
            final HashMap<String, Element> enumElementByKey = new HashMap<String, Element>();
            for (int j = 0; j < enumElements.size(); j++) {
                Element el = enumElements.get(j);
                enumElementByKey.put(el.key, el);
            }

            // add enum values
            for (TmplEnumValue enumValue : enumType.getEnumValues())
                copyOrAddKey(d, enumValue.getTitle(), enumValue.getTitle(), enumElementByKey);
        }
    }

    private void updateStructures() {
        for (TmplField structureType : model.getDocumentByName(DocflowConfig.UDT_DOCUMENT).getFields()) {
            if (structureType.getFields() == null) continue;
            // get or create at the end document title key
            Element d = structuresL18nRoots.get(structureType.getTitle());
            if (d == null) { // create new document element. since it was not found
                d = new Element();
                d.key = structureType.getTitle();
                d.lines.add("");
                d.lines.add(String.format("# Structure: %s", structureType.getName()));
                d.lines.add(structureType.getTitle() + "=" + structureType.getName());
                seq.add(d);
                structuresL18nRoots.put(structureType.getTitle(), d);
            }
            // turn list of enums localization elements to map
            final List<Element> structureElements = structuresL18nElements.get(structureType.getTitle());
            final HashMap<String, Element> structureElementByKey = new HashMap<String, Element>();
            for (int j = 0; j < structureElements.size(); j++) {
                Element el = structureElements.get(j);
                structureElementByKey.put(el.key, el);
            }
            localizeFieldsForStructure(d, structureElementByKey, structureType.getFields());
        }
    }

    private void saveL18n() throws UnsupportedEncodingException {
        FileUtil.OldLF oldLF = FileUtil.setUnixLF();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        final OutputStreamWriter sw = new OutputStreamWriter(os, "UTF-8");
        final PrintWriter pw = new PrintWriter(sw);
        for (Element topElement : seq) {
            for (String line : topElement.lines)
                pw.println(line);
            for (Element subElement : topElement.followers)
                for (String line : subElement.lines)
                    pw.println(line);
        }
        pw.flush();
        FileUtil.saveFileIfChanged(messagesFile, os);
        oldLF.restore();
    }

    private void localizeFields(Element d, HashMap<String, Element> docElementByKey,
                                ImmutableList<TmplField> fields,
                                TmplDocument udt, TmplDocument tmplDocument) {
        // NOTE: Attn this method MUST implement just the same logic as localizeFieldsForStructure() below
        for (TmplField fld : fields) {
            if (!fld.getUdtType()) {
                copyOrAddKey(d, fld.getTitle(), lastTwoPartsOfTheKey(fld.getTitle()), docElementByKey);
                if (fld.getEnumValues() != null) // It's enum
                    for (TmplEnumValue anEnum : fld.getEnumValues())
                        copyOrAddKey(d, fld.getTitle() + TmplField.ENUM_LINK + anEnum.getName(), anEnum.getName(), docElementByKey);
                else if (fld.getFields() != null) // It's either structure or subtable
                    localizeFields(d, docElementByKey, fld.getFields(), udt, tmplDocument);
            } else {
                final TmplField udtType = udt.getFieldByName(fld.getType());
                copyOrAddKey(d, fld.getTitle(), udtType.getTitle(), docElementByKey);
            }
        }
    }

    private void localizeFieldsForStructure(Element d, HashMap<String, Element> docElementByKey,
                                            ImmutableList<TmplField> fields) {
        // NOTE: Attn this method MUST implement just the same logic as localizeFields() above
        for (TmplField fld : fields)
            if (fld.getTitle() != null) {
                copyOrAddKey(d, fld.getTitle(), lastTwoPartsOfTheKey(fld.getTitle()), docElementByKey);
                if (fld.getUdtType())
                    if (fld.getEnumValues() != null)
                        for (TmplEnumValue anEnum : fld.getEnumValues())
                            copyOrAddKey(d, anEnum.getTitle(), anEnum.getName(), docElementByKey);
                    else if (fld.getFields() != null)
                        localizeFieldsForStructure(d, docElementByKey, fld.getFields());
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
        Element existingElement = elementByKey.get(key);
        if (existingElement == null) {
            existingElement = new Element();
            existingElement.key = key;
            existingElement.lines.add(key + "=" + value);
        }
        element.followers.add(existingElement);
    }

    private String[] collectEnumsPrefixes(TmplModel root) {
        final ArrayList<String> prefixes = new ArrayList<String>();
        for (TmplField enumType : root.getDocumentByName(DocflowConfig.UDT_DOCUMENT).getFields()) {
            if (enumType.getEnumValues() == null)
                continue;
            prefixes.add(enumType.getTitle());
        }
        return prefixes.toArray(new String[0]);
    }

    private String[] collectStructuresPrefixes(TmplModel root) {
        final ArrayList<String> prefixes = new ArrayList<String>();
        for (TmplField structureType : root.getDocumentByName(DocflowConfig.UDT_DOCUMENT).getFields()) {
            if (structureType.getFields() == null) continue;
            prefixes.add(structureType.getTitle());
        }
        return prefixes.toArray(new String[0]);
    }

    private String[] collectDocumentsPrefixes(TmplModel root) {
        final ArrayList<String> prefixes = new ArrayList<String>();
        for (TmplDocument tmplDocument : root.getDocuments())
            prefixes.add(tmplDocument.getTitle());
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
