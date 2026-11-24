package dev.kis.dashboard;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Column encryptor built from the same {@code jasypt.encryptor.*} settings as the {@code ENC(...)} config values. */
@Configuration
public class ColumnEncryptionConfiguration {
    @Bean
    StringEncryptor kisColumnEncryptor(@Value("${jasypt.encryptor.password}") String password,
                                       @Value("${jasypt.encryptor.algorithm:PBEWITHHMACSHA512ANDAES_256}") String algorithm,
                                       @Value("${jasypt.encryptor.key-obtention-iterations:100000}") int iterations,
                                       @Value("${jasypt.encryptor.provider-name:SunJCE}") String provider,
                                       @Value("${jasypt.encryptor.salt-generator-classname:org.jasypt.salt.RandomSaltGenerator}") String salt,
                                       @Value("${jasypt.encryptor.iv-generator-classname:org.jasypt.iv.RandomIvGenerator}") String iv,
                                       @Value("${jasypt.encryptor.string-output-type:base64}") String outputType) {
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(password);
        config.setAlgorithm(algorithm);
        config.setKeyObtentionIterations(iterations);
        config.setProviderName(provider);
        config.setSaltGeneratorClassName(salt);
        config.setIvGeneratorClassName(iv);
        config.setStringOutputType(outputType);
        config.setPoolSize(2);
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        encryptor.setConfig(config);
        return encryptor;
    }
}
