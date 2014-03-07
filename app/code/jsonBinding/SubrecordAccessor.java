package code.jsonBinding;

import code.controlflow.Result;
import code.docflow.DocflowConfig;
import code.docflow.messages.GeneralMessages;
import code.jsonBinding.annotations.doc.JsonIndex;
import code.jsonBinding.annotations.doc.JsonPartOfStructure;
import code.utils.TypeBuildersFactory;
import play.exceptions.UnexpectedException;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import java.lang.reflect.Field;

import static com.google.common.base.Preconditions.checkState;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public final class SubrecordAccessor extends RecordAccessorCommon {

    public static final TypeBuildersFactory<SubrecordAccessor> factory = new TypeBuildersFactory<SubrecordAccessor>() {
        public SubrecordAccessor newInstance(TypeDescription typeDesc) {
            checkState(typeDesc.parameters == null);
            return new SubrecordAccessor(typeDesc.type);
        }
    };

    // TODO: Consider replacing by getter and setter.  This code while structures are load in EAGER mode.

    public Field fldFK;
    public Field fldI;

    protected SubrecordAccessor(final Class<?> type) {
        super(type);
    }

    @Override
    protected void init() {
        final Result result = new Result();

        super.init(result);

        Field tFK = null, tI = null;

        final Entity entityAnnotation = type.getAnnotation(Entity.class);
        if (entityAnnotation == null) {
            result.setCode(Result.Failed);
            result.addMsg(GeneralMessages.ClassHasNoEntityAnnotation, type.getName());
        }

        final JsonPartOfStructure partOfStructureAnnotation = type.getAnnotation(JsonPartOfStructure.class);
        final String fkField = partOfStructureAnnotation.fkField();
        if (fkField == null) {
            result.setCode(Result.Failed);
            result.addMsg(GeneralMessages.MissingFkFieldInJsonPartOfStructure, type.getName());
        }

        // TODO: Remove fkField from JsonPartOfStructures
        if (!fkField.equals(DocflowConfig.ImplicitFields.FK.toString())) {
            result.setCode(Result.Failed);
            result.addMsg(GeneralMessages.WrongFkFieldInJsonPartOfStructure, type.getName(), fkField);
        }

        try {
            type.getField(DocflowConfig.ImplicitFields.REV.toString());
            result.setCode(Result.Failed);
            result.addMsg(GeneralMessages.FieldRevNotAllowedInSubrecord, type.getName());
        } catch (NoSuchFieldException e) {
            // it's ok
        }

        try {
            tFK = type.getField(DocflowConfig.ImplicitFields.FK.toString());
            final ManyToOne manyToOneAnnotation = tFK.getAnnotation(ManyToOne.class);
            if (manyToOneAnnotation == null) {
                result.setCode(Result.Failed);
                result.addMsg(GeneralMessages.JsonPartOfStructureFkFieldHasNoManyToOneAnnotation, type.getName(), fkField);
            }
        } catch (NoSuchFieldException e) {
            result.setCode(Result.Failed);
            result.addMsg(GeneralMessages.JsonPartOfStructureFkFieldNotSpecifiedInClass, type.getName(), fkField);
        }

        try {
            tI = type.getField(DocflowConfig.ImplicitFields.I.toString());
            if (!tI.getType().isAssignableFrom(short.class)) {
                result.setCode(Result.Failed);
                result.addMsg(GeneralMessages.FieldHasWrongTypeInClass, type.getName(), tI.getName(), short.class.getName());
            }
            final JsonIndex jsonIndexAnnotation = tI.getAnnotation(JsonIndex.class);
            if (jsonIndexAnnotation == null) {
                result.setCode(Result.Failed);
                result.addMsg(GeneralMessages.JsonPartOfStructureIndexFieldHasNoJsonIndexAnnotation, type.getName(), DocflowConfig.ImplicitFields.I.toString());
            }
        } catch (NoSuchFieldException e) {
            result.setCode(Result.Failed);
            result.addMsg(GeneralMessages.JsonPartOfStructureIndexFieldNotSpecifiedInClass, type.getName(), DocflowConfig.ImplicitFields.I.toString());
        }

        fldFK = tFK;
        fldI = tI;

        if (result.isError())
            throw new UnexpectedException(result.combineMessages());
    }
}
