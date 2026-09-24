package com.join.back.service;

import com.join.back.model.entity.Chat;
import com.join.back.model.entity.Event;
import com.join.back.model.entity.User;
import com.join.back.repository.ChatRepository;
import com.join.back.repository.EventRepository;
import com.join.back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The day before an event, reminds both companions of every chat about it through the bot —
 * so the plan made in JOIN turns into an actual visit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventReminderService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final EventRepository eventRepository;
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final MessengerNotificationService messengerNotificationService;

    @Scheduled(cron = "${reminders.cron:0 0 12 * * *}", zone = "Europe/Moscow")
    public void remindAboutTomorrow() {
        int sent = sendReminders(LocalDate.now(ZONE).plusDays(1));
        log.info("Event reminders sent: {}", sent);
    }

    /** Sends reminders for events on {@code date}; returns the number of reminded users. */
    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public int sendReminders(LocalDate date) {
        Map<Long, Event> events = eventRepository.findByEventDate(date).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));
        if (events.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (Chat chat : chatRepository.findByEventIdIn(events.keySet())) {
            Event event = events.get(chat.getEventId());
            User user1 = userRepository.findById(chat.getUser1Id()).orElse(null);
            User user2 = userRepository.findById(chat.getUser2Id()).orElse(null);
            if (user1 == null || user2 == null) {
                continue;
            }
            String when = event.getEventTime() != null ? "завтра в " + event.getEventTime().format(TIME) : "завтра";
            if (chat.getUser1DeletedAt() == null) {
                messengerNotificationService.sendEventReminder(user1, nameOf(user2), event.getTitle(), when, chat.getId());
                sent++;
            }
            if (chat.getUser2DeletedAt() == null) {
                messengerNotificationService.sendEventReminder(user2, nameOf(user1), event.getTitle(), when, chat.getId());
                sent++;
            }
        }
        return sent;
    }

    private static String nameOf(User user) {
        return user.getFirstName() != null ? user.getFirstName() : "напарником";
    }
}
