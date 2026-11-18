package egovframework.haengjeongnuli.cmm.security;

import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HTTPS 로 켰을 때, 예전 주소(http://...:8080)로 들어와도 "Bad Request" 대신 https 주소로 넘겨 준다.
 * 이 http 포트는 넘겨주기만 하고 화면·데이터는 내보내지 않는다.
 */
@Configuration
@ConditionalOnProperty(name = "server.ssl.enabled", havingValue = "true")
public class HttpRedirectConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpToHttps(
            @Value("${app.http-redirect-port:0}") int httpPort, @Value("${server.port:8443}") int httpsPort) {
        return factory -> {
            if (httpPort <= 0 || httpPort == httpsPort) {
                return;
            }
            Connector c = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            c.setScheme("http");
            c.setSecure(false);
            c.setPort(httpPort);
            c.setRedirectPort(httpsPort);
            factory.addAdditionalTomcatConnectors(c);
            factory.addContextCustomizers(ctx -> {
                SecurityConstraint sc = new SecurityConstraint();
                sc.setUserConstraint("CONFIDENTIAL");
                SecurityCollection all = new SecurityCollection();
                all.addPattern("/*");
                sc.addCollection(all);
                ctx.addConstraint(sc);
            });
        };
    }
}
