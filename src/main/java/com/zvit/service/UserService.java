package com.zvit.service;

import com.zvit.entity.User;
import com.zvit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User getUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));
    }

    @Transactional
    public void deactivateUser(String userId) {
        User user = getUserById(userId);
        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional
    public void activateUser(String userId) {
        User user = getUserById(userId);
        user.setActive(true);
        userRepository.save(user);
    }

    public boolean isUserActive(String userId) {
        User user = getUserById(userId);
        return user.isActive();
    }

    @Transactional
    public void updateFcmToken(String userId, String fcmToken) {
        User user = getUserById(userId);
        user.setFcmToken(fcmToken);
        userRepository.save(user);
    }

    @Transactional
    public void clearFcmToken(String userId) {
        User user = getUserById(userId);
        user.setFcmToken(null);
        userRepository.save(user);
    }
}