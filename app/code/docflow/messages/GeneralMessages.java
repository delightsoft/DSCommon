package code.docflow.messages;

import code.controlflow.Result;

/**
 * Шаблоны общих сообщений.
 */
public class GeneralMessages {
    // Reflection
    public static final MessageTemplate FieldHasWrongTypeInClass = new MessageTemplate(Result.Failed, "Field '%2$s' has wrong type in class '%1$s'. Excepted type '%3$s'.");
    public static final MessageTemplate FieldNotSpecifiedInClass = new MessageTemplate(Result.Failed, "Field '%2$s' not specified in type '%1$s'.");
    public static final MessageTemplate MethodNotSpecifiedInClass = new MessageTemplate(Result.Failed, "Method '%2$s' not specified in type '%1$s'.");

    public static final MessageTemplate ClassMustBeDerivedFromEntityBase = new MessageTemplate(Result.Failed, "Class %1$s must implement 'common.models.IDocument'.");
    public static final MessageTemplate ClassMustBeDerivedFromHistoryBase = new MessageTemplate(Result.Failed, "Class %1$s must be derived from 'common.models.HistoryBase'.");
    public static final MessageTemplate FieldIdMustBeAnnotatedById = new MessageTemplate(Result.Failed, "Field 'id' must be annotated by @Id in class '%1$s'.");
    public static final MessageTemplate FieldRevMustBeAnnotatedByVersion = new MessageTemplate(Result.Failed, "Field 'rev' must be annotated by @Version in class '%1$s'.");
    public static final MessageTemplate ClassHasNoEntityAnnotation = new MessageTemplate(Result.Failed, "Class '%1$s' has no @Entity annotation.");
    public static final MessageTemplate FieldRevNotAllowedInSubrecord = new MessageTemplate(Result.Failed, "Field 'rev' not allowed in class '%1$s' with @JsonPartOfStructure annotation.");
    public static final MessageTemplate MissingFkFieldInJsonPartOfStructure = new MessageTemplate(Result.Failed, "Missing fkField value in @JsonPartOfStructure in type '%1$s'.");
    public static final MessageTemplate WrongFkFieldInJsonPartOfStructure = new MessageTemplate(Result.Failed, "Wrong fkField value '%2$s' in @JsonPartOfStructure in type '%1$s'.");
    public static final MessageTemplate JsonPartOfStructureIndexFieldNotSpecifiedInClass = new MessageTemplate(Result.Failed, "@JsonPartOfStructure index field 'i' annotated by @JsonIndex no not specified in type '%1$s'.");
    public static final MessageTemplate JsonPartOfStructureIndexFieldHasNoJsonIndexAnnotation = new MessageTemplate(Result.Failed, "@JsonPartOfStructure index field 'i' has no @JsonIndex annotation in type '%1$s'.");
    public static final MessageTemplate JsonPartOfStructureFkFieldNotSpecifiedInClass = new MessageTemplate(Result.Failed, "@JsonPartOfStructure.fkField '%2$s' not specified in type '%1$s'.");
    public static final MessageTemplate JsonPartOfStructureFkFieldHasNoManyToOneAnnotation = new MessageTemplate(Result.Failed, "@JsonPartOfStructure.fkField '%2$s' has no @ManyToOne annotation in type '%1$s'.");
    public static final MessageTemplate JsonFieldHasWrongValue = new MessageTemplate(Result.InvalidArguments, "Field '%1$s' has wrong value '%2$s'.");

    // Db
    public static final MessageTemplate DbFailedToConnect = new MessageTemplate(Result.Failed, "Db failed with message: %1$s.");

    // Http
    public static final MessageTemplate HttpMissingParam = new MessageTemplate(Result.Failed, "Missing parameter '%1$s'.");
    public static final MessageTemplate HttpParamHasIncorrectValue = new MessageTemplate(Result.Failed, "Parameter '%1$s' has incorrect value.");
}
