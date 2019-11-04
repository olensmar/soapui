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
 *//*
 * Copyright 2002-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modifications by Ole Lensmar
 * Copyright 2012 SmartBear Software
 */

package com.eviware.soapui.impl.rest.support.handlers;

import com.eviware.soapui.support.JsonUtil;
import com.eviware.soapui.support.MessageSupport;
import com.eviware.soapui.support.xml.XmlUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Utility class for transforming JSON to XML an back.<br>
 * When transforming JSONObject and JSONArray instances to XML, this class will
 * add hints for converting back to JSON.<br>
 * Examples:<br>
 * <p/>
 * <pre>
 * JSONObject json = JSONObject.fromObject("{\"name\":\"json\",\"bool\":true,\"int\":1}");
 * String xml = new XMLSerializer().write( json );
 * <xmp><o class="object">
 *  <name type="string">json</name>
 *  <bool type="boolean">true</bool>
 *  <int type="number">1</int>
 *  </o></xmp>
 * </pre>
 * <p/>
 * <pre>
 * JSONArray json = JSONArray.fromObject("[1,2,3]");
 * String xml = new XMLSerializer().write( json );
 * <xmp><a class="array">
 *  <e type="number">1</e>
 *  <e type="number">2</e>
 *  <e type="number">3</e>
 *  </a></xmp>
 * </pre>
 */
public class JsonXmlSerializer {
    private static final String[] EMPTY_ARRAY = new String[0];
    private static final Logger log = LoggerFactory.getLogger(JsonXmlSerializer.class);
    private static final MessageSupport messages = MessageSupport.getMessages(JsonXmlSerializer.class);

    private JsonXmlSerializerProperties properties;
    /**
     * the name for an JSONArray Element
     */
    private String arrayName;
    /**
     * the name for an JSONArray's element Element
     */
    private String elementName;
    /**
     * list of properties to be expanded from child to parent
     */
    private String[] expandableProperties;
    private boolean forceTopLevelObject;
    /**
     * flag to be tolerant for incomplete namespace prefixes
     */
    private boolean namespaceLenient;
    /**
     * Map of namespaces per element
     */
    //private Map namespacesPerElement = new TreeMap();
    /**
     * the name for an JSONObject Element
     */
    private String objectName;
    /**
     * flag for trimming namespace prefix from element name
     */
    private boolean removeNamespacePrefixFromElements;
    /**
     * the name for the root Element
     */
    private String rootName;
    /**
     * Map of namespaces for root element
     */
    // private Map rootNamespace = new TreeMap();
    /**
     * flag for skipping namespaces while reading
     */
    private boolean skipNamespaces;
    /**
     * flag for skipping whitespace elements while reading
     */
    private boolean skipWhitespace;
    /**
     * flag for trimming spaces from string values
     */
    private boolean trimSpaces;
    /**
     * flag for type hints naming compatibility
     */
    private boolean typeHintsCompatibility;

    /**
     * Creates a new XMLSerializer with default options.<br>
     * <ul>
     * <li><code>objectName</code>: 'o'</li>
     * <li><code>arrayName</code>: 'a'</li>
     * <li><code>elementName</code>: 'e'</li>
     * <li><code>typeHinstEnabled</code>: true</li>
     * <li><code>typeHinstCompatibility</code>: true</li>
     * <li><code>namespaceLenient</code>: false</li>
     * <li><code>expandableProperties</code>: []</li>
     * <li><code>skipNamespaces</code>: false</li>
     * <li><code>removeNameSpacePrefixFromElement</code>: false</li>
     * <li><code>trimSpaces</code>: false</li>
     * </ul>
     */
    public JsonXmlSerializer() {
        properties = new JsonXmlSerializerProperties();
        setObjectName("o");
        setArrayName("a");
        setElementName("e");
        setTypeHintsEnabled(true);
        setTypeHintsCompatibility(true);
        setSkipNamespaces(false);
        setRemoveNamespacePrefixFromElements(false);
        setTrimSpaces(false);
        setExpandableProperties(EMPTY_ARRAY);
        setSkipNamespaces(false);
    }

