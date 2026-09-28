package com.join.back.model.entity;

/** A match is only a suggestion until one side asks and the other accepts — then a chat opens. */
public enum MatchStatus {
    /** Found each other, nobody asked yet. */
    NEW,
    /** One side asked "пойдём вместе?" ({@code requestedBy}). */
    REQUESTED,
    /** Accepted — the pair has a chat. */
    ACCEPTED,
    /** Declined — hidden for both. */
    DECLINED
}
