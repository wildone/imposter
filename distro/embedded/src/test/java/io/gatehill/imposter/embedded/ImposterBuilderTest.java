package io.gatehill.imposter.embedded;

import com.jayway.restassured.RestAssured;
import io.gatehill.imposter.util.HttpUtil;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author pete
 */
class ImposterBuilderTest {
    @Test
    public void testStandaloneSpec() throws Exception {
        final Path specFile = Paths.get(ImposterBuilderTest.class.getResource("/petstore-simple.yaml").toURI());

        final ImposterBuilder.MockEngine imposter = new ImposterBuilder<>()
                .withSpecificationFile(specFile)
                .start().get();

        RestAssured.baseURI = String.valueOf(imposter.getBaseUrl());
        given().when()
                .get("/example")
                .then()
                .statusCode(equalTo(HttpUtil.HTTP_OK));
    }
}
