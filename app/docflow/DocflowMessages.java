package docflow;

import code.docflow.controlflow.Result;
import code.docflow.messages.MessageContainerClass;
import code.docflow.messages.MessageTemplate;

@MessageContainerClass
public class DocflowMessages {

    public static final MessageTemplate error_HttpConcurrency_4 = new MessageTemplate(Result.Error, "Document '%1$s' was modified in parallel by '%3$s'.  Please, repeate your action.", "DOCFLOWMESSAGES.HTTPCONCURRENCY");

    public static final MessageTemplate info_HttpUpdateSucceeded = new MessageTemplate(Result.Ok, "Update succeeded", "DOCFLOWMESSAGES.HTTPUPDATESUCCEEDED");

    public static final MessageTemplate info_HttpCreateSucceeded = new MessageTemplate(Result.Ok, "Create succeeded", "DOCFLOWMESSAGES.HTTPCREATESUCCEEDED");

    public static final MessageTemplate info_HttpDeleteSucceeded = new MessageTemplate(Result.Ok, "Delete succeeded", "DOCFLOWMESSAGES.HTTPDELETESUCCEEDED");

    public static final MessageTemplate info_HttpRecoverSucceeded = new MessageTemplate(Result.Ok, "Recover succeeded", "DOCFLOWMESSAGES.HTTPRECOVERSUCCEEDED");

    public static final MessageTemplate error_HttpInvalidActionParams = new MessageTemplate(Result.Failed, "Invalid action params", "DOCFLOWMESSAGES.HTTPINVALIDACTIONPARAMS");

    public static final MessageTemplate info_HttpActionSucceeded_1 = new MessageTemplate(Result.Ok, "Action %1$s succeeded", "DOCFLOWMESSAGES.HTTPACTIONSUCCEEDED");

    public static final MessageTemplate error_ValidationJsonObjectExpected_2 = new MessageTemplate(Result.Error, "In '%1$s' a json object is expected, not '%2$s'.", "DOCFLOWMESSAGES.VALIDATIONJSONOBJECTEXPECTED");

    public static final MessageTemplate error_ValidationFieldRequired_1 = new MessageTemplate(Result.Error, "'%1$s' required.", "DOCFLOWMESSAGES.VALIDATIONFIELDREQUIRED");

    public static final MessageTemplate error_ValidationFieldUnexpected_1 = new MessageTemplate(Result.Error, "'%1$s' unexpected.", "DOCFLOWMESSAGES.VALIDATIONFIELDUNEXPECTED");

    public static final MessageTemplate error_ValidationFieldUnknown_1 = new MessageTemplate(Result.Error, "'%1$s' unknown.", "DOCFLOWMESSAGES.VALIDATIONFIELDUNKNOWN");

    public static final MessageTemplate error_ValidationFieldIncorrectValue_2 = new MessageTemplate(Result.Error, "'%1$s' incorrect value: %2$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDINCORRECTVALUE");

    public static final MessageTemplate error_ValidationFieldIncorrectJson_4 = new MessageTemplate(Result.Error, "'%1$s' incorrect json: positions (%2$s, %3$s): %4$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDINCORRECTJSON");

    public static final MessageTemplate error_ValidationFieldIncorrectEnumValue_2 = new MessageTemplate(Result.Error, "'%1$s' incorrect enum value: %2$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDINCORRECTENUMVALUE");

    public static final MessageTemplate error_ValidationFieldNotAllowedReplaceLinkedDocument_1 = new MessageTemplate(Result.Error, "'%1$s' not allowed replace linked document.", "DOCFLOWMESSAGES.VALIDATIONFIELDNOTALLOWEDREPLACELINKEDDOCUMENT");

    public static final MessageTemplate error_ValidationFieldMin_2 = new MessageTemplate(Result.Error, "'%1$s' cannot be lower than %2$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDMIN");

    public static final MessageTemplate error_ValidationFieldMax_2 = new MessageTemplate(Result.Error, "'%1$s' cannot be greater than %2$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDMAX");

    public static final MessageTemplate error_ValidationFieldNotInRange_3 = new MessageTemplate(Result.Error, "'%1$s' not in the range %2$s through %3$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDNOTINRANGE");

    public static final MessageTemplate error_ValidationFieldMinLength_2 = new MessageTemplate(Result.Error, "'%1$s' cannot be shorter than %2$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDMINLENGTH");

    public static final MessageTemplate error_ValidationFieldMaxLength_2 = new MessageTemplate(Result.Error, "'%1$s' cannot be longer than %2$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDMAXLENGTH");

    public static final MessageTemplate error_ValidationFieldInvalidEmail_1 = new MessageTemplate(Result.Error, "'%1$s' invalid email value.", "DOCFLOWMESSAGES.VALIDATIONFIELDINVALIDEMAIL");

    public static final MessageTemplate error_ValidationFieldInvalidPhone_1 = new MessageTemplate(Result.Error, "'%1$s' invalid phone number.", "DOCFLOWMESSAGES.VALIDATIONFIELDINVALIDPHONE");

    public static final MessageTemplate error_ValidationFieldPattern_2 = new MessageTemplate(Result.Error, "'%1$s' must follow pattern '%2$s'.", "DOCFLOWMESSAGES.VALIDATIONFIELDPATTERN");

    public static final MessageTemplate error_ValidationSubtableWrongIndexValue_2 = new MessageTemplate(Result.Error, "Index field '%1$s': Wrong value: '%2$s'.", "DOCFLOWMESSAGES.VALIDATIONSUBTABLEWRONGINDEXVALUE");

