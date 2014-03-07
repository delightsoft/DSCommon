package code.jsonBinding;

import code.controlflow.Result;
import code.docflow.messages.GeneralMessages;
import code.utils.Builder;
import play.Play;
import play.db.jpa.JPABase;
import play.exceptions.JavaExecutionException;

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
    public Method methodFindById;
    public Method methodSave;
    public Method methodDelete;

    protected void init(final Result result) {
        Field tId = null, tRev = null;
        Method tFindById = null, tSave = null, tDelete = null;

        if (type == proxyType && !type.isAnnotationPresent(Entity.class))
            result.addMsg(GeneralMessages.ClassHasNoEntityAnnotation, proxyType.getName());

        try {
            tId = proxyType.getField("id");
            if (!tId.getType().isAssignableFrom(long.class))
                result.addMsg(GeneralMessages.FieldHasWrongTypeInClass, proxyType.getName(), tId.getName(), long.class.getName());
            if (tId.getAnnotation(Id.class) == null)
                result.addMsg(GeneralMessages.FieldIdMustBeAnnotatedById, proxyType.getName());
            try {
                getId = proxyType.getMethod("getId");
            } catch (NoSuchMethodException e) {
                result.addMsg(GeneralMessages.MethodNotSpecifiedInClass, proxyType.getName(), "getId()");
            }
        } catch (NoSuchFieldException e) {
            result.addMsg(GeneralMessages.FieldNotSpecifiedInClass, proxyType.getName(), "id");
        }

        try {
            tFindById = type.getDeclaredMethod("findById", Object.class);
        } catch (NoSuchMethodException e) {
            result.addMsg(GeneralMessages.MethodNotSpecifiedInClass, type.getName(), "findById(Object id)");
        }

        try {
            tSave = proxyType.getMethod("save");
        } catch (NoSuchMethodException e) {
            result.addMsg(GeneralMessages.MethodNotSpecifiedInClass, proxyType.getName(), "save()");
        }

        try {
            tDelete = proxyType.getMethod("delete");
        } catch (NoSuchMethodException e) {
            result.addMsg(GeneralMessages.MethodNotSpecifiedInClass, proxyType.getName(), "save()");
        }

        methodFindById = tFindById;
        methodSave = tSave;
        methodDelete = tDelete;
    }

    public RecordAccessorCommon(final Class<?> type) {
        this.proxyType = type;
        String typeName = proxyType.getName();
        final int proxyPostfix = typeName.indexOf("_$$_javassist_");
        if (proxyPostfix < 0)
            this.type = proxyType;
        else
            this.type = Play.classloader.getClassIgnoreCase(typeName.substring(0, proxyPostfix));
    }

    public long getId(JPABase obj) {
        try {
            return (Long) getId.invoke(obj);
        } catch (InvocationTargetException e) {
            throw new JavaExecutionException(String.format("Failed on proxyType '%s'", proxyType.getName()), e.getCause());
        } catch (IllegalAccessException e) {
            throw new JavaExecutionException(String.format("Failed on proxyType '%s'", proxyType.getName()), e);
        }
    }

    public JPABase findById(long id) {
        try {
            return (JPABase) methodFindById.invoke(null, id);
        } catch (IllegalAccessException e) {
            throw new JavaExecutionException(e);
        } catch (InvocationTargetException e) {
            throw new JavaExecutionException(e);
        }
    }

    public JPABase newRecord() {
        try {
            return (JPABase) proxyType.newInstance();
        } catch (InstantiationException e) {
            throw new JavaExecutionException(e);
        } catch (IllegalAccessException e) {
            throw new JavaExecutionException(e);
        }
    }

    public Long save(JPABase obj) {
        try {
            methodSave.invoke(obj);
            return getId(obj);
        } catch (IllegalAccessException e) {
            throw new JavaExecutionException(e);
        } catch (InvocationTargetException e) {
            throw new JavaExecutionException(e);
        }
    }

    public void delete(Object obj) {
        try {
            methodDelete.invoke(obj);
        } catch (IllegalAccessException e) {
            throw new JavaExecutionException(e);
        } catch (InvocationTargetException e) {
            throw new JavaExecutionException(e);
        }
    }
}
