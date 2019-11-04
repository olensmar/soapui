package com.eviware.soapui.impl.rest.support.handlers;

import com.eviware.soapui.support.MessageSupport;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.xml.XmlUtils;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializable;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.fasterxml.jackson.databind.ser.std.SerializableSerializer;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.networknt.schema.JsonType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

import static com.eviware.soapui.support.JsonUtil.getStringRepresentationForValueNode;

public class    JsonXmlSerializableSerializer extends SerializableSerializer {
    private static final Logger log = LoggerFactory.getLogger(JsonXmlSerializableSerializer.class);
    private static final MessageSupport messages = MessageSupport.getMessages(JsonXmlSerializableSerializer.class);

    public static final String NAMESPACE_PREFIX = "xmlns";
    private static final String ARRAY_ELEMENT = "e";
    private static final String ATTRIBUTE_MARK = "@";
    private static final String TEXT_MARK = "#text";
    private static final String CLASS_TYPE_HINT = "class";
    private static final String TYPE_TYPE_HINT = "type";
    private JsonXmlSerializerProperties properties;
    private List<Map.Entry<String, JsonNode>> attributeEntriesNodes = new ArrayList<>();
    private List<Map.Entry<String, JsonNode>> elementEntriesNodes = new ArrayList<>();

    protected JsonXmlSerializableSerializer(JsonXmlSerializerProperties properties) {
        super();
        this.properties = properties;
    }

    @Override
    public void serialize(JsonSerializable value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (!(value instanceof JsonNode)) {
            value.serialize(gen, serializers);
        }
        JsonNode jsonNodeValue = (JsonNode) value;
        if (jsonNodeValue.isObject()) {
            serializeObjectNode((ObjectNode) jsonNodeValue, gen, serializers);
        } else if (jsonNodeValue.isArray()) {
            serializeArrayNode((ArrayNode) jsonNodeValue, gen, serializers);
        } else if (jsonNodeValue.isNull() && ((ToXmlGenerator) gen).inRoot()) {
            gen.writeStartObject();
            addRootAttributes((ToXmlGenerator) gen);
            gen.writeEndObject();
        } else if (jsonNodeValue.isValueNode()) {
            serializeValueNode((ValueNode) jsonNodeValue, gen, serializers);
        }
    }


    private void serializeValueNode(ValueNode value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        ToXmlGenerator xmlGenerator;
        if (gen instanceof ToXmlGenerator) {
            xmlGenerator = (ToXmlGenerator) gen;
        } else {
            value.serialize(gen, serializers);
            return;
        }
        xmlGenerator.writeStartObject(value);
        addTypeHintAttribute(xmlGenerator, value, xmlGenerator.inRoot());
        addAllAttributes(xmlGenerator, false);
        if (!value.isNull()) {
            xmlGenerator.setNextIsUnwrapped(true);
            xmlGenerator.writeFieldName("");
            writeStringValue(xmlGenerator, getStringRepresentationForValueNode(value));
            xmlGenerator.setNextIsUnwrapped(false);
        }
        xmlGenerator.writeEndObject();
    }

    private void writeStringValue(ToXmlGenerator xmlGenerator, String value) throws IOException {
        LinkedHashMap<Integer, Character> incorrectMap = new LinkedHashMap<>();
        xmlGenerator.writeString(XmlUtils.stripNonValidXMLCharacters(value, incorrectMap));
        StringBuilder sb = new StringBuilder();
        if (incorrectMap.size() > 0) {
            incorrectMap.forEach((integer, c) -> {
                if (sb.length() != 0) {
                    sb.append(", ");
                }
                sb.append(String.format("{pos:%d, value:0x%s}", integer, Integer.toHexString((int) c)));
            });
            log.warn(String.format(messages.get("JsonXmlSerializableSerializer.invalid.characters.list"), sb));
        }
    }

    private void serializeArrayNode(ArrayNode value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject(value);
        addTypeHintAttribute((ToXmlGenerator) gen, value, false);
        gen.writeFieldName(ARRAY_ELEMENT);
        int size = value.size();
        gen.writeStartArray(size);
        for (int i = 0; i < size; ++i) {
            JsonNode node = value.get(i);
            serialize(node, gen, provider);
        }
        gen.writeEndArray();
        gen.writeEndObject();
    }

    private void serializeObjectNode(ObjectNode value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        boolean isRootNode = ((ToXmlGenerator) gen).inRoot();
        gen.writeStartObject(value);
        Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
        if (fields.hasNext()) {
            addTypeHintAttribute((ToXmlGenerator) gen, value, isRootNode);
            sortAllNodes(fields, provider);
            addAllAttributes((ToXmlGenerator) gen, isRootNode);
            addAllElements((ToXmlGenerator) gen, provider);
        }
        gen.writeEndObject();
    }

    private void addAllElements(ToXmlGenerator gen, SerializerProvider provider) throws IOException {
        if (elementEntriesNodes.isEmpty()) {
            return;
        }
        for (Map.Entry<String, JsonNode> entryNode : elementEntriesNodes) {
            String fieldName = StringUtils.createXmlName(entryNode.getKey());
            JsonNode jsonNode = entryNode.getValue();
            if (StringUtils.sameString(fieldName, TEXT_MARK)) {
                gen.setNextIsUnwrapped(true);
                gen.writeFieldName(fieldName);
                writeStringValue(gen, jsonNode.isValueNode() ? getStringRepresentationForValueNode((ValueNode) jsonNode) : jsonNode.asText());
                gen.setNextIsUnwrapped(false);
                continue;
            }
            gen.writeFieldName(fieldName);
            serialize(jsonNode, gen, provider);
        }
    }

