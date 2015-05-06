package code.docflow.jsonBinding;

import code.docflow.controlflow.Result;
import code.docflow.utils.Builder;
import play.Play;
import play.db.jpa.GenericModel;
import play.exceptions.JavaExecutionException;
import play.exceptions.UnexpectedException;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public abstract class RecordAccessorCommon extends Builder {

    public final Class<?> type;
    public final Class<?> proxyType;
    public Method getId;
    public Method getFullId;
    public Method methodFindById;
    public Method methodSave;
    public Method methodDelete;

    protected void init(final Result result) {
        Field tId = null, tRev = null;
        Method tFindById = null, tSave = null, tDelete = null;

        if (type == proxyType && !type.isAnnotationPresent(Entity.class))
            result.addMsg(Messages.ClassHasNoEntityAnnotation, proxyType.getName());

        try {
            tId = proxyType.getField("id");
            if (!tId.getType().isAssignableFrom(long.class))
                result.addMsg(Messages.FieldHasWrongTypeInClass, proxyType.getName(), tId.getName(), long.class.getName());
            if (tId.getAnnotation(Id.class) == null)
                result.addMsg(Messages.FieldIdMustBeAnnotatedById, proxyType.getName());
            try {
                getId = proxyType.getMethod("getId");
            } catch (NoSuchMethodException e) {
                result.addMsg(Messages.MethodNotSpecifiedInClass, proxyType.getName(), "getId()");
            }
        } catch (NoSuchFieldException e) {
            result.addMsg(Messages.FieldNotSpecifiedInClass, proxyType.getName(), "id");
        }

        try {
            getFullId = type.getDeclaredMethod("_fullId", Object.class);
        } catch (NoSuchMethodException e) {
            // nothing
        }

        try {
            tFindById = type.getDeclaredMethod("findById", Object.class);
        } catch (NoSuchMethodException e) {
            result.addMsg(Messages.MethodNotSpecifiedInClass, type.getName(), "findById(Object id)");
        }

        try {
            tSave = proxyType.getMethod("save");
        } catch (NoSuchMethodException e) {
            result.addMsg(Messages.MethodNotSpecifiedInClass, proxyType.getName(), "save()");
        }

        try {
            tDelete = proxyType.getMethod("delete");
        } catch (NoSuchMethodException e) {
            result.addMsg(Messages.MethodNotSpecifiedInClass, proxyType.getName(), "save()");
        }

        methodFindById = tFindById;
        methodSave = tSave;
        methodDelete = tDelete;
    }

    public RecordAccessorCommon(final Class<?> type) {
        this.proxyType = type;
        String typeName = proxyType.getName();
        // Note: different version of javassis (they linked to specific play versions) have different postfix for a classnames
        final int proxyPostfix = typeName.indexOf(Play.version.compareTo("1.3") > -1 ? "_$$_jvst" : "_$$_javassist_");
        if (proxyPostfix < 0)
            this.type = proxyType;
        else
            this.type = Play.classloader.getClassIgnoreCase(typeName.substring(0, proxyPostfix));
    }

    public long getId(GenericModel obj) {
        try {
            return (Long) getId.invoke(obj);
        } catch (InvocationTargetException e) {
            throw new JavaExecutionException(String.format("Failed on proxyType '%s'", proxyType.getName()), e.getCause());
        } catch (IllegalAccessException e) {
            throw new JavaExecutionException(String.format("Failed on proxyType '%s'", proxyType.getName()), e);
        }
    }

    public GenericModel findById(long id) {
        try {
            return (GenericModel) methodFindById.invoke(null, id);
        } catch (IllegalAccessException e) {
            throw new UnexpectedException(e);
        } catch (InvocationTargetException e) {
            throw new UnexpectedException(e.getCause());
        }
    }

    public GenericModel newRecord() {
        try {
            return (GenericModel) proxyType.newInstance();
        } catch (InstantiationException e) {
            throw new UnexpectedException(e);
        } catch (IllegalAccessException e) {
            throw new UnexpectedException(e);
        }
    }

    public Long save(GenericModel obj) {
        try {
            methodSave.invoke(obj);
            return getId(obj);
        } catch (IllegalAccessException e) {
            throw new UnexpectedException(e);
        } catch (InvocationTargetException e) {
            throw new UnexpectedException(e.getCause());
        }
    }

    public void delete(Object obj) {
        try {
            methodDelete.invoke(obj);
        } catch (IllegalAccessException e) {
            throw new UnexpectedException(e);
        } catch (InvocationTargetException e) {
            throw new UnexpectedException(e.getCause());
        }
    }
}
