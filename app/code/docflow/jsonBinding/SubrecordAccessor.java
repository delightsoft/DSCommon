package code.docflow.jsonBinding;

import code.docflow.controlflow.Result;
import code.docflow.compiler.enums.BuiltInFields;
import code.docflow.jsonBinding.annotations.doc.JsonIndex;
import code.docflow.jsonBinding.annotations.doc.JsonPartOfStructure;
import code.docflow.utils.EnumUtil;
import code.docflow.utils.TypeBuildersFactory;
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
            result.addMsg(Messages.ClassHasNoEntityAnnotation, type.getName());
        }

        final JsonPartOfStructure partOfStructureAnnotation = type.getAnnotation(JsonPartOfStructure.class);
        final String fkField = partOfStructureAnnotation.fkField();
        if (fkField == null) {
            result.setCode(Result.Failed);
            result.addMsg(Messages.MissingFkFieldInJsonPartOfStructure, type.getName());
        }

        // TODO: Remove fkField from JsonPartOfStructures
        if (!EnumUtil.isEqual(BuiltInFields.FK, fkField)) {
            result.setCode(Result.Failed);
            result.addMsg(Messages.WrongFkFieldInJsonPartOfStructure, type.getName(), fkField);
        }

        try {
            type.getField(BuiltInFields.REV.toString());
            result.setCode(Result.Failed);
            result.addMsg(Messages.FieldRevNotAllowedInSubrecord, type.getName());
        } catch (NoSuchFieldException e) {
            // it's ok
        }

        try {
            tFK = type.getField(BuiltInFields.FK.toString());
            final ManyToOne manyToOneAnnotation = tFK.getAnnotation(ManyToOne.class);
            if (manyToOneAnnotation == null) {
                result.setCode(Result.Failed);
                result.addMsg(Messages.JsonPartOfStructureFkFieldHasNoManyToOneAnnotation, type.getName(), fkField);
            }
        } catch (NoSuchFieldException e) {
            result.setCode(Result.Failed);
            result.addMsg(Messages.JsonPartOfStructureFkFieldNotSpecifiedInClass, type.getName(), fkField);
        }

        try {
            tI = type.getField(BuiltInFields.I.toString());
            if (!tI.getType().isAssignableFrom(short.class)) {
                result.setCode(Result.Failed);
                result.addMsg(Messages.FieldHasWrongTypeInClass, type.getName(), tI.getName(), short.class.getName());
            }
            final JsonIndex jsonIndexAnnotation = tI.getAnnotation(JsonIndex.class);
            if (jsonIndexAnnotation == null) {
                result.setCode(Result.Failed);
                result.addMsg(Messages.JsonPartOfStructureIndexFieldHasNoJsonIndexAnnotation, type.getName(), BuiltInFields.I.toString());
            }
        } catch (NoSuchFieldException e) {
            result.setCode(Result.Failed);
            result.addMsg(Messages.JsonPartOfStructureIndexFieldNotSpecifiedInClass, type.getName(), BuiltInFields.I.toString());
        }

        fldFK = tFK;
        fldI = tI;

        if (result.isError())
            throw new UnexpectedException(result.combineMessages());
    }
}