    private void addAllAttributes(ToXmlGenerator gen, boolean isRootNode) throws IOException {
        gen.setNextIsAttribute(true);
        for (Map.Entry<String, JsonNode> entryNode : attributeEntriesNodes) {
            String attributeName = entryNode.getKey().substring(1);
            if (attributeName != null && attributeName.startsWith(NAMESPACE_PREFIX) &&
                    isAttributeInProperties(attributeName, gen, isRootNode)) {
                continue;
            }
            String fieldName = StringUtils.createXmlName(entryNode.getKey().substring(1));
            gen.writeFieldName(fieldName);
            JsonNode node = entryNode.getValue();
            String attributeValue;
            if (node.isContainerNode()) {
                attributeValue = node.toString();
            } else if (node.isValueNode()) {
                attributeValue = getStringRepresentationForValueNode((ValueNode) node);
            } else {
                attributeValue = node.asText();
            }
            writeStringValue(gen, attributeValue);
        }
        addPropertiesAttributes(gen, isRootNode);
        gen.setNextIsAttribute(false);
    }

    private void addPropertiesAttributes(ToXmlGenerator gen, boolean isRootNode) throws IOException {
        if (isRootNode) {
            addRootAttributes(gen);
            return;
        }
        String elementName = getJsonContextName(gen);
        Map<String, String> attributes = properties.getElementAttributes(elementName);
        if (attributes == null) {
            return;
        }
        for (Map.Entry<String, String> attributeEntry : attributes.entrySet()) {
            String attributeName = StringUtils.createXmlName(attributeEntry.getKey());
            gen.writeFieldName(attributeName);
            String attributeValue = attributeEntry.getValue();
            writeStringValue(gen, attributeValue);
        }
    }

    private String getJsonContextName(ToXmlGenerator gen) {
        JsonStreamContext jsonContext = gen.getOutputContext().getParent();
        while (jsonContext.getCurrentName() == null) {
            jsonContext = jsonContext.getParent();
            if (jsonContext == null) {
                return null;
            }
        }
        return jsonContext.getCurrentName();
    }

    private boolean isAttributeInProperties(String attributeName, ToXmlGenerator gen, boolean isRootNode) {
        if (isRootNode) {
            return properties.getRootAttributes().containsKey(attributeName);
        }
        String elementName = getJsonContextName(gen);
        Map attributes = properties.getElementAttributes(elementName);
        return attributes != null ? attributes.containsKey(attributeName) : false;
    }

    private void addTypeHintAttribute(ToXmlGenerator gen, JsonNode node, boolean isRootNode) throws IOException {
        gen.setNextIsAttribute(true);
        if (node.isNull()) {
            gen.writeStringField(properties.addJsonPrefix("null"), "true");
        }
        if (!properties.isTypeHintsEnabled() || isRootNode) {
            gen.setNextIsAttribute(false);
            return;
        }
        if (node.isObject() || node.isNull()) {
            gen.writeStringField(CLASS_TYPE_HINT, JsonType.OBJECT.toString());
        } else if (node.isArray()) {
            gen.writeStringField(CLASS_TYPE_HINT, JsonType.ARRAY.toString());
        } else if (node.isNumber()) {
            gen.writeStringField(TYPE_TYPE_HINT, JsonType.NUMBER.toString());
        } else if (node.isBoolean()) {
            gen.writeStringField(TYPE_TYPE_HINT, JsonType.BOOLEAN.toString());
        } else {
            gen.writeStringField(TYPE_TYPE_HINT, JsonType.STRING.toString());
        }
        gen.setNextIsAttribute(false);
    }

    private void sortAllNodes(Iterator<Map.Entry<String, JsonNode>> fields, SerializerProvider provider) {
        boolean trimEmptyArray = (provider != null) &&
                !provider.isEnabled(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS);
        attributeEntriesNodes = new ArrayList<>();
        elementEntriesNodes = new ArrayList<>();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entryNode = fields.next();
            JsonNode node = entryNode.getValue();
            if (trimEmptyArray && node.isArray() && node.isEmpty(provider)) {
                continue;
            }
            if (entryNode.getKey().startsWith(ATTRIBUTE_MARK)) {
                attributeEntriesNodes.add(entryNode);
            } else {
                elementEntriesNodes.add(entryNode);
            }
        }
    }

    private void addRootAttributes(ToXmlGenerator gen) throws IOException {
        Map<String, String> rootAttributes = properties.getRootAttributes();
        if (rootAttributes.isEmpty()) {
            return;
        }
        gen.setNextIsAttribute(true);
        for (Map.Entry<String, String> attribute : rootAttributes.entrySet()) {
            String name = attribute.getKey();
            if (name != null && !name.startsWith(NAMESPACE_PREFIX)) {
                name = StringUtils.createXmlName(name);
            }
            gen.writeFieldName(name);
            writeStringValue(gen, attribute.getValue());
        }
        gen.setNextIsAttribute(false);
    }
}