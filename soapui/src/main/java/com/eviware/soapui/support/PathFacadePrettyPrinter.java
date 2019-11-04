package com.eviware.soapui.support;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.MinimalPrettyPrinter;

import java.io.IOException;

public class PathFacadePrettyPrinter extends MinimalPrettyPrinter {
    private boolean isArrayNode = false;

    public PathFacadePrettyPrinter(boolean isArrayNode) {
        this(DEFAULT_ROOT_VALUE_SEPARATOR.toString(), isArrayNode);
    }

    public PathFacadePrettyPrinter(String rootValueSeparator, boolean isArrayNode) {
        super(rootValueSeparator);
        this.isArrayNode = isArrayNode;
    }

    @Override
    public void writeArrayValueSeparator(JsonGenerator g) throws IOException {
        g.writeRaw(this._separators.getArrayValueSeparator());
        if (isArrayNode) {
            g.writeRaw(' ');
        }
    }
}
