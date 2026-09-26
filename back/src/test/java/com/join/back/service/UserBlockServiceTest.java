package com.join.back.service;

import com.join.back.model.dto.BlockStatusResponse;
import com.join.back.model.entity.UserBlock;
import com.join.back.repository.UserBlockRepository;
import com.join.back.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBlockServiceTest {

    @Mock private UserBlockRepository userBlockRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private UserBlockService service;

    @Test
    void blocksAnotherUser() {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(userBlockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(false, true);

        BlockStatusResponse status = service.block(1L, 2L);

        verify(userBlockRepository).saveAndFlush(any(UserBlock.class));
        assertTrue(status.blockedByMe());
        assertFalse(status.blockedMe());
    }

    @Test
    void doesNotDuplicateExistingBlock() {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(userBlockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);

        service.block(1L, 2L);

        verify(userBlockRepository, never()).saveAndFlush(any());
    }

    @Test
    void cannotBlockYourself() {
        assertThrows(UserActionException.class, () -> service.block(1L, 1L));
    }
}
