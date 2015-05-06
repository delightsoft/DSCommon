package controllers;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

import code.docflow.DocflowConfig;
import code.docflow.api.DocflowApiFile;
import code.docflow.api.http.ActionResult;
import code.docflow.api.http.DocflowRenderFile;
import code.docflow.api.http.ResultFile;
import code.docflow.compiler.enums.BuiltInTemplates;
import code.docflow.controlflow.Result;
import code.docflow.docs.Document;
import code.docflow.docs.DocumentPersistent;
import code.docflow.jsonBinding.JsonTypeBinder;
import code.docflow.model.Action;
import code.docflow.model.DocType;
import code.docflow.model.Field;
import code.docflow.rights.DocumentAccessActionsRights;
import code.docflow.rights.RightsCalculator;
import code.docflow.types.DocumentRef;
import code.docflow.users.CurrentUser;
import code.docflow.utils.BooleanUtil;
import code.docflow.utils.FileUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import models.DocflowFile;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.db.jpa.Transactional;
import play.exceptions.UnexpectedException;
import play.mvc.With;

import java.io.*;

@With(CurrentUser.CheckAccess.class)
public class DocflowHttpFileController extends DocflowControllerBase {
    @Transactional(readOnly = false)
    public static void upload(@Required DocumentRef docOrDocType, String field, String action, String t, String rt, String ot, @Required File file) {

        boolean isField = !Strings.isNullOrEmpty(field);
        boolean isAction = !Strings.isNullOrEmpty(action);

        if (!(isField ^ isAction)) {
            if (isField)
                Validation.addError("field", "'field' and 'action' specified in the same time");
            else
                Validation.addError("field", "Neither 'field' or 'action' is specified");
        }

        if (file == null)
            Validation.addError("file", "Missing or empty");

        if (Strings.isNullOrEmpty(t))
            t = BuiltInTemplates.LIST.toString();
        if (Strings.isNullOrEmpty(rt))
            rt = t;
        if (Strings.isNullOrEmpty(ot))
            ot = t;

        returnIfErrors();

        final DocType _docType = DocflowConfig.instance.documents.get(docOrDocType.type.toUpperCase());
        if (_docType == null)
            Validation.addError("docOrDocType", "Unknown docType '%s'.", docOrDocType.type);
        returnIfErrors();

        final Field _field = !isField ? null : _docType.fieldByFullname.get(field.toUpperCase());
        final Action _action = !isAction ? null : _docType.actions.get(action.toUpperCase());

        if (isField && _field == null)
            Validation.addError("field", "DocType '%s': Specified field '%s' not found.", docOrDocType.type, field);
        if (isAction && _action == null)
            Validation.addError("action", "DocType '%s': Specified action '%s' not found.", docOrDocType.type, action);
        returnIfErrors();

        final DocumentAccessActionsRights fullRights = RightsCalculator.instance.calculate(_docType, CurrentUser.getInstance().getUserRoles());
        if (isField && !fullRights.updateMask.get(_field.index))
            Validation.addError("field", "User '%s': DocType '%s': Has no right to updater field '%s'.", CurrentUser.getInstance().getUser().toString(), docOrDocType.type, _field.fullname);
        else if (isAction && !fullRights.actionsMask.get(_action.index))
            Validation.addError("action", "User '%s': DocType '%s': Has no right to call action '%s'.", CurrentUser.getInstance().getUser().toString(), docOrDocType.type, _action.name);
        returnIfErrors();

        if (!docOrDocType.isNew())
            try {
                final Document _doc = docOrDocType.safeGetDocument();
                final DocumentAccessActionsRights rights = RightsCalculator.instance.calculate(_doc, CurrentUser.getInstance());
                if (isField && !rights.updateMask.get(_field.index))
                    Validation.addError("field", "User '%s': Document '%s': Has no right to updater field '%s'.", CurrentUser.getInstance().getUser().toString(), _doc.toString(), _field.fullname);
                else if (isAction && !rights.actionsMask.get(_action.index))
                    Validation.addError("action", "User '%s': Document '%s': Has no right to call action '%s'.", CurrentUser.getInstance().getUser().toString(), _doc.toString(), _action.name);
            } catch (DocumentRef.DocumentAccessException e) {
                switch (e.type) {
                    case NO_ACCESS_TO_DOCUMENT:
                        Validation.addError("docOrDocType", "User '%s': Document '%s': No access.", CurrentUser.getInstance().getUser().toString(), docOrDocType.toString());
                        break;
                    case NO_SUCH_DOCUMENT:
                        Validation.addError("docOrDocType", "Document '%s': No such document.", docOrDocType.toString());
                        break;
                }
                returnIfErrors();
            }
        Result result = new Result();
        DocflowFile res = DocflowApiFile._persistFile(file, null, result);
        throw new ActionResult(null,
                result.isError() ? null : res,
                ot, rt, result);
    }

