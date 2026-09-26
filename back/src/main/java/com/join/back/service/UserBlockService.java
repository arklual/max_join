package com.join.back.service;

import com.join.back.model.dto.BlockStatusResponse;
import com.join.back.model.dto.BlockedUserResponse;
import com.join.back.model.entity.User;
import com.join.back.model.entity.UserBlock;
import com.join.back.repository.UserBlockRepository;
import com.join.back.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Personal black list: once A blocks B, neither can write to the other in personal chats,
 * they are no longer matched and can't meet in the same event group.
 */
@Service
@RequiredArgsConstructor
public class UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;

    @Transactional(transactionManager = "transactionManager")
    public BlockStatusResponse block(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new UserActionException("Нельзя заблокировать самого себя");
        }
        if (!userRepository.existsById(blockedId)) {
            throw new EntityNotFoundException("User not found with id: " + blockedId);
        }
        if (!userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            try {
                userBlockRepository.saveAndFlush(UserBlock.builder()
                        .blockerId(blockerId)
                        .blockedId(blockedId)
                        .createdAt(LocalDateTime.now())
                        .build());
            } catch (DataIntegrityViolationException e) {
                // concurrent double tap — already blocked
            }
        }
        return status(blockerId, blockedId);
    }

    @Transactional(transactionManager = "transactionManager")
    public BlockStatusResponse unblock(Long blockerId, Long blockedId) {
        userBlockRepository.deletePair(blockerId, blockedId);
        return status(blockerId, blockedId);
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public BlockStatusResponse status(Long userId, Long otherId) {
        return new BlockStatusResponse(otherId, hasBlocked(userId, otherId), hasBlocked(otherId, userId));
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public List<BlockedUserResponse> getBlockedUsers(Long userId) {
        List<UserBlock> blocks = userBlockRepository.findByBlockerIdOrderByCreatedAtDesc(userId);
        Map<Long, User> users = userRepository.findAllById(blocks.stream().map(UserBlock::getBlockedId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return blocks.stream()
                .map(b -> {
                    User u = users.get(b.getBlockedId());
                    return new BlockedUserResponse(b.getBlockedId(),
                            u != null ? u.getFirstName() : null,
                            u != null ? u.getPhoto() : null,
                            b.getCreatedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public boolean hasBlocked(Long blockerId, Long blockedId) {
        return userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    /** Users the given one blocked or was blocked by. */
    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Set<Long> relatedUserIds(Long userId) {
        return new HashSet<>(userBlockRepository.findRelatedUserIds(userId));
    }
}
