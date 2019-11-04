package com.eviware.soapui.support;

import java.util.Objects;

/**
 * A tuple containing a JSONPath expression and the value that has been retrieved using this expression.
 */
public class JsonPathValue {
    private final String path;
    private final Object value;

    public JsonPathValue(String path, Object value) {
        Objects.requireNonNull(path, "Path must not be null");
        Objects.requireNonNull(value, "Value must not be null");
        this.path = path;
        this.value = value;
    }

    public String getPath() {
        return path;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof JsonPathValue)) {
            return false;
        }
        JsonPathValue other = (JsonPathValue)obj;
        return this.path.equals(other.path) && this.value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return 17 * path.hashCode() + value.hashCode();
    }

    @Override
    public String toString() {
        return "JsonPathValue{" +
                "path='" + path + '\'' +
                ", value=" + value +
                '}';
    }
}
