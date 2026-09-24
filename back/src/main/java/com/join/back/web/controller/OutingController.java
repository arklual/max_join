package com.join.back.web.controller;

import com.join.back.model.dto.OutingResponse;
import com.join.back.repository.UserRepository;
import com.join.back.service.OutingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/outings")
public class OutingController extends BaseAuthController {

    private final OutingService outingService;

    public OutingController(UserRepository userRepository, OutingService outingService) {
        super(userRepository);
        this.outingService = outingService;
    }

    /** Upcoming events the current user goes to with a companion or a group. */
    @GetMapping
    public List<OutingResponse> getUpcoming() {
        return outingService.getUpcoming(requireCurrentUserId());
    }
}
