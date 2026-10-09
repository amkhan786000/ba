package com.rahbar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahbar.entity.AppSetting;
import com.rahbar.entity.PushSubscription;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.AppSettingRepository;
import com.rahbar.repository.PushSubscriptionRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Browser push notifications (Web Push), so a notification also pops up on the user's phone or computer even when
 * Rahbar isn't open. Users switch it on per browser / device; there is no per-message cost.
 * <p>
 * Implements the standard directly with the JDK's crypto: messages are encrypted for each browser (RFC 8291,
 * aes128gcm) and signed with the application's VAPID key (RFC 8292). The key pair is generated once and kept in
 * app_settings. Sending happens on a background thread; subscriptions the push service reports as gone
 * (404 / 410) are removed, and ones that keep failing are dropped.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final String KEY_PRIVATE = "push.vapid.private";
    private static final String KEY_PUBLIC = "push.vapid.public";
    private static final int MAX_FAILURES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PushSubscriptionRepository subscriptionRepository;
    private final AppSettingRepository settingRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService sender = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "push-sender");
        t.setDaemon(true);
        return t;
    });

    @Value("${app.mail.from:no-reply@rahbar.org}")
    private String contact;

    private volatile KeyPair vapid;
    private volatile String vapidPublic;

    public PushService(PushSubscriptionRepository subscriptionRepository, AppSettingRepository settingRepository,
                       ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.settingRepository = settingRepository;
        this.objectMapper = objectMapper;
    }

    @PreDestroy
    void stop() {
        sender.shutdown();
    }

    // ------------------------------------------------------------------------------------ subscriptions

    /** The application's public key (base64url), which browsers need to subscribe. */
    public String publicKey() {
        keys();
        return vapidPublic;
    }

    /** Saves (or moves to this user) a browser's subscription. */
    public void subscribe(Long userId, String endpoint, String p256dh, String auth, String userAgent) {
        if (endpoint == null || !endpoint.startsWith("https://") || endpoint.length() > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This browser's push address is not valid.");
        }
        if (p256dh == null || auth == null || decode(p256dh).length != 65 || decode(auth).length != 16) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This browser's push keys are not valid.");
        }
        String hash = sha256(endpoint);
        PushSubscription s = subscriptionRepository.findByEndpointHash(hash).orElseGet(PushSubscription::new);
        s.setUserId(userId);
        s.setEndpoint(endpoint);
        s.setEndpointHash(hash);
        s.setP256dh(p256dh);
        s.setAuth(auth);
        s.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(300, userAgent.length())));
        if (s.getCreatedAt() == null) s.setCreatedAt(LocalDateTime.now());
        s.setFailures(0);
        subscriptionRepository.save(s);
    }

    /** Removes this browser's subscription (only the user's own). */
    public void unsubscribe(Long userId, String endpoint) {
        if (endpoint == null) return;
        subscriptionRepository.findByEndpointHash(sha256(endpoint))
                .filter(s -> s.getUserId().equals(userId))
                .ifPresent(subscriptionRepository::delete);
    }

    public int deviceCount(Long userId) {
        return subscriptionRepository.findByUserId(userId).size();
    }

    // ------------------------------------------------------------------------------------ sending

    /** Sends a notification to every browser the user switched on (in the background; never throws). */
    public void sendToUser(Long userId, String title, String body, String link) {
        if (userId == null) return;
        sender.submit(() -> {
            try {
                List<PushSubscription> subs = subscriptionRepository.findByUserId(userId);
                if (subs.isEmpty()) return;
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("title", title == null ? "Rahbar" : title);
                payload.put("body", body == null ? "" : (body.length() > 300 ? body.substring(0, 297) + "..." : body));
                payload.put("url", link == null || link.isBlank() ? "/" : link);
                byte[] json = objectMapper.writeValueAsBytes(payload);
                for (PushSubscription s : subs) deliver(s, json);
            } catch (Exception e) {
                log.warn("Push to user {} failed: {}", userId, e.getMessage());
            }
        });
    }

    /** Sends synchronously and reports how many devices accepted it (for the "send a test" button). */
    public int sendTest(Long userId) {
        int ok = 0;
        try {
            byte[] json = objectMapper.writeValueAsBytes(Map.of("title", "Rahbar", "body",
                    "Notifications are on for this device.", "url", "/"));
            for (PushSubscription s : subscriptionRepository.findByUserId(userId)) if (deliver(s, json)) ok++;
        } catch (Exception e) {
            log.warn("Test push to user {} failed: {}", userId, e.getMessage());
        }
        return ok;
    }

    private boolean deliver(PushSubscription s, byte[] payload) {
        try {
            byte[] body = encrypt(payload, decode(s.getP256dh()), decode(s.getAuth()));
            URI uri = URI.create(s.getEndpoint());
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("TTL", "86400")
                    .header("Urgency", "normal")
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("Authorization", "vapid t=" + jwt(uri.getScheme() + "://" + uri.getAuthority()) + ", k=" + publicKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 200 && status < 300) {
                s.setLastSuccessAt(LocalDateTime.now());
                s.setFailures(0);
                subscriptionRepository.save(s);
                return true;
            }
            if (status == 404 || status == 410) {
                subscriptionRepository.delete(s); // the browser unsubscribed or the subscription expired
            } else {
                failed(s, "HTTP " + status);
            }
        } catch (Exception e) {
            failed(s, e.getMessage());
        }
        return false;
    }

    private void failed(PushSubscription s, String why) {
        int failures = (s.getFailures() == null ? 0 : s.getFailures()) + 1;
        if (failures >= MAX_FAILURES) {
            log.info("Dropping push subscription {} after {} failures ({})", s.getSubscriptionId(), failures, why);
            subscriptionRepository.delete(s);
        } else {
            s.setFailures(failures);
            subscriptionRepository.save(s);
        }
    }

    // ------------------------------------------------------------------------------------ RFC 8291 encryption

    /** aes128gcm content encoding for one browser (one record; payloads are small). */
    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) throws GeneralSecurityException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(p256(), RANDOM);
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, uaPublic, authSecret, gen.generateKeyPair(), salt);
    }

    /** With a given sender key pair and salt (the RFC 8291 test vector uses fixed ones). */
    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair ephemeral, byte[] salt)
            throws GeneralSecurityException {
        ECParameterSpec params = p256();
        byte[] asPublic = uncompressed((ECPublicKey) ephemeral.getPublic());

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(ephemeral.getPrivate());
        ka.doPhase(publicKey(uaPublic, params), true);
        byte[] ecdhSecret = ka.generateSecret();

        byte[] keyInfo = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0}, uaPublic, asPublic);
        byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);
        byte[] cek = hkdf(salt, ikm, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII), new byte[]{0}), 16);
        byte[] nonce = hkdf(salt, ikm, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII), new byte[]{0}), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[]{2})); // 0x02: last (and only) record

        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
        header.put(salt).putInt(4096).put((byte) asPublic.length).put(asPublic);
        return concat(header.array(), ciphertext);
    }

    /** HKDF-SHA256 (extract + one expand block; enough for up to 32 bytes). */
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] okm = mac.doFinal(concat(info, new byte[]{1}));
        return Arrays.copyOf(okm, length);
    }

    // ------------------------------------------------------------------------------------ VAPID (RFC 8292)

    private String jwt(String audience) throws Exception {
        String header = encode(objectMapper.writeValueAsBytes(Map.of("typ", "JWT", "alg", "ES256")));
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("aud", audience);
        claims.put("exp", System.currentTimeMillis() / 1000 + 12 * 3600);
        claims.put("sub", "mailto:" + contact);
        String body = encode(objectMapper.writeValueAsBytes(claims));
        Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
        sig.initSign(keys().getPrivate());
        sig.update((header + "." + body).getBytes(StandardCharsets.US_ASCII));
        return header + "." + body + "." + encode(sig.sign());
    }

    /** The VAPID key pair: generated once and kept in app_settings. */
    private KeyPair keys() {
        if (vapid != null) return vapid;
        synchronized (this) {
            if (vapid != null) return vapid;
            try {
                KeyFactory kf = KeyFactory.getInstance("EC");
                Optional<AppSetting> priv = settingRepository.findById(KEY_PRIVATE);
                Optional<AppSetting> pub = settingRepository.findById(KEY_PUBLIC);
                if (priv.isPresent() && pub.isPresent()) {
                    PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(priv.get().getValue())));
                    PublicKey publicKey = publicKey(decode(pub.get().getValue()), ((ECPrivateKey) privateKey).getParams());
                    vapid = new KeyPair(publicKey, privateKey);
                } else {
                    KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
                    gen.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
                    KeyPair kp = gen.generateKeyPair();
                    save(KEY_PRIVATE, Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()));
                    save(KEY_PUBLIC, encode(uncompressed((ECPublicKey) kp.getPublic())));
                    log.info("Generated the push notification (VAPID) key pair");
                    vapid = kp;
                }
                vapidPublic = encode(uncompressed((ECPublicKey) vapid.getPublic()));
                return vapid;
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Could not load the push notification keys", e);
            }
        }
    }

    private void save(String key, String value) {
        AppSetting s = new AppSetting();
        s.setKey(key);
        s.setValue(value);
        s.setUpdatedAt(LocalDateTime.now());
        settingRepository.save(s);
    }

    // ------------------------------------------------------------------------------------ EC helpers

    static ECParameterSpec p256() throws GeneralSecurityException {
        AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
        p.init(new ECGenParameterSpec("secp256r1"));
        return p.getParameterSpec(ECParameterSpec.class);
    }

    /** 0x04 || X || Y (65 bytes). */
    static byte[] uncompressed(ECPublicKey key) {
        byte[] x = fixed(key.getW().getAffineX()), y = fixed(key.getW().getAffineY());
        return concat(new byte[]{4}, x, y);
    }

    static PublicKey publicKey(byte[] uncompressed, ECParameterSpec params) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 4) throw new InvalidKeyException("Not an uncompressed P-256 point");
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), params));
    }

    private static byte[] fixed(BigInteger n) {
        byte[] b = n.toByteArray();
        if (b.length == 32) return b;
        byte[] out = new byte[32];
        if (b.length > 32) System.arraycopy(b, b.length - 32, out, 0, 32);
        else System.arraycopy(b, 0, out, 32 - b.length, b.length);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.writeBytes(p);
        return out.toByteArray();
    }

    static String encode(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static byte[] decode(String s) {
        try {
            return Base64.getUrlDecoder().decode(s.trim().replace('+', '-').replace('/', '_').replace("=", ""));
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