    /**
     * Adds a namespace declaration to the root element.
     *
     * @param prefix namespace prefix
     * @param uri    namespace uri
     */
    public void addNamespace(String prefix, String uri) {
        addNamespace(prefix, uri, null);
    }

    /**
     * Adds a namespace declaration to an element.<br>
     * If the elementName param is null or blank, the namespace declaration will
     * be added to the root element.
     *
     * @param prefix      namespace prefix
     * @param uri         namespace uri
     * @param elementName name of target element
     */
    public void addNamespace(String prefix, String uri, String elementName) {
        if (StringUtils.isBlank(uri)) {
            return;
        }
        if (prefix == null) {
            prefix = "";
        } else {
            prefix = StringUtils.isNotBlank(prefix) ? ":" + prefix.trim() : prefix.trim();
        }

        String name = JsonXmlSerializableSerializer.NAMESPACE_PREFIX + prefix.trim();
        if (StringUtils.isBlank(elementName)) {
            properties.addRootAttribute(name, uri.trim());
        } else {
            properties.addElementAttribute(elementName, name, uri.trim());
        }
    }

    /**
     * Removes all namespaces declarations (from root an elements).
     */
    public void clearNamespaces() {
        properties.clearRootAttributes();
        properties.clearElementAttributes();
    }

    /**
     * Removes all namespace declarations from an element.<br>
     * If the elementName param is null or blank, the declarations will be
     * removed from the root element.
     *
     * @param elementName name of target element
     */
    public void clearNamespaces(String elementName) {
        if (StringUtils.isBlank(elementName)) {
            properties.clearRootAttributes();
        } else {
            properties.removeElementAttribute(elementName);
        }
    }

    /**
     * Returns the name used for JSONArray.
     */
    public String getArrayName() {
        return arrayName;
    }

    /**
     * Returns the name used for JSONArray elements.
     */
    public String getElementName() {
        return elementName;
    }

    /**
     * Returns a list of properties to be expanded from child to parent.
     */
    public String[] getExpandableProperties() {
        return expandableProperties;
    }

    /**
     * Returns the name used for JSONArray.
     */
    public String getObjectName() {
        return objectName;
    }

    /**
     * Returns the name used for the root element.
     */
    public String getRootName() {
        return rootName;
    }

    public boolean isForceTopLevelObject() {
        return forceTopLevelObject;
    }

    /**
     * Returns wether this serializer is tolerant to namespaces without URIs or
     * not.
     */
    public boolean isNamespaceLenient() {
        return namespaceLenient;
    }

    /**
     * Returns wether this serializer will remove namespace prefix from elements
     * or not.
     */
    public boolean isRemoveNamespacePrefixFromElements() {
        return removeNamespacePrefixFromElements;
    }

    /**
     * Returns wether this serializer will skip adding namespace declarations to
     * elements or not.
     */
    public boolean isSkipNamespaces() {
        return skipNamespaces;
    }

    /**
     * Returns wether this serializer will skip whitespace or not.
     */
    public boolean isSkipWhitespace() {
        return skipWhitespace;
    }

    /**
     * Returns wether this serializer will trim leading and trealing whitespace
     * from values or not.
     */
    public boolean isTrimSpaces() {
        return trimSpaces;
    }

    /**
     * Returns true if types hints will have a 'json_' prefix or not.
     */
    public boolean isTypeHintsCompatibility() {
        return properties.isTypeHintsCompatibility();
    }

    /**
     * Returns true if JSON types will be included as attributes.
     */
    public boolean isTypeHintsEnabled() {
        return properties.isTypeHintsEnabled();
    }

    /**
     * Creates a JSON value from a XML string.
     *
     * @param xml A well-formed xml document in a String
     * @return a JSONNull, JSONObject or JSONArray
     * @throws IOException if the conversion from XML to JSON can't be made for I/O or
     *                     format reasons.
     */
    public JsonNode read(String xml) throws IOException {
        JsonNode json = null;
        try {
            json = JsonUtil.getJsonFromXml(xml);
        } catch (Exception e) {
            throw e;
        }
        return json;
    }

