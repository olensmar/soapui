/*
 * SoapUI, Copyright (C) 2004-2019 SmartBear Software
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

import com.eviware.soapui.support.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class JsonXmlSerializerTest {

    private JsonXmlSerializer serializer;

    @Before
    public void setUp() throws Exception {
        serializer = new JsonXmlSerializer();
    }

    @Test
    public void serializesJsonWithVanillaNames() throws Exception {
        JsonNode parse = new JsonUtil().parseTrimmedText("{ name: 'Barack', surname: 'Obama', profession: 'president'}");

        assertThat(serializer.write(parse), is("<?xml version='1.0' encoding='UTF-8'?><o>" +
                "<name type=\"string\">Barack</name>" +
                "<surname type=\"string\">Obama</surname>" +
                "<profession type=\"string\">president</profession>" +
                "</o>"));
    }

    @Test
    public void serializesJsonWithDollarSign() throws Exception {
        JsonNode parse = new JsonUtil().parseTrimmedText("{ $: 'value' }");

        assertThat(serializer.write(parse), is("<?xml version='1.0' encoding='UTF-8'?><o>" +
                "<_ type=\"string\">value</_>" +
                "</o>"));
    }
}
