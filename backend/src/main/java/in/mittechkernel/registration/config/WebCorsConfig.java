package in.mittechkernel.registration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * CORS for the Vite dev server and whatever origin the site is eventually deployed to.
 *
 * <p>Origins come from configuration rather than a wildcard: this API will accept
 * registrations, and an allow-all policy is a habit that survives into production.
 * Credentials are not enabled because there is no authentication in this milestone.
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public WebCorsConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "Accept")
                .maxAge(3600);
    }
}
