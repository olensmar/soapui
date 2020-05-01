/*
 * Copyright 2004-2019 SmartBear Software
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

package com.eviware.soapui.impl.rest.support.handlers;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.AbstractRequestConfig;
import com.eviware.soapui.impl.rest.RestRequest;
import com.eviware.soapui.impl.rest.support.MediaTypeHandler;
import com.eviware.soapui.impl.support.HttpUtils;
import com.eviware.soapui.impl.wsdl.submit.transports.http.HttpResponse;
import com.eviware.soapui.model.iface.Request;
import com.eviware.soapui.model.iface.TypedContent;
import com.eviware.soapui.support.JsonUtil;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.xml.XmlUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URL;

public class JsonMediaTypeHandler implements MediaTypeHandler {
    private static final String SINGLE_SLASH = "/";
    private static final String DOUBLE_SLASH = "//";

    public boolean canHandle(String contentType) {
        return JsonUtil.seemsToBeJsonContentType(contentType);
    }

    @Override
    public String createXmlRepresentation(HttpResponse response) {
        if (response == null) {
            return null;
        }
        return createXmlRepresentation(
                response,
                HttpUtils.isErrorStatus(response.getStatusCode()) ? "Fault" : "Response",
                createNamespaceUri(response));
    }

    public String createXmlRepresentation(TypedContent typedContent) {
        return createXmlRepresentation(typedContent, "Response", "json");
    }

    public String createXmlRepresentation(TypedContent typedContent, String rootNodeName, String uri) {
        try {
            if (typedContent == null) {
                return null;
            }
            String content = typedContent.getContentAsString();
            if (StringUtils.isNullOrEmpty(content)) {
                return null;
            }
            content = content.trim();
            JsonXmlSerializer serializer = createJsonXmlSerializer();

            serializer.setRootName(rootNodeName);
            serializer.setNamespace("", uri);

            return XmlUtils.prettyPrintXml(serializer.write(createJsonObject(content)));
        } catch (Exception e) {
            SoapUI.logError( e );
        }
        return "<xml/>";
    }

    public static String readOriginalUriFrom(Request request) {
        if (request instanceof RestRequest) {
            AbstractRequestConfig config = ((RestRequest) request).getConfig();
            String originalUri = config.getOriginalUri();
            // if URI contains unexpanded template parameters
            if (StringUtils.hasContent(originalUri)) {
                if (originalUri.contains("{")) {
                    return null;
                }
                int count = org.apache.commons.lang.StringUtils.countMatches(originalUri, DOUBLE_SLASH);
                if (count > 1) {
                    originalUri = originalUri.replaceAll("(?!(?<=http(|s):))" + DOUBLE_SLASH, SINGLE_SLASH);
                    config.setOriginalUri(originalUri);
                }
            }
            return originalUri;
        } else {
            return null;
        }
    }

    private static JsonXmlSerializer createJsonXmlSerializer() {
        JsonXmlSerializer serializer = new JsonXmlSerializer();
        serializer.setTypeHintsEnabled(false);
        return serializer;
    }

    private static String createNamespaceUri(HttpResponse response) {
        URL url = response.getURL();
        String originalUri = readOriginalUriFrom(response.getRequest());
        return originalUri != null ? originalUri : makeNamespaceUriFrom(url);
    }

    public static JsonNode createJsonObject(String content) {
        // remove nulls - workaround for bug in xmlserializer!?
        content = content.replaceAll("\\\\u0000", "");
        return JsonUtil.getValidJson(content);
    }

    public static String makeNamespaceUriFrom(URL url) {
        String path = url.getPath().replace(DOUBLE_SLASH, SINGLE_SLASH);
        return url.getProtocol() + ":" + DOUBLE_SLASH + url.getHost() + path;
    }
}
