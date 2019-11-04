package com.smartbear.ready.recipe.assertions;

import com.eviware.soapui.impl.wsdl.teststeps.assertions.json.JsonPathContentAssertion;
import com.eviware.soapui.impl.wsdl.teststeps.assertions.json.JsonPathRegExAssertion;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

import static com.smartbear.ready.recipe.NullChecker.checkNotNull;

/**
 * Captures a JsonPath Contains assertion in JSON format.
 */
@ApiModel(value = "JsonPathRegExAssertion", description = "JsonPath RegEx assertion definition")
public class JsonPathRegExAssertionStruct extends AssertionStruct<JsonPathRegExAssertion> {

    public final String jsonPath;
    public final String regularExpression;

    @JsonCreator
    public JsonPathRegExAssertionStruct(@JsonProperty("name") String name, @JsonProperty("jsonPath") String jsonPath, @JsonProperty("regEx") String regularExpression, @JsonProperty("allowWildcards") boolean allowWildcards) {
        super(JsonPathContentAssertion.LABEL, name);

        checkNotNull(jsonPath, "jsonPath");
        checkNotNull(regularExpression, "regularExpression");

        this.jsonPath = jsonPath;
        this.regularExpression = regularExpression;
    }

    @Override
    void configureAssertion(JsonPathRegExAssertion assertion) {
        assertion.setPath(jsonPath);
        assertion.setRegularExpression(regularExpression);
    }
}
