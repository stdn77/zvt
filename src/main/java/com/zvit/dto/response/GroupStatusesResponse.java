package com.zvit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Повна відповідь зі статусами групи, включаючи терміновий збір
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupStatusesResponse {

    private List<UserStatusResponse> users;    // Статуси користувачів
    private UrgentSessionInfo urgentSession;   // Інформація про терміновий збір
}
