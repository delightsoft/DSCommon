package code.docflow.yaml;

import code.controlflow.Result;
import code.docflow.messages.MessageTemplate;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public class YamlMessages {
    public static final MessageTemplate error_FileNotFound = new MessageTemplate(Result.Failed, "Docflow: File '%1$s' not found.");
    public static final MessageTemplate error_NoRolesDefinitions = new MessageTemplate(Result.Ok, "Docflow: None role definition files are found.");
    public static final MessageTemplate error_FileSkipped = new MessageTemplate(Result.Ok, "Docflow: File '%1$s' skipped, since same named file presents in prior location.");

    public static final MessageTemplate error_InvalidYamlFormat = new MessageTemplate(Result.Failed, "%1$s(%3$s, %4$s): %2$s.");
    public static final MessageTemplate error_WrongValueForField = new MessageTemplate(Result.Failed, "%1$sWrong value for field '%3$s %2$s': %4$s.");
    public static final MessageTemplate error_WrongValueForType = new MessageTemplate(Result.Failed, "%1$sWrong value for type '%2$s': %3$s.");
    public static final MessageTemplate error_InvalidStructure = new MessageTemplate(Result.Failed, "%1$sInvalid structure: %2$s.");
    public static final MessageTemplate error_InvalidProperty = new MessageTemplate(Result.Failed, "%1$sInvalid property: %2$s.");
    public static final MessageTemplate error_DuplicatedPropertyAssignment = new MessageTemplate(Result.Failed, "%1$sDuplicated assignment to property '%2$s'.");
    public static final MessageTemplate error_DuplicatedMapKey = new MessageTemplate(Result.Failed, "%1$sDuplicated map key: %2$s.");
    public static final MessageTemplate error_CannotConvertKey = new MessageTemplate(Result.Failed, "%1$sCannot convert key to type '%2$s': %3$s.");
    public static final MessageTemplate error_InvalidRootElementKey = new MessageTemplate(Result.Failed, "%1$sInvalid root element key: %2$s");
    public static final MessageTemplate error_InvalidFieldDescription = new MessageTemplate(Result.Failed, "%1$sInvalid field description: '%2$s'.");
    public static final MessageTemplate error_InvalidTransitionFormat = new MessageTemplate(Result.Failed, "%1$sInvalid transition format: '%2$s'.");
    public static final MessageTemplate error_InvalidRoleRightFormat = new MessageTemplate(Result.Failed, "%1$sInvalid role right format: '%2$s'.");
    public static final MessageTemplate error_UnknownFlag = new MessageTemplate(Result.Failed, "%1$sUnknown flag '%2$s'.");
    public static final MessageTemplate error_DocumentClassNameIsMissingAfterRefers = new MessageTemplate(Result.Failed, "%1$sDocument class name is missing after 'refers'.");
    public static final MessageTemplate error_DocumentClassNameIsMissingAfterTags = new MessageTemplate(Result.Failed, "%1$sDocument class name is missing after 'tags'.");
    // TODO: This should go away then we will turn to multiple inheritance of documents from single parent type
    public static final MessageTemplate error_TagsCanOnlyRefersSingleDocType = new MessageTemplate(Result.Failed, "%1$sType tags can only reference one type of documents.");

    public static final MessageTemplate debug_DocflowConfigLoadedSuccessfully = new MessageTemplate(Result.Ok, "Docflow configuration loaded successfully.");
    public static final MessageTemplate error_FailedToLoadDocflowConfig = new MessageTemplate(Result.Failed, "Failed to load 'docflow' configuration.");
    public static final MessageTemplate debug_FileLoadedSuccessfully = new MessageTemplate(Result.Ok, "File '%1$s' loaded successfully.");
    public static final MessageTemplate debug_InFile = new MessageTemplate(Result.Ok, "File '%1$s':");
    public static final MessageTemplate warning_InFile = new MessageTemplate(Result.Warning, "File '%1$s':");
    public static final MessageTemplate error_InFile = new MessageTemplate(Result.Failed, "File '%1$s':");

    public static final MessageTemplate error_FileExpectedToBegingWith = new MessageTemplate(Result.Failed, "Expected to begin with '%1$s'.");

    public static final MessageTemplate error_SubjInNotLinkedDocument = new MessageTemplate(Result.Failed, "Document '%1$s': Field named '%2$s' can only be used in document marked as linkedDocument.");
    public static final MessageTemplate error_LinkedDocumentSubjOfWrongType = new MessageTemplate(Result.Failed, "Document '%1$s': Field '%2$s' has non reference type, required by linkedDocument pattern.");
    public static final MessageTemplate error_LinkedDocumentMissingSubj = new MessageTemplate(Result.Failed, "Document '%1$s': Not defined 'subj' field requried by linkedDocument pattern.");
    public static final MessageTemplate error_StatesInSimpleDocument = new MessageTemplate(Result.Failed, "Document '%1$s': States specified for a simple document.");
    public static final MessageTemplate error_FieldFromGroupNotFound = new MessageTemplate(Result.Failed, "Document '%1$s': Fields group '%2$s': Field '%3$s' not found in the document.");
    public static final MessageTemplate warning_FieldNotMentionedInGroups = new MessageTemplate(Result.Ok, "Document '%1$s': Field '%2$s': Not mentioned in any fields group.");
    public static final MessageTemplate error_ActionHasReservedName = new MessageTemplate(Result.Failed, "Document '%1$s': Action '%2$s': Has reserved action name.");
    public static final MessageTemplate error_ActionMustBeAService = new MessageTemplate(Result.Failed, "Document '%1$s': Action '%2$s': Must be a service.");
    public static final MessageTemplate error_ActionOutOfFormAndOtherInTheSameTime = new MessageTemplate(Result.Failed, "Document '%1$s': Action '%2$s': 'outOfForm' and 'other' set to true in the same time.");
    public static final MessageTemplate error_ActionParameterHasUnknownType = new MessageTemplate(Result.Failed, "Document '%1$s': Action '%2$s': Parameter '%3$s' has unknown type '%3$s'.");
    public static final MessageTemplate error_ActionParameterNotSupported = new MessageTemplate(Result.Failed, "Document '%1$s': Action '%2$s': Parameters are not supported for built-in action.");
    public static final MessageTemplate error_FieldHasReservedName = new MessageTemplate(Result.Failed, "Document '%1$s': Field '%2$s': Has reserved field name.");
    public static final MessageTemplate error_FieldsGroupHasReservedName = new MessageTemplate(Result.Failed, "Document '%1$s': Fields group '%2$s': Has reserved fields group name.");
    public static final MessageTemplate error_SortOrderFieldNotFound = new MessageTemplate(Result.Failed, "Document '%1$s': Sort order '%2$s': Field '%3$s' not found.");

    public static final MessageTemplate error_EnumHasReservedName = new MessageTemplate(Result.Failed, "Enum '%1$s': Has reserved enum name.");
    public static final MessageTemplate error_EnumImplementationNotFound = new MessageTemplate(Result.Failed, "Enum '%1$s': Java code implementation not found.");
    public static final MessageTemplate error_EnumNotEnumType = new MessageTemplate(Result.Failed, "Enum '%1$s': Type with annotation @DocflowEnum must be an enum.");
    public static final MessageTemplate error_EnumImplementationContainsUnderfinedValue = new MessageTemplate(Result.Failed, "Enum '%1$s': Java code implementation contains underfined value '%2$s'.");
    public static final MessageTemplate error_EnumImplementationMissingValue = new MessageTemplate(Result.Failed, "Enum '%1$s': Java code implementation missing value '%2$s'.");

    public static final MessageTemplate error_TypeHasUnknownType = new MessageTemplate(Result.Failed, "Type '%1$s': Has unknown type '%2$s'.");
    public static final MessageTemplate error_TypeNotAnEnumType = new MessageTemplate(Result.Failed, "Type '%1$s': Type '%2$s' not an enum.");
    public static final MessageTemplate error_TypeNotAStructureType = new MessageTemplate(Result.Failed, "Type '%1$s': Type '%2$s' not a structure.");

    public static final MessageTemplate error_FieldHasUnknownType = new MessageTemplate(Result.Failed, "Document '%1$s': Field '%2$s': Has unknown type '%3$s'.");
    public static final MessageTemplate error_FieldHasNotSimpleType = new MessageTemplate(Result.Failed, "Document '%1$s': Field '%2$s': Type '%3$s' is not simple udt type (might be 'enum' or 'structure' is missing).");
    public static final MessageTemplate error_FieldNotAStructureType = new MessageTemplate(Result.Failed, "Document '%1$s': Field '%2$s': Type '%3$s' not a structure.");
    public static final MessageTemplate error_FieldNotASubtableType = new MessageTemplate(Result.Failed, "Document '%1$s': Field '%2$s': Type '%3$s' not a subtable.");
    public static final MessageTemplate error_FieldNotAnEnumType = new MessageTemplate(Result.Failed, "Document '%1$s': Field '%2$s': Type '%3$s' not an enum.");
    public static final MessageTemplate error_FieldMustHaveGivenAttributeSpecified = new MessageTemplate(Result.Failed, "Document '%1$s': Field '%2$s': Type '%3$s' must have attribute '%4$s' specified.");
    public static final MessageTemplate error_FieldMustNotHaveGivenAttributeSpecified = new MessageTemplate(Result.Failed, "Document '%1$s': Field '%2$s': Type '%3$s' must not have attribute '%4$s' specified.");
    public static final MessageTemplate error_FieldMustHasMinAttrBiggerThenMax = new MessageTemplate(Result.Failed, "Document '%1$s': Field '%2$s': 'min' attribute bigger then 'max'.");
    public static final MessageTemplate error_FieldMustHasMaxLengthAttrBiggerThenLength = new MessageTemplate(Result.Failed, "Document '%1$s': Field '%2$s': 'maxLenght' attribute bigger then 'length'.");
    public static final MessageTemplate error_FieldMustHasMinLengthAttrBiggerThenLength = new MessageTemplate(Result.Failed, "Document '%1$s': Field '%2$s': 'minLenght' attribute bigger then 'length'.");
    public static final MessageTemplate error_FieldMustHasMinLengthAttrBiggerThenMaxLength = new MessageTemplate(Result.Failed, "Document '%1$s': Field '%2$s': 'minLenght' attribute bigger then 'maxLength'.");

    public static final MessageTemplate error_FieldRefersUndefinedDocument = new MessageTemplate(Result.Failed, "Document '%1$s': Field '%2$s': Undefined document '%3$s' specified in refers.");

    public static final MessageTemplate error_DocumentStateContainsUndefinedFieldInView = new MessageTemplate(Result.Failed, "Document '%1$s': State '%2$s': Undefined field '%3$s' in view attribute.");
    public static final MessageTemplate error_DocumentStateContainsUndefinedFieldsGroupInView = new MessageTemplate(Result.Failed, "Document '%1$s': State '%2$s': Undefined fields group '%3$s' in view attribute.");
    public static final MessageTemplate error_DocumentStateContainsUndefinedFieldInUpdate = new MessageTemplate(Result.Failed, "Document '%1$s': State '%2$s': Undefined field '%3$s' in update attribute.");
    public static final MessageTemplate error_DocumentStateContainsUndefinedFieldsGroupInUpdate = new MessageTemplate(Result.Failed, "Document '%1$s': State '%2$s': Undefined fields group '%3$s' in update attribute.");

    public static final MessageTemplate error_RoleDocumentCreateActionNoAllowedForLinkedDocument = new MessageTemplate(Result.Failed, "Role '%1$s': Document '%2$s': Action '%3$s' not allowed for linked document.");
    public static final MessageTemplate error_RoleDocumentContainsUndefinedFieldsGroupInView = new MessageTemplate(Result.Failed, "Role '%1$s': Document '%2$s': Undefined fields group '%3$s' in view attribute.");
    public static final MessageTemplate error_RoleDocumentContainsUndefinedFieldInView = new MessageTemplate(Result.Failed, "Role '%1$s': Document '%2$s': Undefined field '%3$s' in view attribute.");
    public static final MessageTemplate error_RoleDocumentContainsUndefinedFieldsGroupInUpdate = new MessageTemplate(Result.Failed, "Role '%1$s': Document '%2$s': Undefined fields group '%3$s' in update attribute.");
    public static final MessageTemplate error_RoleDocumentContainsUndefinedFieldInUpdate = new MessageTemplate(Result.Failed, "Role '%1$s': Document '%2$s': Undefined field '%3$s' in update attribute.");
    public static final MessageTemplate error_RoleDocumentContainsUndefinedActionInActions = new MessageTemplate(Result.Failed, "Role '%1$s': Document '%2$s': Undefined action '%3$s' in actions attribute.");
    public static final MessageTemplate error_RoleDocumentMistakenlySpecifiesRecoverAction = new MessageTemplate(Result.Failed, "Role '%1$s': Document '%2$s': Not allowed to specify action '%3$s', since it inherited from action '%4$s'.");

    public static final MessageTemplate error_RoleDocumentHasReservedName = new MessageTemplate(Result.Failed, "Role '%1$s': Has reserved role name..");
    public static final MessageTemplate error_RoleDocumentContainsUndefinedDocument = new MessageTemplate(Result.Failed, "Role '%1$s': Document '%2$s' not found.");
    public static final MessageTemplate error_RoleDocumentFailedToInstantiateRelation = new MessageTemplate(Result.Failed, "Role '%1$s': Document '%2$s': Failed instantiate relation evaluator '%3$s'.");
    public static final MessageTemplate error_RoleDocumentNotDeclaredRelation = new MessageTemplate(Result.Failed, "Role '%1$s': Document '%2$s': Relation '%3$s' was not declared in the document.");

    public static final MessageTemplate error_DocumentStateTransitionNoSuchAction = new MessageTemplate(Result.Failed, "Document '%1$s': State '%2$s': Transition '%3$s': Action '%3$s' not in document actions.");
    public static final MessageTemplate error_DocumentStateTransitionRefersDocumentWideAction = new MessageTemplate(Result.Failed, "Document '%1$s': State '%2$s': Transition '%3$s': Document wide action '%4$s' must not be used for state transitions.");
    public static final MessageTemplate error_DocumentStateTransitionNoSuchEndState = new MessageTemplate(Result.Failed, "Document '%1$s': State '%2$s': Transition '%3$s': Refers unknown state '%4$s' as the end state.");
    public static final MessageTemplate error_DocumentStateConditionalTransitionHasNoCorrespondedUnconditionalTransition = new MessageTemplate(Result.Failed, "Document '%1$s': State '%2$s': Transition '%3$s': There is no unconditional transition for same action.");

    public static final MessageTemplate error_DocumentNewStateMustComeFirst = new MessageTemplate(Result.Failed, "Document '%1$s': State '%2$s': Implicite state '%3$s' must come first.");
    public static final MessageTemplate error_DocumentNewStateCanOnlyHasCreateTransitions = new MessageTemplate(Result.Failed, "Document '%1$s': State '%2$s': Only allowed to have '%3$s' transitions.");

    public static final MessageTemplate error_DocumentNoCorrespondedListQueryClass = new MessageTemplate(Result.Failed, "Document '%1$s': No corresponded list query class found '%2$s'.");
    public static final MessageTemplate error_DocumentNoCorrespondedModelClass = new MessageTemplate(Result.Failed, "Document '%1$s': No corresponded model class found '%2$s'.");
    public static final MessageTemplate error_DocumentNoCorrespondedActionsClass = new MessageTemplate(Result.Failed, "Document '%1$s': No corresponded actions class found '%2$s'.");
    public static final MessageTemplate error_DocumentNoCorrespondedRelationsClass = new MessageTemplate(Result.Failed, "Document '%1$s': No corresponded relations class found '%2$s'.");
    public static final MessageTemplate error_DocumentMethodNoSuchActionOrPreconditionInModel = new MessageTemplate(Result.Failed, "Document '%1$s': Method '%2$s': No such action or precondition declated in the document.");
    public static final MessageTemplate error_DocumentActionExpectedToHaveSignature = new MessageTemplate(Result.Failed, "Document '%1$s': Method '%2$s': Expected to have signature '%3$s'.");
    public static final MessageTemplate error_DocumentActionFailedToFindClass = new MessageTemplate(Result.Failed, "Document '%1$s': Action '%2$s': Failed to find class '%3$s'. Run of Docflow Compiler should help.");
    public static final MessageTemplate error_DocumentActionServiceActionMustBeImplemented = new MessageTemplate(Result.Failed, "Document '%1$s': Action '%2$s': Service action must be implemented by method '%4$s' in class '%3$s'.");
    public static final MessageTemplate error_DocumentPreUpdateRequiresParametersOnUpdate = new MessageTemplate(Result.Failed, "Document '%1$s': Method '%2$s': Requires parameters to be defind on 'update' action.");
    public static final MessageTemplate error_DocumentCorrespondedClassMustBeChildOfEntityBase = new MessageTemplate(Result.Failed, "Document '%1$s': Corresponded class '%2$s' must be derived from '%3$s'.");
    public static final MessageTemplate error_DocumentClassFieldNotDefinedInModel = new MessageTemplate(Result.Failed, "Document '%1$s': Class '%3$s': Field: '%4$s': Field '%2$s' not defined in model.");
    public static final MessageTemplate error_DocumentModelFieldNotFoundInClass = new MessageTemplate(Result.Failed, "Document '%1$s': Field '%2$s': Not found in class '%3$s' (or it's related classes).");
    public static final MessageTemplate error_DocumentQueryInvalidParameters = new MessageTemplate(Result.Failed, "Document '%1$s': Method '%2$s': Invalid method parameters.");
    public static final MessageTemplate error_DocumentRelationServiceActionMustBeImplemented = new MessageTemplate(Result.Failed, "Document '%1$s': Relation '%2$s': Relation must be implemented by method '%4$s' in class '%3$s'.");
    public static final MessageTemplate error_DocumentRelationExpectedToHaveSignature = new MessageTemplate(Result.Failed, "Document '%1$s': Method '%2$s': Expected to have signature '%3$s'.");
    public static final MessageTemplate error_DocumentPreconditionExpectedToHaveSignature = new MessageTemplate(Result.Failed, "Document '%1$s': Method '%2$s': Expected to have signature '%3$s'.");
    public static final MessageTemplate error_DocumentPreconditionMustBeImplemented = new MessageTemplate(Result.Failed, "Document '%1$s': Precondition '%2$s': Must be implemented by method '%4$s' in class '%3$s'.");
    public static final MessageTemplate error_DocumentIsNotMentionedInAnyRole = new MessageTemplate(Result.Warning, "Document '%1$s': Document is not mentioned in any role.");

    public static final MessageTemplate error_UDTypeHasUnknownType = new MessageTemplate(Result.Failed, "UDType '%1$s': Has unknown type '%2$s'.");
    public static final MessageTemplate error_UDTypeCyclingDependenciesWithTypes = new MessageTemplate(Result.Failed, "UDType '%1$s': Part of cycling dependencies with types: %2$s.");

    public static final MessageTemplate error_InvalidMessageDescription = new MessageTemplate(Result.Failed, "%1$sInvalid message description: '%2$s'.");
    public static final MessageTemplate error_UnknownMessageType = new MessageTemplate(Result.Failed, "%1$sInvalid message type: '%2$s'.");
    public static final MessageTemplate error_MessageUnknownParameter = new MessageTemplate(Result.Failed, "Message '%1$s': Unknwon parameter '%2$s'.");

    public static final MessageTemplate error_DocumentTemplateFieldNotFound = new MessageTemplate(Result.Failed, "Document '%1$s': Template '%2$s': Field '%3$s' not defined within document.");
    public static final MessageTemplate error_DocumentTemplateNotDefinedTemplate = new MessageTemplate(Result.Failed, "Document '%1$s': Template '%2$s': Field '%3$s': Template '%5$s' not defined in document '%4$s'.");
    public static final MessageTemplate error_DocumentTemplateImplicitTemplateOverride = new MessageTemplate(Result.Failed, "Document '%1$s': Template '%2$s': It's implicit template what cannot be overriden.");

    public static final MessageTemplate error_DocumentTemplateTabUnknownDocumentType = new MessageTemplate(Result.Failed, "Document '%1$s': Template '%2$s': Tab '%3$s': Unknown document type '%4$s'.");
    public static final MessageTemplate error_DocumentTemplateTabTemplateNotFoundInDocumentType = new MessageTemplate(Result.Failed, "Document '%1$s': Template '%2$s': Tab '%3$s': Template '%5$s' not found in the document type '%4$s'.");
    public static final MessageTemplate error_DocumentTemplateColumnHasUnexpectedName = new MessageTemplate(Result.Failed, "Document '%1$s': Template '%2$s': Column '%3$s': Has unexpected name.");
    public static final MessageTemplate error_DocumentTemplateColumnNoSuchField = new MessageTemplate(Result.Failed, "Document '%1$s': Template '%2$s': Column '%3$s': Field with given name is not found in the document.");
}