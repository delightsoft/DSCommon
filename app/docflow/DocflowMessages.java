package docflow;

import code.controlflow.Result;
import code.docflow.messages.MessageTemplate;

public class DocflowMessages {

    public static final MessageTemplate error_HttpConcurrency_4 = new MessageTemplate(Result.Failed, "Document '%1$s' was modified in parallel by '%3$s'.  Please, repeate your action.", "DOCFLOWMESSAGES.HTTPCONCURRENCY");

    public static final MessageTemplate info_HttpUpdateSucceeded = new MessageTemplate(Result.Ok, "Update succeeded", "DOCFLOWMESSAGES.HTTPUPDATESUCCEEDED");

    public static final MessageTemplate info_HttpCreateSucceeded = new MessageTemplate(Result.Ok, "Create succeeded", "DOCFLOWMESSAGES.HTTPCREATESUCCEEDED");

    public static final MessageTemplate info_HttpDeleteSucceeded = new MessageTemplate(Result.Ok, "Delete succeeded", "DOCFLOWMESSAGES.HTTPDELETESUCCEEDED");

    public static final MessageTemplate info_HttpRecoverSucceeded = new MessageTemplate(Result.Ok, "Recover succeeded", "DOCFLOWMESSAGES.HTTPRECOVERSUCCEEDED");

    public static final MessageTemplate error_HttpInvalidActionParams = new MessageTemplate(Result.Failed, "Invalid action params", "DOCFLOWMESSAGES.HTTPINVALIDACTIONPARAMS");

    public static final MessageTemplate info_HttpActionSucceeded_1 = new MessageTemplate(Result.Ok, "Action %1$s succeeded", "DOCFLOWMESSAGES.HTTPACTIONSUCCEEDED");

    public static final MessageTemplate error_ValidationFieldRequired_1 = new MessageTemplate(Result.Failed, "'%1$s' required.", "DOCFLOWMESSAGES.VALIDATIONFIELDREQUIRED");

    public static final MessageTemplate error_ValidationFieldIncorrectValue_2 = new MessageTemplate(Result.Failed, "'%1$s' incorrect value: %2$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDINCORRECTVALUE");

    public static final MessageTemplate error_ValidationFieldIncorrectEnumValue_2 = new MessageTemplate(Result.Failed, "'%1$s' incorrect enum value: %2$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDINCORRECTENUMVALUE");

    public static final MessageTemplate error_ValidationFieldNotAllowedReplaceLinkedDocument_1 = new MessageTemplate(Result.Failed, "'%1$s' not allowed replace linked document.", "DOCFLOWMESSAGES.VALIDATIONFIELDNOTALLOWEDREPLACELINKEDDOCUMENT");

    public static final MessageTemplate error_ValidationFieldMin_2 = new MessageTemplate(Result.Failed, "'%1$s' cannot be lower than %2$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDMIN");

    public static final MessageTemplate error_ValidationFieldMax_2 = new MessageTemplate(Result.Failed, "'%1$s' cannot be greater than %2$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDMAX");

    public static final MessageTemplate error_ValidationFieldNotInRange_3 = new MessageTemplate(Result.Failed, "'%1$s' not in the range %2$s through %3$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDNOTINRANGE");

    public static final MessageTemplate error_ValidationFieldMinLength_2 = new MessageTemplate(Result.Failed, "'%1$s' cannot be shorter than %2$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDMINLENGTH");

    public static final MessageTemplate error_ValidationFieldMaxLength_2 = new MessageTemplate(Result.Failed, "'%1$s' cannot be longer than %2$s.", "DOCFLOWMESSAGES.VALIDATIONFIELDMAXLENGTH");

    public static final MessageTemplate error_ValidationFieldPattern_2 = new MessageTemplate(Result.Failed, "'%1$s' must follow pattern '%2$s'.", "DOCFLOWMESSAGES.VALIDATIONFIELDPATTERN");

    public static final MessageTemplate error_ValidationSubtableWrongIndexValue_2 = new MessageTemplate(Result.Failed, "Index field '%1$s': Wrong value: '%2$s'.", "DOCFLOWMESSAGES.VALIDATIONSUBTABLEWRONGINDEXVALUE");

    public static final MessageTemplate error_ValidationSubtableIndexOutOfRange_3 = new MessageTemplate(Result.Failed, "Index field '%1$s': '%2$s' out of range 0 to %3$s.", "DOCFLOWMESSAGES.VALIDATIONSUBTABLEINDEXOUTOFRANGE");

    public static final MessageTemplate error_ValidationSubtableIncorrectOrder_1 = new MessageTemplate(Result.Failed, "'%1$s': Incorrect order information.", "DOCFLOWMESSAGES.VALIDATIONSUBTABLEINCORRECTORDER");

    public static final MessageTemplate error_ValidationStateCanOnlyBeAssignedToAnewDocument_1 = new MessageTemplate(Result.Failed, "'%1$s': 'state' can only be applied to a new document.", "DOCFLOWMESSAGES.VALIDATIONSTATECANONLYBEASSIGNEDTOANEWDOCUMENT");

    public static final MessageTemplate error_ValidationStateNullOrEmpty_1 = new MessageTemplate(Result.Failed, "'%1$s': 'state' cannot be null or empty.", "DOCFLOWMESSAGES.VALIDATIONSTATENULLOREMPTY");

    public static final MessageTemplate error_ValidationStateValue_2 = new MessageTemplate(Result.Failed, "'%1$s': Wrong state value 'state': %2$s.", "DOCFLOWMESSAGES.VALIDATIONSTATEVALUE");

    public static final MessageTemplate error_DocflowDocumentNotFound_1 = new MessageTemplate(Result.Failed, "Document '%1$s': Not found", "DOCFLOWMESSAGES.DOCFLOWDOCUMENTNOTFOUND");

    public static final MessageTemplate error_DocflowInsufficientRights_2 = new MessageTemplate(Result.Failed, "Insufficient rights to perform '%2$s' on document '%1$s'.", "DOCFLOWMESSAGES.DOCFLOWINSUFFICIENTRIGHTS");

    public static final MessageTemplate error_DocflowObsoleteRevision_3 = new MessageTemplate(Result.Failed, "Document '%1$s': Document concurrent update. Update for revision %2$s may not update revision %3$s.", "DOCFLOWMESSAGES.DOCFLOWOBSOLETEREVISION");

    public static final MessageTemplate error_DocflowActionNoAllowedInState_3 = new MessageTemplate(Result.Failed, "Document '%1$s':  Action '%2$s' is not allowed in state '%3$s'.", "DOCFLOWMESSAGES.DOCFLOWACTIONNOALLOWEDINSTATE");

    public static final MessageTemplate error_ActionParamsMissingParameter_2 = new MessageTemplate(Result.Failed, "Action '%1$s': Missing parameter '%2$s'", "DOCFLOWMESSAGES.ACTIONPARAMSMISSINGPARAMETER");
}
