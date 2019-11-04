package com.eviware.soapui.impl.rest.support.handlers;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.ser.DefaultSerializerProvider;
import com.fasterxml.jackson.databind.ser.SerializerFactory;
import com.fasterxml.jackson.dataformat.xml.ser.XmlSerializerProvider;
import com.fasterxml.jackson.dataformat.xml.util.XmlRootNameLookup;

public class JsonXmlSerializerProvider extends XmlSerializerProvider {
    private JsonXmlSerializerProperties properties;

    public JsonXmlSerializerProvider(JsonXmlSerializerProperties properties) {
        super(new XmlRootNameLookup());
        this.properties = properties;
    }

    public JsonXmlSerializerProvider(XmlSerializerProvider src, SerializationConfig config,
                                     SerializerFactory f, JsonXmlSerializerProperties properties) {
        super(src, config, f);
        this.properties = properties;
    }

    @Override
    public DefaultSerializerProvider createInstance(SerializationConfig config, SerializerFactory jsf) {
        return new JsonXmlSerializerProvider(this, config, jsf, properties);
    }

    @Override
    protected JsonSerializer<Object> _createAndCacheUntypedSerializer(Class<?> rawType)
            throws JsonMappingException {
        JavaType fullType = _config.constructType(rawType);
        JsonSerializer<Object> ser;
        try {
            if (JsonNode.class.isAssignableFrom(rawType)) {
                ser = (JsonSerializer) new JsonXmlSerializableSerializer(properties);
            } else {
                ser = _createUntypedSerializer(fullType);
            }
        } catch (IllegalArgumentException iae) {
            ser = null;
            reportMappingProblem(iae, iae.getMessage());
        }

        if (ser != null) {
            _serializerCache.addAndResolveNonTypedSerializer(rawType, fullType, ser, this);
        }
        return ser;
    }
}
