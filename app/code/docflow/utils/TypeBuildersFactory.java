package code.docflow.utils;

import play.Play;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

/**
 * Factory that provides single binder object per type in thread safe manner.
 */
public abstract class TypeBuildersFactory<T extends Builder> {

    public final static class TypeDescription {
        public final Class type;
        public final Type[] parameters;

        private String asString;

        public TypeDescription(Type type) {
            checkNotNull(type, "type");
            if (type instanceof TypeVariable) {
                final TypeVariable typeVariable = (TypeVariable) type;
                this.type = (Class) typeVariable.getGenericDeclaration();
                this.parameters = null;
            }
            else if (type instanceof ParameterizedType) {
                final ParameterizedType parameterizedType = (ParameterizedType) type;
                this.type = (Class) parameterizedType.getRawType();
                this.parameters = parameterizedType.getActualTypeArguments();
            }
            else {
                this.type = (Class) type;
                this.parameters = null;
            }
            this.asString = type.toString();
        }

        public TypeDescription(Field field) {
            checkNotNull(field, "field");
            this.type = field.getType();
            final java.lang.reflect.Type genericType = field.getGenericType();
            this.parameters = (genericType instanceof ParameterizedType) ? ((ParameterizedType) genericType).getActualTypeArguments() : null;
            asString = genericType.toString();
        }

        public String toString() {
            return asString;
        }
    }

    private HashMap<String, T> map = new HashMap<String, T>();

    public void _resetForTest() {
        checkState(Play.mode == Play.Mode.DEV);
        synchronized (map) {
            // TODO: Switch to concurrency API
            map = new HashMap<String, T>();
        }
    }


    /**
     * Returns binder for given type of object.  It's thread safe method.  Binders stay stored.
     */
    public T get(Type type) {
        return get(new TypeDescription(type), false);
    }

    /**
     * Returns binder for given type of object.  It's thread safe method.  Binders stay stored.
     */
    public T get(Type type, boolean returnNotInitialized) {
        return get(new TypeDescription(type), returnNotInitialized);
    }

    /**
     * Returns binder for given type of field.  It's thread safe method.  Binders stay stored.
     */
    public T get(Field fld) {
        return get(new TypeDescription(fld), false);
    }

    /**
     * Returns binder for given type of field.  It's thread safe method.  Binders stay stored.
     */
    public T get(Field fld, boolean returnNotInitialized) {
        return get(new TypeDescription(fld), returnNotInitialized);
    }

    protected T get(TypeDescription typeDesc, boolean returnNotInitialized) {
        final String typeName = typeDesc.toString();
        T builder = map.get(typeName);
        if (builder == null) {
            // TODO: Switch to concurrency API
            synchronized (map) {
                builder = map.get(typeName);
                if (builder == null) {
                    builder = newInstance(typeDesc);
                    map.put(typeName, builder);
                    try {
                        builder.init();
                    } finally {
                        builder.initialized = true;
                        map.notifyAll();
                    }
                }
            }
        } else if (!builder.initialized && !returnNotInitialized) {
            try {
                synchronized (map) {
                    if (!builder.initialized)
                        map.wait();
                }
            } catch (InterruptedException e) {
            }
        }
        return builder;
    }

    public abstract T newInstance(TypeDescription typeDesc);
}
