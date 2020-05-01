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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.databind.ser.std.SerializableSerializer;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.eviware.soapui.support.JsonUtil.getStringRepresentationForValueNode;

/**
 * This class wraps read and write operations based on JSONPath.
 */
public class JsonPathFacade {

    private static final Logger log = LoggerFactory.getLogger(JsonPathFacade.class);

    private static final String DOLLAR_SIGN_PREFIX = "$";
    private static final String DOT = "\\.";
    private static final int INDEX_NOT_FOUND = -1;
    private static final String DOT_NOTATION_FORBIDDEN_CHARACTERS_REGEXP = "[ ]";
    private static final String CHARACTERS_FILTER_EXPRESSION = "\\=\\>\\<\\!";

    public static final String EMPTY_ARRAY_STRING = "[]";

    private String currentJson;
    private JsonNode jsonObject;
    private static ObjectMapper mapper;

    static {
        initObjectMapper();
    }

    public JsonPathFacade(String targetJson) {
        jsonObject = JsonUtil.getValidJson(targetJson);
        if (jsonObject == null) {
            throw new IllegalArgumentException("Invalid JSON: " + targetJson);
        }
        this.currentJson = targetJson;
    }

    private static void initObjectMapper() {
        mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        mapper.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
        SimpleModule module = new SimpleModule();
        module.addSerializer(new DeleteQuotesSerializer());
        mapper.registerModule(module);
    }

    public static ObjectMapper getPathFacadeObjectMapper() {
        return mapper;
    }

    public String readStringValue(String jsonPathExpression) {
        return readStringValue(jsonPathExpression, true);
    }

