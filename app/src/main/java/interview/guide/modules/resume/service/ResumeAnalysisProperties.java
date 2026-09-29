package interview.guide.modules.resume.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "app.resume.analysis")
public class ResumeAnalysisProperties {

    private String defaultDirection = "TECH";
    private Map<String, PromptRoute> promptRoutes = new LinkedHashMap<>();

    @Data
    public static class PromptRoute {
        private String systemPromptPath;
        private String userPromptPath;
    }
}
