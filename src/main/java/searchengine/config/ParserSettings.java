package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jsoup-settings")
public class ParserSettings {
    private String userAgent;
    private String referrer;
    private int timeout;
    private boolean ignoreContentType;
    private boolean ignoreHttpErrors;
    private boolean followRedirects;
}