    public String readStringValue(String jsonPathExpression, boolean emptyStringIfNull) {
        JsonNode value = readObjectValue(jsonPathExpression);
        String stringValue = emptyStringIfNull ? "" : null;
        if (value == null) {
            return stringValue;
        }

        if (isNodeTypeNull(value)) {
            return EMPTY_ARRAY_STRING;
        }

        stringValue = getArrayNodeValue(value, stringValue, jsonPathExpression);

        if (StringUtils.isNotEmpty(stringValue)) {
            return stringValue;
        }

        try {
            PathFacadePrettyPrinter printer = new PathFacadePrettyPrinter(value.isArray());
            stringValue = mapper.writer(printer).writeValueAsString(value);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return stringValue;
    }

    private String getArrayNodeValue(JsonNode value, String defaultValue, String jsonPathExpression) {
        if (!value.isArray()) {
            return defaultValue;
        }

        if (!isSingleArrayElement(value)) {
            if (isArrayNotFormatted(jsonPathExpression)) {
                return value.toString();
            } else {
                return defaultValue;
            }
        }

        if (isSingleArrayElement(value) && isSingleElementArrayResult(jsonPathExpression)) {
            return value.get(0).asText();
        }

        return defaultValue;
    }

    private boolean isSingleArrayElement(JsonNode value) {
        return value.isArray() && value.size() == 1 && value.get(0).isValueNode();
    }

    private boolean isArrayNotFormatted(String jsonPathExpression) {
        if ((jsonPathExpression.indexOf("@") > 0) || (jsonPathExpression.indexOf("*") > 0) ||
                jsonPathExpression.indexOf("..") > 0 || hasUnionOperator(jsonPathExpression) ||
                hasArraySliceOperator(jsonPathExpression)) {
            return false;
        }

        return true;
    }

    private boolean hasUnionOperator(String jsonPathExpression) {
        Pattern unionOperatorPattern = Pattern.compile("\\[(\\d+,\\d*)+\\]");
        return unionOperatorPattern.matcher(jsonPathExpression).find();
    }

    private boolean hasArraySliceOperator(String jsonPathExpression) {
        Pattern arraySliceOperatorPattern = Pattern.compile("\\[(-?\\d+:-?\\d*)|(-?\\d*:-?\\d+):?\\d*\\]");
        return arraySliceOperatorPattern.matcher(jsonPathExpression).find();
    }

    private boolean isSingleElementArrayResult(String jsonPathExpression) {
        Pattern arrayIndexPattern = Pattern.compile("\\[\\d+\\]");
        if (jsonPathExpression.indexOf("@") < 0 && arrayIndexPattern.matcher(jsonPathExpression).find()) {
            return true;
        }
        return false;
    }

    public void writeValue(String jsonPathExpression, Object value) {
        String allowedPathExpression = wrapForbiddenCharactersInBrackets(addQuotes(jsonPathExpression));
        Configuration configuration = JsonUtil.getDefaultConfiguration();
        DocumentContext documentContext = JsonPath.using(configuration).parse(jsonObject);
        JsonNode newJson = documentContext.set(allowedPathExpression, value).json();
        currentJson = newJson.toString();
    }

    public JsonNode getJSON() {
        return jsonObject;
    }

    public String getCurrentJson() {
        return currentJson;
    }

    private String wrapForbiddenCharactersInBrackets(String jsonPathExpression) {
        Pattern quotedStringPattern = Pattern.compile("(?<!\\\\)'([^']|\\\\')+[^\\\\]'");
        Matcher quoteMatcher = quotedStringPattern.matcher(jsonPathExpression);
        int beginIndex = 0;
        int endIndex;
        StringBuffer allowedExpression = new StringBuffer();
        while (quoteMatcher.find()) {
            endIndex = quoteMatcher.start();
            String notMatched = jsonPathExpression.substring(beginIndex, endIndex);
            wrapUnquotedPart(notMatched, allowedExpression);
            allowedExpression.append(quoteMatcher.group());
            beginIndex = quoteMatcher.end();
        }
        if (beginIndex < jsonPathExpression.length()) {
            wrapUnquotedPart(jsonPathExpression.substring(beginIndex), allowedExpression);
        }
        if (allowedExpression.length() > 0) {
            return allowedExpression.toString();
        } else {
            return jsonPathExpression;
        }
    }

    private void wrapUnquotedPart(String notMatched, StringBuffer allowedExpression) {
        final String CHARACTERS_BUT_DOT_OR_LEFT_BRACKET = "[^" + CHARACTERS_FILTER_EXPRESSION + DOT + "\\(]*";
        final String CHARACTERS_BUT_DOT_OR_RIGHT_BRACKET = "[^" + CHARACTERS_FILTER_EXPRESSION + DOT + "\\)]*";
        final String CHARACTERS_QUOTED_EXPRESSION = "[\\=\\!\\>\\<]*\"[^\"]*\"[\\s\\)\\]*]";
        Pattern pattern = Pattern.compile("(?<!@)" + DOT + CHARACTERS_BUT_DOT_OR_LEFT_BRACKET + DOT_NOTATION_FORBIDDEN_CHARACTERS_REGEXP +
                CHARACTERS_BUT_DOT_OR_RIGHT_BRACKET + "(?=" + DOT + "?)" + "?");
        Pattern pattern_quots = Pattern.compile(CHARACTERS_QUOTED_EXPRESSION);

        Matcher m = pattern.matcher(notMatched);
        Matcher matcherQuots = pattern_quots.matcher(notMatched);
        List<MatcherData> quotsList = new ArrayList<>();
        while (matcherQuots.find()) {
            String group = matcherQuots.group();
            int start = matcherQuots.start();
            int end = matcherQuots.end();
            quotsList.add(new MatcherData(start, end, group));
        }

        int beginSize = allowedExpression.length();
        while (m.find()) {
            if (skipGroup(m, quotsList)) {
                continue;
            }
            String forbiddenDotPart = removeLastSpaces(m, notMatched);
            String firstBracketPart = forbiddenDotPart.replaceFirst(DOT, "['");
            String allBracketPart = firstBracketPart.replaceFirst(DOT, "']");
            if (allBracketPart.equals(firstBracketPart)) {
                allBracketPart = allBracketPart + "']";
            }
            m.appendReplacement(allowedExpression, allBracketPart);
        }
        if (allowedExpression.length() > beginSize) {
            m.appendTail(allowedExpression);
        } else {
            allowedExpression.append(notMatched);
        }
    }

    private String removeLastSpaces(Matcher matcher, String wholeText) {
        String group = matcher.group();
        int nextCharIndex = wholeText.length() > matcher.end() ? matcher.end() : wholeText.length() - 1;
        String nextChar = Character.toString(wholeText.charAt(nextCharIndex));
        if (nextChar.matches("[" + CHARACTERS_FILTER_EXPRESSION + "]")) {
            return group.replaceFirst("\\s+$", "");
        }
        return group;
    }

    private boolean skipGroup(Matcher matcher, List<MatcherData> quotedDataList) {
        return quotedDataList.stream().anyMatch(quotedData -> {
            return skipGroup(matcher, quotedData);
        });
    }

    private boolean skipGroup(Matcher matcher, MatcherData quotedData) {
        if (matcher.end() < quotedData.getStart() || matcher.start() > quotedData.getEnd()) {
            return false;
        }
        return true;
    }

    public JsonNode readObjectValue(String jsonPathExpression) {
        return readObjectValue(jsonPathExpression, false);
    }

    public JsonNode readObjectValue(String jsonPathExpression, boolean processArray) {
        String allowedPathExpression = wrapForbiddenCharactersInBrackets(formatJsonPath(jsonPathExpression));
        Configuration configuration = JsonUtil.getDefaultConfiguration();
        JsonPath jsonPath = JsonPath.compile(allowedPathExpression);
        JsonNode value;
        try {
            Object node = jsonPath.read(jsonObject, configuration);
            if( node instanceof JsonNode ) {
                value = (JsonNode) node;
            }
            else {
                value = new TextNode( String.valueOf( node ));
            }

            if (isNodeTypeNull(value)) {
                Pattern pattern = Pattern.compile("\\[\\d+\\]");
                Matcher m = pattern.matcher(allowedPathExpression);
                String lastIndexValue = null;
                int lastIndex = INDEX_NOT_FOUND;
                while (m.find()) {
                    lastIndexValue = m.group();
                    lastIndex = m.start();
                }
                if (lastIndex > INDEX_NOT_FOUND && allowedPathExpression.endsWith(lastIndexValue)) {
                    allowedPathExpression = allowedPathExpression.substring(0, lastIndex);
                    jsonPath = JsonPath.compile(allowedPathExpression);
                    value = jsonPath.read(jsonObject, configuration);
                    if (isNodeTypeNull(value)) {
                        value = JsonNodeFactory.instance.arrayNode();
                    } else {
                        String indexString = lastIndexValue.replaceAll("[\\D]", "");
                        int id = Integer.parseInt(indexString);
                        value = value.get(id);
                    }
                }
            }
        } catch (PathNotFoundException ex) {
            return null;
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return null;
        }

        if (value == null || value.getNodeType() == null) {
            return null;
        }

        if (processArray) {
            if (!isNodeTypeNull(value) && isSingleArrayElement(value) && isSingleElementArrayResult(jsonPathExpression)) {
                return value.get(0);
            }
        }

        return value;
    }

    public static boolean isNodeTypeNull(JsonNode node) {
        return node.isArray() && node.size() == 1 && node.get(0).getNodeType() == null;
    }


    public JsonNode nullIfNodeTypeNull(JsonNode node) {
        return node == null || isNodeTypeNull(node) ? null : node;
    }


    public Set<JsonPathValue> listLeafValues() {
        if (jsonObject.isObject()) {
            return getValuesFrom((ObjectNode) jsonObject, DOLLAR_SIGN_PREFIX);
        } else if (jsonObject.isArray()) {
            return getValuesFrom((ArrayNode) jsonObject, DOLLAR_SIGN_PREFIX);
        } else {
            log.warn("Can't get JSON values from instance of class " + jsonObject.getClass());
            return new TreeSet<>();
        }
    }

    private Set<JsonPathValue> getValuesFrom(ObjectNode jsonObject, String path) {
        Set<JsonPathValue> values = new LinkedHashSet<>();
        Iterator<Map.Entry<String, JsonNode>> fields = jsonObject.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            String nodePath = path + "." + field.getKey();
            addValue(values, value, nodePath);
        }
        return values;
    }

