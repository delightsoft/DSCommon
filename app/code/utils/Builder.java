package code.utils;

/**
 * Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
 */
public abstract class Builder {
    /**
     * Initialize builder.
     */
    protected abstract void init();

    boolean initialized;
}
