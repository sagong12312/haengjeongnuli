package egovframework.haengjeongnuli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HaengjeongnuliApplication {

    public static void main(String[] args) {
        SpringApplication.run(HaengjeongnuliApplication.class, args);
    }
}
