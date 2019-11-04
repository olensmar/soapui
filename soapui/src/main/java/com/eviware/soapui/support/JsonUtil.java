/*
 * Copyright 2004-2014 SmartBear Software
 *
 * Licensed under the EUPL, Version 1.1 or - as soon as they will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the Licence for the specific language governing permissions and limitations
 * under the Licence.
 */
package com.eviware.soapui.support;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Date;

import static org.apache.commons.lang3.StringUtils.containsIgnoreCase;

/**
 * Utilities for examining and processing content that is or can be JSON.
 */
public class JsonUtil {
    static final String WHILE_1 = "while(1);";
    static final String CLOSING_BRACKETS_WITH_COMMA = ")]}',";
    static final String CLOSING_BRACKETS = ")]}'";
    static final String EMPTY_FOR = "for(;;);";
    static final String D_PREFIXED = "{\"d\":";

    private static final String DEFAULT_INDENT = "   ";
    private static final String[] VULNERABILITY_TOKENS = {WHILE_1, CLOSING_BRACKETS_WITH_COMMA, CLOSING_BRACKETS, EMPTY_FOR};
    private static final Logger log = LoggerFactory.getLogger(JsonUtil.class);
    private static ObjectMapper mapper;
    private static XmlMapper xmlMapper;
    private static JacksonJsonNodeJsonProvider defaultNodeProvider;
    private static Configuration configuration;
    private static DefaultPrettyPrinter printer;

    static {
        initStaticVariables();
    }

    private static void initStaticVariables() {
        mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        mapper.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
        mapper.setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        SimpleModule module = new SimpleModule();
        module.addSerializer(Date.class, new DateObjectSerializer());
        mapper.registerModule(module);
        defaultNodeProvider = new PlainJavaJsonProvider(mapper);
        xmlMapper = new XmlMapper();
        configuration = Configuration.builder()
                .jsonProvider(defaultNodeProvider)
                .mappingProvider(new JacksonMappingProvider(mapper))
                .build();
        DefaultPrettyPrinter.Indenter indenter =
                new DefaultIndenter(DEFAULT_INDENT, DefaultIndenter.SYS_LF);
        printer = new DefaultPrettyPrinter();
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
    }

    public static ObjectMapper getMapper() {
        return mapper;
    }

    public static Configuration getDefaultConfiguration() {
        return configuration;
    }

    public static XmlMapper getXmlMapper() {
        return xmlMapper;
    }

    public static Object getValueNodeValue(ValueNode node) {
        return defaultNodeProvider.unwrap(node);
    }

    public static boolean isValidJson(String value) {
        return getValidJson(value) != null;
    }

    public static boolean isObjectOrArray(String value) {
        JsonNode node = getValidJson(value);
        return (node != null) && node.isContainerNode();
    }

    public static boolean isSimpleValue(String value) {
        JsonNode node = getValidJson(value);
        return  node != null && node.isValueNode();
    }

    public static JsonNode getJson(String value) throws IOException {
        return getJson(value, mapper);
    }

    public static JsonNode getValidJson(String value) {
        return getValidJson(value, mapper);
    }

    public static JsonNode getJsonFromXml(String xml) throws IOException {
        return getJson(xml, xmlMapper);
    }

    public static JsonNode getValidJsonFromXml(String xml) {
        return getValidJson(xml, xmlMapper);
    }

    public static String getStringRepresentationForValueNode(ValueNode value) {
        if (value.isBigDecimal()) {
            return value.decimalValue().toPlainString();
        } else {
            return value.asText();
        }
    }

    private static JsonNode getJson(String value, ObjectMapper mapper) throws IOException {
        JsonNode json = mapper.readTree(value);
        return json instanceof NullNode ? null : json;
    }

    private static JsonNode internalGetValidJson(String value, ObjectMapper mapper) throws IOException {
        JsonNode jsonNode = mapper.readTree(value);
        if (jsonNode == null && !(jsonNode instanceof NullNode)) {
            return null;
        }
        return jsonNode;
    }

    public static void ensureJsonValid(String value) throws IOException {
        internalGetValidJson(value, mapper);
    }

    @Nullable
    public static JsonNode getValidJson(String value, ObjectMapper mapper) {
        try {
            return internalGetValidJson(value, mapper);
        } catch (Exception e) {
            return null;
        }
    }

    public static String getJsonNodeText(JsonNode jsonNode) {
        return jsonNode != null ? jsonNode.asText() : null;
    }

    /**
     * This method and its name are somewhat awkward, but both stem from the fact that there are so many commonly used
     * content types for JSON.
     *
     * @param contentType the MIME type to examine
     * @return <code>true</code> if content type is non-null and contains either "json" or "javascript"
     */
    public static boolean seemsToBeJsonContentType(String contentType) {
        return containsIgnoreCase(contentType, "javascript") || containsIgnoreCase(contentType, "json");
    }

    public static boolean seemsToBeJson(String content) {
        if (StringUtils.isBlank(content)) {
            return false;
        }
        try {
            mapper.readTree(content);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static JsonNode parseTrimmedText(String text) throws IOException {
        if (text == null) {
            return null;
        }
        String trimmedText = removeVulnerabilityTokens(text).trim();
        return getJson(trimmedText);
    }

    /* package protected to make fine grained unit tests possible */

    public static String removeVulnerabilityTokens(String inputJsonString) {
        if (inputJsonString == null) {
            return null;
        }
        String outputString = inputJsonString.trim();
        for (String vulnerabilityToken : VULNERABILITY_TOKENS) {
            if (outputString.startsWith(vulnerabilityToken)) {
                outputString = outputString.substring(vulnerabilityToken.length()).trim();
            }
        }

        if (outputString.startsWith(D_PREFIXED) && outputString.endsWith("}")) {
            outputString = outputString.substring(D_PREFIXED.length(), outputString.length() - 1).trim();
        }
        return outputString;
    }

    public static String format(Object json) {
        if (json instanceof JsonNode) {
            try {
                return mapper.writer(printer).writeValueAsString(json);
            } catch (JsonProcessingException e) {
                log.error(e.getMessage(), e);
            }
        }
        return json.toString();
    }

    public String getFormattedStringValue(JsonNode value) {
        PathFacadePrettyPrinter printer = new PathFacadePrettyPrinter(value.isArray());
        String stringValue = "";
        try {
            stringValue = JsonPathFacade.getPathFacadeObjectMapper().writer(printer).writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
        }
        return stringValue;
    }
}
