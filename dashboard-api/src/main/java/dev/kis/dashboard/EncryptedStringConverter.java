package dev.kis.dashboard;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Encrypts a column with the application's Jasypt encryptor (same master password and algorithm as the
 * {@code ENC(...)} configuration values). Each write uses a random salt and IV.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    private final StringEncryptor encryptor;

    public EncryptedStringConverter(@Qualifier("kisColumnEncryptor") StringEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(String value) {
        return value == null ? null : encryptor.encrypt(value);
    }

    @Override
    public String convertToEntityAttribute(String value) {
        return value == null ? null : encryptor.decrypt(value);
    }
}
