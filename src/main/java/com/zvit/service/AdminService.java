package com.zvit.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.zvit.dto.response.GroupMemberResponse;
import com.zvit.dto.response.UserStatusResponse;
import com.zvit.entity.GroupMember;
import com.zvit.repository.GroupMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final GroupMemberRepository groupMemberRepository;
    private final ReportService reportService;
    private final EncryptionService encryptionService;
    
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private final Map<String, DashboardToken> dashboardTokens = new ConcurrentHashMap<>();

    public byte[] generateQRCode(String groupId, String userId) {
        if (!groupMemberRepository.isUserAdminOfGroup(groupId, userId)) {
            throw new RuntimeException("Тільки адміністратор може генерувати QR-код");
        }

        String token = generateDashboardToken(groupId, userId);
        String dashboardUrl = baseUrl + "/web/admin/dashboard.html?token=" + token;

        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(
                dashboardUrl, 
                BarcodeFormat.QR_CODE, 
                300, 
                300
            );

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();

        } catch (WriterException | IOException e) {
            throw new RuntimeException("Помилка генерації QR-коду", e);
        }
    }

    public String generateDashboardToken(String groupId, String userId) {
        String token = UUID.randomUUID().toString();
        
        dashboardTokens.put(token, new DashboardToken(
            groupId, 
            userId, 
            LocalDateTime.now().plusHours(24)
        ));
        
        return token;
    }

    public List<UserStatusResponse> getDashboardByToken(String token) {
        DashboardToken dashboardToken = dashboardTokens.get(token);
        
        if (dashboardToken == null) {
            throw new RuntimeException("Невалідний токен");
        }
        
        if (dashboardToken.expiresAt.isBefore(LocalDateTime.now())) {
            dashboardTokens.remove(token);
            throw new RuntimeException("Токен закінчився");
        }
        
        return reportService.getGroupStatuses(
            dashboardToken.groupId, 
            dashboardToken.userId
        );
    }

    @Transactional(readOnly = true)
    public GroupMemberResponse getMemberDetails(String userId, String token) {
        DashboardToken dashboardToken = dashboardTokens.get(token);
        
        if (dashboardToken == null || dashboardToken.expiresAt.isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Невалідний або закінчений токен");
        }

        GroupMember member = groupMemberRepository
            .findByGroupIdAndUserId(dashboardToken.groupId, userId)
            .orElseThrow(() -> new RuntimeException("Користувач не знайдений в групі"));

        String decryptedPhone = encryptionService.decrypt(member.getUser().getPhoneEncrypted());

        return GroupMemberResponse.builder()
            .userId(member.getUser().getId())
            .name(member.getUser().getName())
            .phone(decryptedPhone)
            .role(member.getRole().name())
            .joinedAt(member.getJoinedAt())
            .build();
    }

    private static class DashboardToken {
        String groupId;
        String userId;
        LocalDateTime expiresAt;

        DashboardToken(String groupId, String userId, LocalDateTime expiresAt) {
            this.groupId = groupId;
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }
}