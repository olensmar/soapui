package com.eviware.soapui.impl.rest.support.handlers;

import java.util.HashMap;
import java.util.Map;

public class JsonXmlSerializerProperties {
    private static final String JSON_PREFIX = "json_";
    private Map<String, Map<String, String>> elementAttributes = new HashMap<>();
    private Map<String, String> rootAttributes = new HashMap<>();
    private boolean typeHintsEnabled;
    private boolean typeHintsCompatibility;

    public Map<String, String> getRootAttributes() {
        return rootAttributes;
    }

    public Map<String, Map<String, String>> getElementAttributes() {
        return elementAttributes;
    }

    public Map<String, String> getElementAttributes(String elementName) {
        return elementAttributes.get(elementName);
    }

    public void addRootAttribute(String name, String value) {
        rootAttributes.put(name, value);
    }

    public void addElementAttribute(String elementName, String name, String value) {
        Map attributes = elementAttributes.get(elementName);
        if (attributes == null) {
            attributes = new HashMap<>();
            attributes.put(name, value);
            elementAttributes.put(elementName, attributes);
        } else {
            attributes.put(name, value);
        }
    }

    public void removeRootAttribute(String name) {
        rootAttributes.remove(name);
    }

    public void removeElementAttribute(String elementName) {
        elementAttributes.remove(elementName);
    }

    public void removeElementAttribute(String elementName, String name) {
        Map attributes = elementAttributes.get(elementName);
        if (attributes != null) {
            if (attributes.size() == 1 && attributes.get(name) != null) {
                elementAttributes.remove(elementName);
            }
            attributes.remove(name);
        }
    }

    public void clearRootAttributes() {
        rootAttributes.clear();
    }

    public void clearElementAttributes() {
        elementAttributes.clear();
    }

    public boolean isTypeHintsEnabled() {
        return typeHintsEnabled;
    }

    public void setTypeHintsEnabled(boolean typeHintsEnabled) {
        this.typeHintsEnabled = typeHintsEnabled;
    }

    public boolean isTypeHintsCompatibility() {
        return typeHintsCompatibility;
    }

    public void setTypeHintsCompatibility(boolean typeHintsCompatibility) {
        this.typeHintsCompatibility = typeHintsCompatibility;
    }

    public String addJsonPrefix(String str) {
        if (!isTypeHintsCompatibility()) {
            return JSON_PREFIX + str;
        }
        return str;
    }
}
