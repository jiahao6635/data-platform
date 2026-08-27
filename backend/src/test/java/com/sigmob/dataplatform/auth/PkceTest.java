package com.sigmob.dataplatform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;

import org.junit.jupiter.api.Test;

class PkceTest {

    @Test
    void s256ChallengeMatchesRfc7636TestVector() {
        assertThat(Pkce.s256Challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    void generatedVerifierMeetsRfcLength() {
        String verifier = Pkce.randomUrlSafe(new SecureRandom(), Pkce.RANDOM_BYTE_LENGTH);
        assertThat(verifier).hasSize(43).doesNotContain("=").doesNotContain("+").doesNotContain("/");
    }
}