    public static final MessageTemplate error_ValidationSubtableIndexOutOfRange_3 = new MessageTemplate(Result.Error, "Index field '%1$s': '%2$s' out of range 0 to %3$s.", "DOCFLOWMESSAGES.VALIDATIONSUBTABLEINDEXOUTOFRANGE");

    public static final MessageTemplate error_ValidationSubtableIncorrectOrder_1 = new MessageTemplate(Result.Error, "'%1$s': Incorrect order information.", "DOCFLOWMESSAGES.VALIDATIONSUBTABLEINCORRECTORDER");

    public static final MessageTemplate error_ValidationStateCanOnlyBeAssignedToAnewDocument_1 = new MessageTemplate(Result.Error, "'%1$s': 'state' can only be applied to a new document.", "DOCFLOWMESSAGES.VALIDATIONSTATECANONLYBEASSIGNEDTOANEWDOCUMENT");

    public static final MessageTemplate error_ValidationStateNullOrEmpty_1 = new MessageTemplate(Result.Error, "'%1$s': 'state' cannot be null or empty.", "DOCFLOWMESSAGES.VALIDATIONSTATENULLOREMPTY");

    public static final MessageTemplate error_ValidationStateValue_2 = new MessageTemplate(Result.Error, "'%1$s': Wrong state value 'state': %2$s.", "DOCFLOWMESSAGES.VALIDATIONSTATEVALUE");

    public static final MessageTemplate error_DocflowDocumentNotFound_1 = new MessageTemplate(Result.Error, "Document '%1$s': Not found", "DOCFLOWMESSAGES.DOCFLOWDOCUMENTNOTFOUND");

    public static final MessageTemplate error_DocflowInsufficientRights_2 = new MessageTemplate(Result.Error, "Insufficient rights to perform '%2$s' on document '%1$s'.", "DOCFLOWMESSAGES.DOCFLOWINSUFFICIENTRIGHTS");

    public static final MessageTemplate error_DocflowObsoleteRevision_3 = new MessageTemplate(Result.Error, "Document '%1$s': Document concurrent update. Update for revision %2$s may not update revision %3$s.", "DOCFLOWMESSAGES.DOCFLOWOBSOLETEREVISION");

    public static final MessageTemplate error_DocflowActionNoAllowedInState_3 = new MessageTemplate(Result.Error, "Document '%1$s':  Action '%2$s' is not allowed in state '%3$s'.", "DOCFLOWMESSAGES.DOCFLOWACTIONNOALLOWEDINSTATE");

    public static final MessageTemplate error_ActionParamsMissingParameter_2 = new MessageTemplate(Result.Error, "Action '%1$s': Missing parameter '%2$s'", "DOCFLOWMESSAGES.ACTIONPARAMSMISSINGPARAMETER");

    public static final MessageTemplate error_ValidationFieldUnknownDocType_2 = new MessageTemplate(Result.Error, "'%1$s' unknow document type: %2$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDUNKNOWNDOCTYPE");

    public static final MessageTemplate error_ValidationFieldNoAccessToDocType_2 = new MessageTemplate(Result.Error, "'%1$s' no access to document type: %2$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDNOACCESSTODOCTYPE");

    public static final MessageTemplate error_ValidationFieldNoAccessToTheDocument_2 = new MessageTemplate(Result.Error, "'%1$s' no access to the document: %2$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDNOACCESSTOTHEDOCUMENT");

    public static final MessageTemplate error_ValidationFieldNoSuchDocument_2 = new MessageTemplate(Result.Error, "'%1$s' no such document: %2$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDNOSUCHDOCUMENT");

    public static final MessageTemplate error_ProhibitedToUpdateVersionedDocumentFromPersistedDocument_2 = new MessageTemplate(Result.Error, "Due to history consistency it's prohibited to update versioned document (%2$s) from persisted document action (%1$s).", "DOCFLOWMESSAGES.PROHIBITEDTOUPDATEVERSIONEDDOCUMENTFROMPERSISTEDDOCUMENT");

    public static final MessageTemplate error_CannotDeleteOrRecoverNonVersionedDocument_1 = new MessageTemplate(Result.Error, "Cannot delete or recover non versioned document (%1$s).", "DOCFLOWMESSAGES.CANNOTDELETEORRECOVERNONVERSIONEDDOCUMENT");

    public static final MessageTemplate error_FailedWithException_1 = new MessageTemplate(Result.Error, "Failed with exception (Ref: %1$s)", "DOCFLOWMESSAGES.FAILEDWITHEXCEPTION");

    public static final MessageTemplate info_FailureContext_1 = new MessageTemplate(Result.Ok, "Failed while %1$s", "DOCFLOWMESSAGES.FAILURECONTEXT");

    public static final MessageTemplate error_QueryParamInvalidType_3 = new MessageTemplate(Result.Error, "Parameter '%1$s': Expected type '%2$s', but was '%3$s'.", "DOCFLOWMESSAGES.QUERYPARAMINVALIDTYPE");

    public static final MessageTemplate error_QueryParamInvalidValue_2 = new MessageTemplate(Result.Error, "Parameter '%1$s': Unexpected value '%2$s'.", "DOCFLOWMESSAGES.QUERYPARAMINVALIDVALUE");

    public static final MessageTemplate error_ConvertFailed_2 = new MessageTemplate(Result.Error, "Converter to %1$s failed: Cmd line: %2$s", "DOCFLOWMESSAGES.CONVERTFAILED");
}
