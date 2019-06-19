package upc.similarity.similaritydetectionapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.service.Tag;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;
import upc.similarity.similaritydetectionapi.RestApiController;

import java.util.HashSet;

@Configuration
@PropertySource("classpath:swagger.properties")
@ComponentScan(basePackageClasses = RestApiController.class)
@EnableSwagger2
public class SwaggerConfig {

    private static final String	LICENSE_TEXT	    = "License";
    private static final String	TITLE		    = "SIMILARITY DETECTION COMPONENT";
    private static final String	DESCRIPTION    = "" +
            "<p>The component is based in td-idf numerical statistic. The aim of the API is to calculate the similarity score between multiple pairs of requirements."+
            "</p>" +
            "<p>There are seven main methods: </p>" +
            "<ul>" +
            "<li><strong>AddReqs</strong>: Generates a model with the input requirements</li>" +
            "<li><strong>ReqReq</strong>: Compares two requirements</li>" +
            "<li><strong>ReqProject</strong>: Compares between a list of requirements and a set of requirements</li>" +
            "<li><strong>ReqOrganization</strong>: Compares between a list of requirements and all the requirements of a specific organization</li>" +
            "<li><strong>Project</strong>: Compares all possible pairs of requirements from a set of requirements</li>" +
            "<li><strong>AddReqsAndCompute</strong>: Is a mixture between AddReqs and Project methods</li>" +
            "<li><strong>AddReqsAndComputeOrphans</strong>: Generates a model with the input clusters and computes the similarity between them</li>" +
            "</ul>" +
            "<p>And three auxiliary operations: </p>" +
            "<ul>" +
            "<li><strong>GetResponse</strong>: Returns in patches the resulting dependencies of the other methods</li>" +
            "<li><strong>DeleteOrganizationResponses</strong>: Deletes the organization responses from the database</li>" +
            "<li><strong>DeleteDatabase</strong>: Deletes all data from the database</li>" +
            "</ul>" +
            "<p>The component needs to preprocess the requirements before doing any comparison. The operation AddReqs is responsible for that work.</p>" +
            "<p>The operations AddReqs, ReqProject, Project and AddReqsAndCompute are asynchronous. It is necessary to write a server URL as parameter in all of them. The outcome of the operation will be returned to that url. All these operations follow the same pattern:</p>" +
            "<ol><li>The client calls the operation with all necessary parameters</li>" +
            "<li>The service receives the request and checks the main conditions</li>" +
            "<li>The service returns if the client request has been accepted or not and closes the connection" +
            "<ul><li>(httpStatus!=200) The request has not been accepted. The message body contains the exception cause.</li>" +
            "<li>(httpStatus==200) The request has been accepted. The similarity calculation runs in the background. The message body contains the request identifier i.e. <em>{\"id\": \"1548924677975_523\"}</em></li></ul>" +
            "<li>When the calculation finishes (only if the request has been accepted) the service opens a connection with the server url specified as parameter. It sends a Json object that contains the outcome of the computation:<br>" +
            "<ul>" +
            "<li>(success) Example: {\"code\": 200,\"id\": \"1557395889689_587\",\"operation\": \"AddReqs\"}.</li>" +
            "<li>(!success) Example: {\"code\": 400,\"id\": \"1557396039530_583\",\"error\": \"Bad request\",\"message\": \"The requirement with id QM-3 is already inside the project\",\"operation\": \"ReqProject\"}.</li>" +
            "<li>The resulting dependencies can be obtained via the GetResponse method.</li></ul></li></ol>" +
            "<p>The API uses UTF-8 charset. Also, it uses the OpenReq format for input JSONs (it is specified in the Models section).</p>";

    /**
     * API Documentation Generation.
     * @return
     */
    @Bean
    public Docket api() {
        HashSet<String> protocols = new HashSet<>();
        protocols.add("https");
        return new Docket(DocumentationType.SWAGGER_2)
                .useDefaultResponseMessages(false)
                .apiInfo(apiInfo())
                .pathMapping("/")
                //.host("api.openreq.eu/similarity-detection")
                //.protocols(protocols)
                .select()
                .paths(PathSelectors.regex("^((?!Test).)*$"))
                .apis(RequestHandlerSelectors.basePackage("upc.similarity.similaritydetectionapi")).paths(PathSelectors.regex("/upc.*"))
                .build().tags(new Tag("Similarity detection Service", "API related to similarity detection"));
    }
    /**
     * Informtion that appear in the API Documentation Head.
     *
     * @return
     */
    private ApiInfo apiInfo() {
        return new ApiInfoBuilder().title(TITLE).description(DESCRIPTION).license(LICENSE_TEXT)
                .contact(new Contact("UPC-GESSI (OPENReq)", "http://openreq.eu/", ""))
                .build();
    }
}