    /**
     * Creates a JSON value from a File.
     *
     * @param file
     * @return a JSONNull, JSONObject or JSONArray
     * @throws IOException if the conversion from XML to JSON can't be made for I/O or
     *                     format reasons.
     */
    public JsonNode readFromFile(File file) throws Exception {
        if (file == null) {
            throw new Exception(messages.get("JsonXmlSerializer.file.read.error.null"));
        }
        if (!file.canRead()) {
            throw new Exception(messages.get("JsonXmlSerializer.file.read.error.cannotRead"));
        }
        if (file.isDirectory()) {
            throw new Exception(messages.get("JsonXmlSerializer.file.read.error.directory"));
        }
        try {
            return readFromStream(new FileInputStream(file));
        } catch (IOException ioe) {
            throw ioe;
        }
    }

    /**
     * Creates a JSON value from a File.
     *
     * @param path
     * @return a JSONNull, JSONObject or JSONArray
     * @throws Exception if the conversion from XML to JSON can't be made for I/O or
     *                   format reasons.
     */
    public JsonNode readFromFile(String path) throws IOException {
        return readFromStream(Thread.currentThread().getContextClassLoader().getResourceAsStream(path));
    }

    /**
     * Creates a JSON value from an input stream.
     *
     * @param stream
     * @return a JSONNull, JSONObject or JSONArray
     * @throws IOException if the conversion from XML to JSON can't be made for I/O or
     *                     format reasons.
     */
    public JsonNode readFromStream(InputStream stream) throws IOException {
        try {
            StringBuffer xml = new StringBuffer();
            BufferedReader in = new BufferedReader(new InputStreamReader(stream));
            String line = null;
            while ((line = in.readLine()) != null) {
                xml.append(line);
            }
            return read(xml.toString());
        } catch (IOException ioe) {
            throw ioe;
        }
    }

    /**
     * Removes a namespace from the root element.
     *
     * @param prefix namespace prefix
     */
    public void removeNamespace(String prefix) {
        removeNamespace(prefix, null);
    }

    /**
     * Removes a namespace from the root element.<br>
     * If the elementName is null or blank, the namespace will be removed from
     * the root element.
     *
     * @param prefix      namespace prefix
     * @param elementName name of target element
     */
    public void removeNamespace(String prefix, String elementName) {
        if (prefix == null) {
            prefix = "";
        } else {
            prefix = StringUtils.isNotBlank(prefix) ? ":" + prefix.trim() : prefix.trim();
        }
        String name = JsonXmlSerializableSerializer.NAMESPACE_PREFIX + prefix;
        if (StringUtils.isBlank(elementName)) {
            properties.removeRootAttribute(name);
        } else {
            properties.removeElementAttribute(elementName, name);
        }
    }

    /**
     * Sets the name used for JSONArray.<br>
     * Default is 'a'.
     */
    public void setArrayName(String arrayName) {
        this.arrayName = StringUtils.isBlank(arrayName) ? "a" : arrayName;
    }

    /**
     * Sets the name used for JSONArray elements.<br>
     * Default is 'e'.
     */
    public void setElementName(String elementName) {
        this.elementName = StringUtils.isBlank(elementName) ? "e" : elementName;
    }

    /**
     * Sets the list of properties to be expanded from child to parent.
     */
    public void setExpandableProperties(String[] expandableProperties) {
        this.expandableProperties = expandableProperties == null ? EMPTY_ARRAY : expandableProperties;
    }

    public void setForceTopLevelObject(boolean forceTopLevelObject) {
        this.forceTopLevelObject = forceTopLevelObject;
    }

    /**
     * Sets the namespace declaration to the root element.<br>
     * Any previous values are discarded.
     *
     * @param prefix namespace prefix
     * @param uri    namespace uri
     */
    public void setNamespace(String prefix, String uri) {
        setNamespace(prefix, uri, null);
    }

