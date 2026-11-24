package dev.kis.dashboard;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptedApplicationPropertiesTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "JASYPT_ENCRYPTOR_PASSWORD", matches = ".+")
    void decryptsOAuthAndDatabaseValuesFromApplicationConfiguration() throws IOException {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        String googleIdCipher = find(yaml, "google", "client-id");
        String googleSecretCipher = find(yaml, "google", "client-secret");
        String githubIdCipher = find(yaml, "github", "client-id");
        String githubSecretCipher = find(yaml, "github", "client-secret");
        String dbUrlCipher = find(yaml, "datasource", "url");
        String dbUserCipher = find(yaml, "datasource", "username");
        String dbPasswordCipher = find(yaml, "datasource", "password");

        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        encryptor.setPassword(System.getenv("JASYPT_ENCRYPTOR_PASSWORD"));
        encryptor.setKeyObtentionIterations(100_000);
        encryptor.setProviderName("SunJCE");
        encryptor.setSaltGenerator(new org.jasypt.salt.RandomSaltGenerator());
        encryptor.setIvGenerator(new org.jasypt.iv.RandomIvGenerator());
        encryptor.setStringOutputType("base64");

        assertTrue(encryptor.decrypt(googleIdCipher).endsWith(".apps.googleusercontent.com"));
        assertTrue(!encryptor.decrypt(googleSecretCipher).isBlank());
        assertTrue(!encryptor.decrypt(githubIdCipher).isBlank());
        assertTrue(!encryptor.decrypt(githubSecretCipher).isBlank());
        assertTrue(encryptor.decrypt(dbUrlCipher).startsWith("jdbc:mysql://localhost:3306/kis_service?"));
        assertTrue(!encryptor.decrypt(dbUserCipher).isBlank());
        assertTrue(!encryptor.decrypt(dbPasswordCipher).isBlank());
    }

    @Test
    void kisAppCredentialsAreNotStoredInConfiguration() throws IOException {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        // KIS app keys, secrets and account numbers are stored per user, encrypted, in the database.
        assertFalse(yaml.contains("app-key"));
        assertFalse(yaml.contains("app-secret"));
        assertFalse(yaml.contains("account-number"));
    }

    private static String find(String yaml, String section, String property) {
        int sectionStart;
        int sectionEnd;
        if (section.equals("google")) {
            sectionStart = yaml.indexOf("          google:");
            sectionEnd = yaml.indexOf("\n          github:", sectionStart + 1);
        } else if (section.equals("github")) {
            sectionStart = yaml.indexOf("          github:");
            sectionEnd = yaml.indexOf("\n  web:", sectionStart + 1);
        } else {
            sectionStart = yaml.indexOf("  datasource:");
            sectionEnd = yaml.indexOf("\n  jpa:", sectionStart + 1);
        }
        if (sectionStart < 0) throw new AssertionError("Missing encrypted application configuration section");
        if (sectionEnd < 0) sectionEnd = yaml.length();
        Matcher matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(property) + ":\\s*ENC\\(([^)]+)\\)\\s*$")
                .matcher(yaml.substring(sectionStart, sectionEnd));
        if (!matcher.find()) throw new AssertionError("Missing encrypted property in application configuration");
        return matcher.group(1);
    }

}
