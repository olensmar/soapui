/*
 *  SoapUI, copyright (C) 2004-2014 smartbear.com
 *
 *  SoapUI is free software; you can redistribute it and/or modify it under the
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  SoapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */
package com.eviware.soapui.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;

public class PlainJavaJsonProvider extends JacksonJsonNodeJsonProvider {

    public PlainJavaJsonProvider(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public void setArrayIndex(Object array, int index, Object newValue) {
        if (!isArray(array)) {
            throw new UnsupportedOperationException();
        } else {
            ArrayNode arrayNode = (ArrayNode) array;
            removeDefaultNullNode(arrayNode);
            if (index == arrayNode.size()) {
                arrayNode.add(createJsonElement(newValue));
            } else {
                arrayNode.set(index, createJsonElement(newValue));
            }
        }
    }

    private void removeDefaultNullNode(ArrayNode node) {
        if (node.size() == 1 && node.get(0).getNodeType() == null) {
            node.remove(0);
        }
    }

    @Override
    public Object createArray() {
        ArrayNode node = JsonNodeFactory.instance.arrayNode();
        node.add(new NullPathNode());
        return node;
    }

    @Override
    public Object getArrayIndex(Object obj, int idx) {
        Object arrayElement = super.getArrayIndex(obj, idx);
        return arrayElement != null ? arrayElement : new NullPathNode();
    }

    private JsonNode createJsonElement(Object o) {
        if (o != null) {
            return o instanceof JsonNode ? (JsonNode) o : this.objectMapper.valueToTree(o);
        } else {
            return null;
        }
    }

    @Override
    public Object unwrap(Object o) {
        if (o == null || o instanceof NullPathNode) {
            return null;
        } else {
            return super.unwrap(o);
        }
    }
}
