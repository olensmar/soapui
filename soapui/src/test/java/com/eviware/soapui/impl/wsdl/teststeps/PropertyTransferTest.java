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

package com.eviware.soapui.impl.wsdl.teststeps;

import com.eviware.soapui.config.PropertyTransferConfig;
import com.eviware.soapui.impl.wsdl.WsdlSubmitContext;
import com.eviware.soapui.model.support.DefaultTestStepProperty;
import com.eviware.soapui.model.testsuite.TestProperty;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class PropertyTransferTest {

    private PropertyTransfer transfer;
    private DefaultTestStepProperty sourceProperty;
    private DefaultTestStepProperty targetProperty;
    private WsdlSubmitContext submitContext;

    @Before
    public void setUp() throws Exception {
        sourceProperty = new DefaultTestStepProperty("source", null);
        targetProperty = new DefaultTestStepProperty("target", null);
        transfer = new PropertyTransfer(null, PropertyTransferConfig.Factory.newInstance()) {
            @Override
            public TestProperty getSourceProperty() {
                return sourceProperty;
            }

            @Override
            public TestProperty getTargetProperty() {
                return targetProperty;
            }
        };
        submitContext = mock(WsdlSubmitContext.class);
    }

    @Test
    public void testStringToStringTransfer() throws Exception {
        sourceProperty.setValue("Test");

        transfer.transferProperties(submitContext);

        assertThat(targetProperty.getValue(), is("Test"));
    }

    @Test
    public void testStringToXmlTransfer() throws Exception {
        sourceProperty.setValue("audi");
        targetProperty.setValue("<bil><name>bmw</name></bil>");

        transfer.setTargetPath("//name/text()");
        transfer.transferProperties(submitContext);
        assertThat(targetProperty.getValue(), is("<bil><name>audi</name></bil>"));

        targetProperty.setValue("<bil><name test=\"test\">bmw</name></bil>");
        transfer.transferProperties(submitContext);
        assertThat(targetProperty.getValue(), is("<bil><name test=\"test\">audi</name></bil>"));

        transfer.setTargetPath("//name/@test");
        transfer.transferProperties(submitContext);
        assertThat(targetProperty.getValue(), is("<bil><name test=\"audi\">audi</name></bil>"));
    }

    @Test
    public void testXmlToStringTransfer() throws Exception {
        sourceProperty.setValue("<bil><name>audi</name></bil>");
        targetProperty.setValue("");
        transfer.setSourcePath("//name/text()");

        transfer.transferProperties(submitContext);
        assertThat(targetProperty.getValue(), is("audi"));
    }

    @Test
    public void testXmlToStringNullTransfer() throws Exception {
        sourceProperty.setValue("<bil></bil>");
        targetProperty.setValue("");

        transfer.setSourcePath("//name/text()");

        transfer.transferProperties(submitContext);
        assertThat(targetProperty.getValue(), is(nullValue()));
    }

    @Test
    public void testTextXmlToXmlTransfer() throws Exception {
        sourceProperty.setValue("<bil><name>audi</name></bil>");
        targetProperty.setValue("<bil><name>bmw</name></bil>");

        transfer.setSourcePath("//name/text()");
        transfer.setTargetPath("//name/text()");

        transfer.transferProperties(submitContext);
        assertThat(targetProperty.getValue(), is(sourceProperty.getValue()));

        targetProperty.setValue("<bil><name test=\"test\">bmw</name></bil>");
        transfer.transferProperties(submitContext);

        assertThat(targetProperty.getValue(), is("<bil><name test=\"test\">audi</name></bil>"));
    }

    @Test
    public void testTextContentXmlToXmlTransfer() throws Exception {
        sourceProperty.setValue("<bil><name>audi</name></bil>");
        targetProperty.setValue("<bil><name2>bmw</name2></bil>");

        transfer.setTransferTextContent(true);
        transfer.setSourcePath("//name");
        transfer.setTargetPath("//name2");

        transfer.transferProperties(submitContext);

        assertThat(targetProperty.getValue(), is("<bil><name2>audi</name2></bil>"));
    }

    @Test
    public void testTextXmlToXmlNullTransfer() throws Exception {
        sourceProperty.setValue("<bil><name/></bil>");
        targetProperty.setValue("<bil><name>bmw</name></bil>");

        transfer.setSourcePath("//name/text()");
        transfer.setTargetPath("//name/text()");

        transfer.transferProperties(submitContext);

        assertThat(targetProperty.getValue(), is("<bil><name/></bil>"));
    }

    @Test
    public void testAttributeXmlToXmlTransfer() throws Exception {
        sourceProperty.setValue("<bil><name value=\"fiat\" value2=\"volvo\">alfa</name></bil>");
        targetProperty.setValue("<bil><name test=\"test\">bmw</name></bil>");

        transfer.setSourcePath("//name/@value");
        transfer.setTargetPath("//name/text()");

        transfer.transferProperties(submitContext);

        assertThat(targetProperty.getValue(), is("<bil><name test=\"test\">fiat</name></bil>"));

        transfer.setSourcePath("//name/text()");
        transfer.setTargetPath("//name/@test");

        transfer.transferProperties(submitContext);

        assertThat(targetProperty.getValue(), is("<bil><name test=\"alfa\">fiat</name></bil>"));

        transfer.setSourcePath("//name/@value2");
        transfer.transferProperties(submitContext);
        assertThat(targetProperty.getValue(), is("<bil><name test=\"volvo\">fiat</name></bil>"));
    }

    @Test
    public void testElementXmlToXmlTransfer() throws Exception {
        sourceProperty.setValue("<bil><name>audi</name></bil>");
        targetProperty.setValue("<bil><test/></bil>");

        transfer.setSourcePath("//bil");
        transfer.setTargetPath("//bil");

        transfer.setTransferTextContent(false);
        transfer.transferProperties(submitContext);
        assertThat(targetProperty.getValue(), is(sourceProperty.getValue()));

        targetProperty.setValue("<bil><name></name></bil>");

        transfer.setSourcePath("//bil/name/text()");
        transfer.setTargetPath("//bil/name");

        transfer.transferProperties(submitContext);
        assertThat(targetProperty.getValue(), is(sourceProperty.getValue()));
    }

    @Test
    public void testElementWithNsXmlToXmlTransfer() throws Exception {
        sourceProperty.setValue("<ns1:bil xmlns:ns1=\"ns1\"><ns1:name>audi</ns1:name></ns1:bil>");
        targetProperty.setValue("<bil><name/></bil>");

        transfer.setTransferTextContent(false);
        transfer.setSourcePath("declare namespace ns='ns1';//ns:bil/ns:name");
        transfer.setTargetPath("//bil/name");

        transfer.transferProperties(submitContext);
        assertThat(targetProperty.getValue(), is("<bil xmlns:ns1=\"ns1\"><ns1:name>audi</ns1:name></bil>"));
    }

    @Test
    public void supportsJsonPathInSource() throws Exception {
        sourceProperty.setValue("{ persons: [" +
                "{ firstName: 'Anders', lastName: 'And' }," +
                "{ firstName: 'Anders', lastName: 'And' }" +
                "] }");
        transfer.setSourcePath("$.persons[0].firstName");
        transfer.transferProperties(submitContext);

        assertThat(targetProperty.getValue(), is("Anders"));
    }
}
