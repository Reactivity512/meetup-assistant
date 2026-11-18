package github.reactivity512.meetup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
public class MeetupAssistantApplication {

    static {
        // Vosk returns JSON as UTF-8. JNA decodes native strings with the platform
        // charset by default (for example windows-1251), which turns Russian text
        // into mojibake. The property must be set before JNA is initialized.
        if (System.getProperty("jna.encoding") == null) {
            System.setProperty("jna.encoding", "UTF-8");
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(MeetupAssistantApplication.class, args);
    }
}
