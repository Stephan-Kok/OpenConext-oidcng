package oidc.endpoints;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import net.minidev.json.JSONObject;
import oidc.secure.TokenGenerator;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
public class JwkKeysEndpoint {

    private TokenGenerator tokenGenerator;
    private Map<String, Object> wellKnownConfiguration;

    public JwkKeysEndpoint(TokenGenerator tokenGenerator, ObjectMapper objectMapper,
                           @Value("${spring.security.saml2.service-provider.base-path}") String basePath,
                           @Value("${spring.security.saml2.service-provider.entity-id}") String issuer) throws IOException {
        this.tokenGenerator = tokenGenerator;
        String json = IOUtils.toString(new ClassPathResource("openid-configuration.json").getInputStream(), Charset.defaultCharset());
        json = json.replaceAll("@@base_url@@", basePath).replaceAll("@@issuer@@", issuer);
        this.wellKnownConfiguration = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
        });
    }

    @GetMapping("/oidc/generate-jwks-keystore")
    public Map<String, List<JSONObject>> generate() throws Exception {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        com.nimbusds.jose.jwk.RSAKey build = new com.nimbusds.jose.jwk.RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .algorithm(new Algorithm("RS256"))
                .keyID("oidc")
                .build();

        return Collections.singletonMap("keys", Collections.singletonList(build.toJSONObject()));
    }

    @GetMapping(value = {"/oidc/certs"}, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public String publishClientJwk() {
        Map<String, ? extends JWK> allPublicKeys = tokenGenerator.getAllPublicKeys();
        JWKSet jwkSet = new JWKSet(new ArrayList<>(allPublicKeys.values()));
        return jwkSet.toString();
    }

    @GetMapping("oidc/.well-known/openid-configuration")
    public Map<String, Object> wellKnownConfiguration() {
        return this.wellKnownConfiguration;
    }

}