package com.zvit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicKeyResponse {
    private String publicKey;   // Base64 encoded public key
    private String algorithm;   // RSA
    private int keySize;        // 2048
}