    /**
     * Adds a namespace declaration to an element.<br>
     * Any previous values are discarded. If the elementName param is null or
     * blank, the namespace declaration will be added to the root element.
     *
     * @param prefix      namespace prefix
     * @param uri         namespace uri
     * @param elementName name of target element
     */
    public void setNamespace(String prefix, String uri, String elementName) {
        if (StringUtils.isBlank(uri)) {
            return;
        }
        if (prefix == null) {
            prefix = "";
        } else {
            prefix = StringUtils.isNotBlank(prefix) ? ":" + prefix.trim() : prefix.trim();
        }
        String name = JsonXmlSerializableSerializer.NAMESPACE_PREFIX + prefix.trim();
        if (StringUtils.isBlank(elementName)) {
            properties.addRootAttribute(name, uri.trim());
        } else {
            properties.addElementAttribute(elementName, name, uri.trim());
        }
    }

    /**
     * Sets wether this serializer is tolerant to namespaces without URIs or not.
     */
    public void setNamespaceLenient(boolean namespaceLenient) {
        this.namespaceLenient = namespaceLenient;
    }

    /**
     * Sets the name used for JSONObject.<br>
     * Default is 'o'.
     */
    public void setObjectName(String objectName) {
        this.objectName = StringUtils.isBlank(objectName) ? "o" : objectName;
    }

    /**
     * Sets if this serializer will remove namespace prefix from elements when
     * reading.
     */
    public void setRemoveNamespacePrefixFromElements(boolean removeNamespacePrefixFromElements) {
        this.removeNamespacePrefixFromElements = removeNamespacePrefixFromElements;
    }

    /**
     * Sets the name used for the root element.
     */
    public void setRootName(String rootName) {
        this.rootName = StringUtils.isBlank(rootName) ? null : rootName;
    }

    /**
     * Sets if this serializer will skip adding namespace declarations to
     * elements when reading.
     */
    public void setSkipNamespaces(boolean skipNamespaces) {
        this.skipNamespaces = skipNamespaces;
    }

    /**
     * Sets if this serializer will skip whitespace when reading.
     */
    public void setSkipWhitespace(boolean skipWhitespace) {
        this.skipWhitespace = skipWhitespace;
    }

    /**
     * Sets if this serializer will trim leading and trealing whitespace from
     * values when reading.
     */
    public void setTrimSpaces(boolean trimSpaces) {
        this.trimSpaces = trimSpaces;
    }

    /**
     * Sets wether types hints will have a 'json_' prefix or not.
     */
    public void setTypeHintsCompatibility(boolean typeHintsCompatibility) {
        properties.setTypeHintsCompatibility(typeHintsCompatibility);
    }

    /**
     * Sets wether JSON types will be included as attributes.
     */
    public void setTypeHintsEnabled(boolean typeHintsEnabled) {
        properties.setTypeHintsEnabled(typeHintsEnabled);
    }

    /**
     * Writes a JSON value into a XML string with UTF-8 encoding.<br>
     *
     * @param json The JSON value to transform
     * @return a String representation of a well-formed xml document.
     * @throws Exception if the conversion from JSON to XML can't be made for I/O
     *                   reasons.
     */
    public String write(JsonNode json) {
        return write(json, null);
    }

    /**
     * Writes a JSON value into a XML string with an specific encoding.<br>
     * If the encoding string is null it will use UTF-8.
     *
     * @param json     The JSON value to transform
     * @param encoding The xml encoding to use
     * @return a String representation of a well-formed xml document.
     * @throws Exception if the conversion from JSON to XML can't be made for I/O
     *                   reasons or the encoding is not supported.
     */
    public String write(JsonNode json, String encoding) {
        try {
            XmlMapper xmlMapper = new XmlMapper();
            xmlMapper.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);
            if (json == null) {
                return XmlUtils.EMPTY_XML;
            }
            PropertyName rootElement = getRootElementProperty(json);
            xmlMapper.setSerializerProvider(new JsonXmlSerializerProvider(properties));
            String xml = xmlMapper.writer().withRootName(rootElement).writeValueAsString(json);
            return xml;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return "";
    }

    private PropertyName getRootElementProperty(JsonNode json) {
        String rootName;
        if (json.isArray()) {
            rootName = getRootName() == null ? getArrayName() : getRootName();
            return new PropertyName(rootName);
        }

        rootName = getRootName() == null ? getObjectName() : getRootName();

        if (json.isNull()) {
            properties.clearRootAttributes();
            properties.addRootAttribute(properties.addJsonPrefix("null"), "true");
        }

        return new PropertyName(rootName);

    }
}