    private void addValue(Set<JsonPathValue> values, JsonNode value, String nodePath) {
        if (value.isObject()) {
            values.addAll(getValuesFrom((ObjectNode) value, nodePath));
        } else if (value.isArray()) {
            values.addAll(getValuesFrom((ArrayNode) value, nodePath));
        } else if (value.isValueNode()) {
            values.add(new JsonPathValue(nodePath, JsonUtil.getValueNodeValue((ValueNode) value)));
        }
    }

    private Set<JsonPathValue> getValuesFrom(ArrayNode array, String nodePath) {
        Set<JsonPathValue> values = new LinkedHashSet<>();
        int index = 0;
        for (JsonNode value : array) {
            addValue(values, value, nodePath + "[" + index + "]");
            index++;
        }
        return values;
    }

    private String addQuotes(String jsonPath) {
        jsonPath = jsonPath.trim();
        Matcher matcher = Pattern.compile("\\[(\\d*?[a-zA-Z][^\\]\\[]*)\\]").matcher(jsonPath);
        final int QUOTES_GROUP = 1;
        StringBuffer stringBuffer = new StringBuffer();
        try {
            while (matcher.find()) {
                matcher.appendReplacement(stringBuffer, "['" + matcher.group(QUOTES_GROUP) + "']");
            }
            matcher.appendTail(stringBuffer);
            return stringBuffer.toString();
        } catch (Exception e) {
            return jsonPath;
        }
    }

    private String formatJsonPath(String jsonPath) {
        return addQuotes(jsonPath);
    }


    private static class DeleteQuotesSerializer extends SerializableSerializer {
        @Override
        public void serialize(JsonSerializable value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (!(value instanceof JsonNode)) {
                value.serialize(gen, serializers);
            }
            JsonNode jsonNodeValue = (JsonNode) value;
            if (jsonNodeValue.isArray()) {
                serializeArrayNode((ArrayNode) jsonNodeValue, gen, serializers);
            } else if (jsonNodeValue.isValueNode()) {
                gen.writeRawValue(getStringRepresentationForValueNode((ValueNode) jsonNodeValue));
            } else {
                value.serialize(gen, serializers);
            }
        }

        private void serializeArrayNode(ArrayNode value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            int size = value.size();
            gen.writeStartArray(size);
            for (int i = 0; i < size; ++i) {
                JsonNode jsonNode = value.get(i);
                if (jsonNode.isValueNode()) {
                    gen.writeRawValue(getStringRepresentationForValueNode((ValueNode) jsonNode));
                } else {
                    jsonNode.serialize(gen, provider);
                }
            }
            gen.writeEndArray();
        }
    }

    private class MatcherData {
        private int start;
        private int end;
        private String group;

        public MatcherData(int start, int end, String group) {
            this.start = start;
            this.end = end;
            this.group = group;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public String getGroup() {
            return group;
        }
    }
}
