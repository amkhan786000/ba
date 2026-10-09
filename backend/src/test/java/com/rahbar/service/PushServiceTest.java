package com.rahbar.service;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Web Push encryption must match the worked example in RFC 8291, Appendix A, byte for byte. */
class PushServiceTest {

    @Test
    void encryptMatchesRfc8291Example() throws Exception {
        ECParameterSpec params = PushService.p256();
        byte[] asPrivate = PushService.decode("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
        byte[] asPublic = PushService.decode("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        KeyPair sender = new KeyPair(PushService.publicKey(asPublic, params),
                KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, asPrivate), params)));

        byte[] body = PushService.encrypt(
                "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8),
                PushService.decode("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"),
                PushService.decode("BTBZMqHH6r4Tts7J_aSIgg"),
                sender,
                PushService.decode("DGv6ra1nlYgDCS1FRnbzlw"));

        assertEquals("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN",
                PushService.encode(body));
    }
}
