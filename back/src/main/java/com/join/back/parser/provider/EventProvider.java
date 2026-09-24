package com.join.back.parser.provider;

import com.join.back.model.entity.EventSource;
import com.join.back.parser.dto.RawExternalEvent;

import java.util.List;

public interface EventProvider {

    EventSource getSource();

    List<RawExternalEvent> fetchEvents();
}