    public static void download(@Required String filename, final String doc, final String file, final String key, final String inline) {

        returnIfErrors();

        if (!(Strings.isNullOrEmpty(doc) ^ Strings.isNullOrEmpty(file))) {
            Validation.addError("temp", "Either 'doc' or 'file' parameter is required.");
            returnIfErrors();
        }

        // TODO: This code was not actually used.  It relies on missing argument 'temp'.  Remove after 2015-3-1 if non use will be found
//        if (!Strings.isNullOrEmpty(file)) {
//            if (Strings.isNullOrEmpty(file)) {
//                Validation.addError("temp", "Either 'doc' or 'temp' parameter is required.");
//                returnIfErrors();
//            }
//            final String tempFilename = file + FileUtil.extension(filename);
//            final File tempFile = new File(ResultFile.FILES_TEMP_DIR, tempFilename);
//            if (!tempFile.exists())
//                notFound(tempFilename);
//
//            throw new DocflowRenderFile(tempFile, true, BooleanUtil.parse(inline));
//        }

        DocumentRef docflowFile = null;
        try {
            docflowFile = DocumentRef.parse(doc);
            if (!DocflowFile._type().name.equals(docflowFile.type))
                docflowFile = null;
        } catch (IllegalArgumentException e) { // try temp file
            // nothing
        }

        // it's persistent file - DocflowFile@...
        if (docflowFile.isNew()) {
            Validation.addError("fileDocId", "Must specify existing document.");
            returnIfErrors();
        }

        try {
            final Result result = new Result();
            final DocflowFile _fileDoc = docflowFile.safeGetDocument();
            if (!Strings.isNullOrEmpty(key)) { // then just if key corrent, just return the document
                final File keyFile = new File(ResultFile.KEY_TEMP_DIR, key + ResultFile.KEY_FILE_EXT);
                if (!keyFile.exists()) {
                    Validation.addError("key", "Key '%s': Does not exist.", key);
                    returnIfErrors();
                }
                try {
                    final BufferedReader in = new BufferedReader(new FileReader(keyFile));
                    try {
                        if (!doc.equals(in.readLine())) {
                            Validation.addError("key", "Key '%s': Does not give access to document '%s'.", key, doc);
                            returnIfErrors();
                        }
                        final File resultFile = DocflowApiFile._getFile(_fileDoc, result);
                        if (result.isError())
                            error(400, result.toString());
                        throw new DocflowRenderFile(resultFile, false, BooleanUtil.parse(inline));
                    } catch (IOException e) {
                        throw new UnexpectedException(e);
                    } finally {
                        FileUtil.closeQuietly(in);
                        keyFile.delete();
                    }
                } catch (FileNotFoundException e) {
                    throw new UnexpectedException(e);
                }
            } else {
                if (_fileDoc.field == null || _fileDoc.document == null) {
                    Validation.addError("fileDocId", "Document '%s': Cannot be download cause it's not linked to a document field.", docflowFile.toString());
                    returnIfErrors();
                }
                try {
                    final DocumentPersistent _doc = _fileDoc.document.safeGetDocument();
                    final DocumentAccessActionsRights _docRights = RightsCalculator.instance.calculate(_doc, CurrentUser.getInstance());
                    final Field _field = _doc._docType().fieldByFullname.get(_fileDoc.field.toUpperCase());
                    if (_field == null) {
                        Validation.addError("fileDocId", "Document '%s': There is no field '%s'  (referenced by DocflowFile).", _fileDoc.document.toString(), _fileDoc.field);
                        returnIfErrors();
                    }
                    if (!_docRights.viewMask.get(_field.index)) {
                        Validation.addError("fileDocId", "Document '%s': No view access to field '%s'  (referenced by DocflowFile).", _fileDoc.document.toString(), _fileDoc.field);
                        returnIfErrors();
                    }
                    final File resultFile = DocflowApiFile._getFile(_fileDoc, result);
                    if (result.isError())
                        error(400, result.toString());
                    throw new DocflowRenderFile(resultFile, false, BooleanUtil.parse(inline));
                } catch (DocumentRef.DocumentAccessException e) {
                    switch (e.type) {
                        case UNKNOWN_DOCUMENT_TYPE:
                            Validation.addError("docOrDocType", "User '%s': Where is no docType '%s' (referenced by DocflowFile).", CurrentUser.getInstance().getUser().toString(), _fileDoc.document.type);
                            break;
                        case NO_ACCESS_TO_DOCUMENT_TYPE:
                            Validation.addError("docOrDocType", "User '%s': Has no access to docType '%s' (referenced by DocflowFile).", CurrentUser.getInstance().getUser().toString(), _fileDoc.document.type);
                            break;
                        case NO_ACCESS_TO_DOCUMENT:
                            Validation.addError("docOrDocType", "User '%s': Document '%s': No access (referenced by DocflowFile).", CurrentUser.getInstance().getUser().toString(), _fileDoc.document.toString());
                            break;
                        case NO_SUCH_DOCUMENT:
                            Validation.addError("docOrDocType", "Document '%s': No such document (referenced by DocflowFile).", _fileDoc.document.toString());
                            break;
                    }
                    returnIfErrors();
                }
            }
        } catch (DocumentRef.DocumentAccessException e) {
            switch (e.type) {
                case NO_ACCESS_TO_DOCUMENT_TYPE:
                    Validation.addError("docOrDocType", "User '%s': Has no access to docType '%s'.", CurrentUser.getInstance().getUser().toString(), docflowFile.type);
                    break;
                case NO_ACCESS_TO_DOCUMENT:
                    Validation.addError("docOrDocType", "User '%s': Document '%s': No access.", CurrentUser.getInstance().getUser().toString(), docflowFile.toString());
                    break;
                case NO_SUCH_DOCUMENT:
                    Validation.addError("docOrDocType", "Document '%s': No such document.", docflowFile.toString());
                    break;
            }
            returnIfErrors();
        }
    }
}
