package mtvs.onvision.vision.common.swagger;

import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SwaggerOrderTransformer extends SwaggerIndexPageTransformer {

    public SwaggerOrderTransformer(SwaggerUiConfigProperties a, SwaggerUiOAuthProperties b,
                                   SwaggerWelcomeCommon c, ObjectMapperProvider d) {
        super(a, b, c, d);
    }

    @Override
    public Resource transform(HttpServletRequest request, Resource resource,
                              ResourceTransformerChain transformer) throws IOException {
        Resource transformed = super.transform(request, resource, transformer);
        if (resource.toString().contains("swagger-initializer.js")) {
            String content = new String(transformed.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String customSorter = "\"operationsSorter\" : function(a, b) {"
                    + "  var oa = (a.get('operation').getIn(['x-order']) ?? 999);"
                    + "  var ob = (b.get('operation').getIn(['x-order']) ?? 999);"
                    + "  return oa - ob;"
                    + "},";
            content = content.replace("\"configUrl\" :", customSorter + "\n  \"configUrl\" :");
            return new TransformedResource(resource, content.getBytes(StandardCharsets.UTF_8));
        }
        return transformed;
    }
}
