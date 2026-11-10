package com.petshop;

import com.petshop.config.security.JwtService;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import static org.assertj.core.api.Assertions.*;

class RegistrationEncryptionTests {
    private final SecretKey key = Jwts.ENC.A256GCM.key().build();
    private final String encodedKey = Base64.getEncoder().encodeToString(key.getEncoded());
    private final JwtService tokens = new JwtService(encodedKey);

    private String registration() {
        return tokens.generateRegistrationToken("Test User", "synthetic@example.test", "0900000000", "SYNTHETIC_PASSWORD_HASH", 60000);
    }

    @Test void encryptedRegistrationSurvivesRestartWithTheSamePrivateKey() {
        var claims = new JwtService(encodedKey).parseRegistrationToken(registration());
        assertThat(claims.getSubject()).isEqualTo("synthetic@example.test");
        assertThat(claims.get("pwd")).isEqualTo("SYNTHETIC_PASSWORD_HASH");
        assertThat(claims.get("purpose")).isEqualTo("REGISTER");
    }

    @Test void readableTokenSegmentsNeverContainRegistrationDetails() {
        String[] segments = registration().split("\\.", -1);
        assertThat(segments).hasSize(5);
        for (String segment : segments) {
            String decoded = new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8);
            assertThat(decoded).doesNotContain("SYNTHETIC_PASSWORD_HASH", "synthetic@example.test", "0900000000", "Test User");
        }
    }

    @Test void tamperedCiphertextCannotActivateAnAccount() {
        String[] segments = registration().split("\\.", -1);
        byte[] ciphertext = Base64.getUrlDecoder().decode(segments[3]);
        ciphertext[ciphertext.length / 2] ^= 1;
        segments[3] = Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
        assertThatThrownBy(() -> tokens.parseRegistrationToken(String.join(".", segments))).isInstanceOf(JwtException.class);
    }

    @Test void aDifferentKeyCannotReadTheRegistration() {
        JwtService other = new JwtService(Base64.getEncoder().encodeToString(Jwts.ENC.A256GCM.key().build().getEncoded()));
        assertThatThrownBy(() -> other.parseRegistrationToken(registration())).isInstanceOf(JwtException.class);
    }

    @Test void expiredAndWrongPurposePayloadsAreRejected() {
        String expired = Jwts.builder().subject("synthetic@example.test").claim("purpose", "REGISTER")
                .expiration(new Date(System.currentTimeMillis() - 60000)).encryptWith(key, Jwts.ENC.A256GCM).compact();
        String wrongPurpose = Jwts.builder().subject("synthetic@example.test").claim("purpose", "RESET")
                .expiration(new Date(System.currentTimeMillis() + 60000)).encryptWith(key, Jwts.ENC.A256GCM).compact();
        assertThatThrownBy(() -> tokens.parseRegistrationToken(expired)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> tokens.parseRegistrationToken(wrongPurpose)).isInstanceOf(JwtException.class);
    }

    @Test void signedLegacyAndUnsignedTokensAreRejected() {
        String signed = Jwts.builder().subject("synthetic@example.test").claim("purpose", "REGISTER")
                .signWith(Jwts.SIG.HS256.key().build()).compact();
        String unsigned = Jwts.builder().subject("synthetic@example.test").claim("purpose", "REGISTER").compact();
        assertThatThrownBy(() -> tokens.parseRegistrationToken(signed)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> tokens.parseRegistrationToken(unsigned)).isInstanceOf(JwtException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"invalid*", "dG9vLXNob3J0"})
    void invalidConfigurationFailsWithoutPrintingTheSecret(String value) {
        assertThatThrownBy(() -> new JwtService(value)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("registration-encryption-key").hasMessageNotContaining(value);
    }

    @Test void otherAuthenticatedEncryptionFormatsAreNotRegistrationTokens() {
        String wrapped=Jwts.builder().subject("synthetic@example.test").claim("purpose","REGISTER")
                .encryptWith(key,Jwts.KEY.A256KW,Jwts.ENC.A256GCM).compact();
        assertThatThrownBy(() -> tokens.parseRegistrationToken(wrapped)).isInstanceOf(JwtException.class);
    }
}
