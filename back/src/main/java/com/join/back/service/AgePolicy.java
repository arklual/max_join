package com.join.back.service;

import com.join.back.model.entity.User;

/**
 * JOIN is open from 14 — the Pushkin card age. Teenagers (14–17) meet only each other:
 * matches, open companies and profiles never cross the 18-year line.
 */
public final class AgePolicy {

    public static final int MIN_AGE = 14;
    public static final int ADULT_AGE = 18;
    public static final String MIN_AGE_MESSAGE = "JOIN доступен с 14 лет";

    private AgePolicy() {
    }

    /** Unknown age counts as adult, so such a user never meets a teenager. */
    public static boolean isMinor(User user) {
        return user.getAge() != null && user.getAge() < ADULT_AGE;
    }

    public static boolean canMeet(User a, User b) {
        return isMinor(a) == isMinor(b);
    }

    public static void requireAllowedAge(Integer age) {
        if (age == null || age < MIN_AGE) {
            throw new IllegalArgumentException(MIN_AGE_MESSAGE);
        }
    }
}
