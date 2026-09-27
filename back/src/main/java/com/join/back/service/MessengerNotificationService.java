package com.join.back.service;

import com.join.back.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Routes bot notifications to every messenger the user is connected to
 * (MAX and/or Telegram). Users of the Android app only get in-app notifications.
 */
@Service
@RequiredArgsConstructor
public class MessengerNotificationService {

    private final MaxNotificationService maxNotificationService;
    private final TelegramNotificationService telegramNotificationService;

    public void sendMatchNotification(User user, String companionName, String eventTitle, Long chatId) {
        if (user == null) return;
        if (user.getMaxId() != null) maxNotificationService.sendMatchNotification(user.getMaxId(), companionName, eventTitle, chatId);
        if (user.getTelegramId() != null) telegramNotificationService.sendMatchNotification(user.getTelegramId(), companionName, eventTitle, chatId);
    }

    public void sendChatMessageNotification(User user, String senderName, String messageText, Long chatId) {
        if (user == null) return;
        if (user.getMaxId() != null) maxNotificationService.sendChatMessageNotification(user.getMaxId(), senderName, messageText, chatId);
        if (user.getTelegramId() != null) telegramNotificationService.sendChatMessageNotification(user.getTelegramId(), senderName, messageText, chatId);
    }

    public void sendGroupMessageNotification(User user, String senderName, String groupEventTitle, String messageText, Long groupChatId) {
        if (user == null) return;
        if (user.getMaxId() != null) maxNotificationService.sendGroupMessageNotification(user.getMaxId(), senderName, groupEventTitle, messageText, groupChatId);
        if (user.getTelegramId() != null) telegramNotificationService.sendGroupMessageNotification(user.getTelegramId(), senderName, groupEventTitle, messageText, groupChatId);
    }

    public void sendGroupInvite(User user, String inviterName, String eventTitle, String when, Long groupId) {
        if (user == null) return;
        if (user.getMaxId() != null) maxNotificationService.sendGroupInvite(user.getMaxId(), inviterName, eventTitle, when, groupId);
        if (user.getTelegramId() != null) telegramNotificationService.sendGroupInvite(user.getTelegramId(), inviterName, eventTitle, when, groupId);
    }

    public void sendOutingProposal(User user, String companionName, String eventTitle, Long chatId) {
        if (user == null) return;
        if (user.getMaxId() != null) maxNotificationService.sendOutingProposal(user.getMaxId(), companionName, eventTitle, chatId);
        if (user.getTelegramId() != null) telegramNotificationService.sendOutingProposal(user.getTelegramId(), companionName, eventTitle, chatId);
    }

    public void sendOutingAgreed(User user, String companionName, String eventTitle, Long chatId) {
        if (user == null) return;
        if (user.getMaxId() != null) maxNotificationService.sendOutingAgreed(user.getMaxId(), companionName, eventTitle, chatId);
        if (user.getTelegramId() != null) telegramNotificationService.sendOutingAgreed(user.getTelegramId(), companionName, eventTitle, chatId);
    }

    public void sendOutingFeedback(User user, String companionName, String eventTitle, Long chatId) {
        if (user == null) return;
        if (user.getMaxId() != null) maxNotificationService.sendOutingFeedback(user.getMaxId(), companionName, eventTitle, chatId);
        if (user.getTelegramId() != null) telegramNotificationService.sendOutingFeedback(user.getTelegramId(), companionName, eventTitle, chatId);
    }

    public void sendEventReminder(User user, String companionName, String eventTitle, String when, Long chatId) {
        if (user == null) return;
        if (user.getMaxId() != null) maxNotificationService.sendEventReminder(user.getMaxId(), companionName, eventTitle, when, chatId);
        if (user.getTelegramId() != null) telegramNotificationService.sendEventReminder(user.getTelegramId(), companionName, eventTitle, when, chatId);
    }
}
