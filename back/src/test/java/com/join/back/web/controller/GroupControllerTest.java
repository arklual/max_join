package com.join.back.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.join.back.model.dto.CreateGroupRequest;
import com.join.back.model.dto.GroupMemberResponse;
import com.join.back.model.dto.GroupResponse;
import com.join.back.model.dto.JoinGroupResponse;
import com.join.back.model.dto.LeaveGroupResponse;
import com.join.back.model.entity.User;
import com.join.back.repository.UserRepository;
import com.join.back.security.MaxAuthenticationToken;
import com.join.back.service.GroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GroupController.class,
        excludeAutoConfiguration = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@ActiveProfiles("test")
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GroupService groupService;

    @MockBean
    private UserRepository userRepository;

    private GroupResponse sampleGroupResponse;

    @BeforeEach
    void setUp() {
        GroupMemberResponse creator = new GroupMemberResponse(
                1L, "Алексей", "https://photo.url", "CREATOR", LocalDateTime.now());

        sampleGroupResponse = new GroupResponse(
                100L, 10L, "Test Event", LocalDate.of(2026, 4, 15),
                "Test Group", "Description", 5, 1,
                "OPEN", 200L, creator, List.of(creator), LocalDateTime.now()
        );
    }

    @Test
    void shouldCreateGroup() throws Exception {
        setupAuthentication();
        CreateGroupRequest request = new CreateGroupRequest("Test Group", "Description", 5);

        when(groupService.createGroup(eq(1L), eq(10L), any(CreateGroupRequest.class)))
                .thenReturn(sampleGroupResponse);

        mockMvc.perform(post("/api/events/10/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.eventId").value(10))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.groupChatId").value(200));
    }

    @Test
    void shouldReturnBadRequestForInvalidMaxSize() throws Exception {
        setupAuthentication();
        CreateGroupRequest request = new CreateGroupRequest("Test Group", null, 2); // < 3

        mockMvc.perform(post("/api/events/10/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetGroupsForEvent() throws Exception {
        Page<GroupResponse> page = new PageImpl<>(List.of(sampleGroupResponse), PageRequest.of(0, 20), 1);
        when(groupService.getGroupsForEvent(eq(10L), any())).thenReturn(page);

        mockMvc.perform(get("/api/events/10/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(100))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldGetGroupById() throws Exception {
        when(groupService.getGroupById(100L)).thenReturn(sampleGroupResponse);

        mockMvc.perform(get("/api/groups/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.title").value("Test Group"));
    }

    @Test
    void shouldJoinGroup() throws Exception {
        setupAuthentication();
        JoinGroupResponse joinResponse = new JoinGroupResponse(100L, 200L, "You have successfully joined the group");
        when(groupService.joinGroup(1L, 100L)).thenReturn(joinResponse);

        mockMvc.perform(post("/api/groups/100/join"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(100))
                .andExpect(jsonPath("$.groupChatId").value(200));
    }

    @Test
    void shouldLeaveGroup() throws Exception {
        setupAuthentication();
        LeaveGroupResponse leaveResponse = new LeaveGroupResponse(100L, "You have left the group");
        when(groupService.leaveGroup(1L, 100L)).thenReturn(leaveResponse);

        mockMvc.perform(post("/api/groups/100/leave"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(100))
                .andExpect(jsonPath("$.message").value("You have left the group"));
    }

    @Test
    void shouldGetMyGroups() throws Exception {
        setupAuthentication();
        Page<GroupResponse> page = new PageImpl<>(List.of(sampleGroupResponse), PageRequest.of(0, 20), 1);
        when(groupService.getMyGroups(eq(1L), any())).thenReturn(page);

        mockMvc.perform(get("/api/groups/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(100));
    }

    private void setupAuthentication() {
        User user = User.builder()
                .id(1L)
                .maxId(12345L)
                .build();
        when(userRepository.findByMaxId(12345L)).thenReturn(Optional.of(user));

        MaxAuthenticationToken auth = new MaxAuthenticationToken(12345L, "Test", "User", "testuser");
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
