package code.docflow.utils;

import play.Play;

import java.util.HashMap;

import static com.google.common.base.Preconditions.checkState;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */

/**
 * Factory that provides single tmplRoot object per roles list string in thread safe manner.
 */
public abstract class TemplatesBuildersFactory<T extends Builder> {

    private HashMap<String, T> map = new HashMap<String, T>();

    public void _resetForTest() {
        checkState(Play.mode == Play.Mode.DEV);
        synchronized (map) {
            // TODO: Switch to concurrency API
            map = new HashMap<String, T>();
        }
    }

    public T get(String roles) {
        T builder = map.get(roles);
        if (builder == null) {
            // TODO: Switch to concurrency API
            synchronized (map) {
                builder = map.get(roles);
                if (builder == null) {
                    builder = newInstance(roles);
                    map.put(roles, builder);
                    try {
                        builder.init();
                    } finally {
                        builder.initialized = true;
                        map.notifyAll();
                    }
                }
            }
        } else if (!builder.initialized) {
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

    public abstract T newInstance(String roles);
}
