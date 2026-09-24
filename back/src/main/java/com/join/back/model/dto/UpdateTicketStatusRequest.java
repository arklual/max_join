package com.join.back.model.dto;

import com.join.back.model.entity.SupportTicketStatus;

public record UpdateTicketStatusRequest(SupportTicketStatus status) {
}
