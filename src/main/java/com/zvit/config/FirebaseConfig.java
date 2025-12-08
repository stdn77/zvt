package com.zvit.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.config-path:firebase-service-account.json}")
    private String firebaseConfigPath;

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                InputStream serviceAccount;

                // Спробуємо знайти файл у classpath (resources)
                try {
                    ClassPathResource resource = new ClassPathResource(firebaseConfigPath);
                    serviceAccount = resource.getInputStream();
                    log.info("Firebase config loaded from classpath: {}", firebaseConfigPath);
                } catch (IOException e) {
                    // Якщо не знайдено в classpath, спробуємо як абсолютний шлях
                    serviceAccount = new FileInputStream(firebaseConfigPath);
                    log.info("Firebase config loaded from file path: {}", firebaseConfigPath);
                }

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized successfully");
            }
        } catch (IOException e) {
            log.warn("Firebase initialization skipped - config file not found: {}. Push notifications will be disabled.", firebaseConfigPath);
        }
    }
}
