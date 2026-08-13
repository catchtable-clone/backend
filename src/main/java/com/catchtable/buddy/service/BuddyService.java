package com.catchtable.buddy.service;

import com.catchtable.buddy.dto.request.BuddySendRequest;
import com.catchtable.buddy.dto.response.BuddyListResponse;
import com.catchtable.buddy.dto.response.BuddyPendingRequestResponse;
import com.catchtable.buddy.dto.response.BuddyRequestResponse;
import com.catchtable.buddy.entity.Buddy;
import com.catchtable.buddy.entity.BuddyStatus;
import com.catchtable.buddy.repository.BuddyRepository;
import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
import com.catchtable.user.entity.User;
import com.catchtable.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BuddyService {

    private final BuddyRepository buddyRepository;
    private final UserRepository userRepository;

    @Transactional
    public BuddyRequestResponse sendRequest(Long requesterId, BuddySendRequest request) {
        if (requesterId.equals(request.receiverId())) {
            throw new CustomException(ErrorCode.BUDDY_SELF_REQUEST);
        }

        User requester = userRepository.getById(requesterId);
        User receiver = userRepository.getById(request.receiverId());

        boolean alreadyExists = buddyRepository.existsByRequesterIdAndReceiverIdAndStatusIn(
                requesterId, request.receiverId(),
                List.of(BuddyStatus.PENDING, BuddyStatus.ACCEPTED)
        ) || buddyRepository.existsByRequesterIdAndReceiverIdAndStatusIn(
                request.receiverId(), requesterId,
                List.of(BuddyStatus.PENDING, BuddyStatus.ACCEPTED)
        );

        if (alreadyExists) {
            throw new CustomException(ErrorCode.BUDDY_ALREADY_EXISTS);
        }

        Buddy buddy = Buddy.builder()
                .requester(requester)
                .receiver(receiver)
                .build();

        Buddy saved = buddyRepository.save(buddy);
        return new BuddyRequestResponse(saved.getId());
    }

    @Transactional(readOnly = true)
    public List<BuddyPendingRequestResponse> getPendingRequests(Long userId) {
        return buddyRepository.findByReceiverIdAndStatus(userId, BuddyStatus.PENDING)
                .stream()
                .map(b -> new BuddyPendingRequestResponse(
                        b.getId(),
                        b.getRequester().getId(),
                        b.getRequester().getNickname(),
                        b.getRequester().getProfileImage(),
                        b.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void acceptRequest(Long userId, Long requestId) {
        Buddy buddy = buddyRepository.getById(requestId);
        buddy.validateReceiver(userId);

        if (buddy.getStatus() != BuddyStatus.PENDING) {
            throw new CustomException(ErrorCode.BUDDY_REQUEST_NOT_PENDING);
        }

        buddy.accept();
    }

    @Transactional
    public void rejectRequest(Long userId, Long requestId) {
        Buddy buddy = buddyRepository.getById(requestId);
        buddy.validateReceiver(userId);

        if (buddy.getStatus() != BuddyStatus.PENDING) {
            throw new CustomException(ErrorCode.BUDDY_REQUEST_NOT_PENDING);
        }

        buddy.reject();
    }

    @Transactional(readOnly = true)
    public List<BuddyListResponse> getBuddies(Long userId) {
        return buddyRepository.findAcceptedBuddiesByUserId(userId)
                .stream()
                .map(b -> {
                    User other = b.getRequester().getId().equals(userId)
                            ? b.getReceiver()
                            : b.getRequester();
                    return new BuddyListResponse(b.getId(), other.getId(), other.getNickname(), other.getProfileImage());
                })
                .toList();
    }

    @Transactional
    public void deleteBuddy(Long userId, Long buddyId) {
        Buddy buddy = buddyRepository.getById(buddyId);

        boolean isParticipant = buddy.getRequester().getId().equals(userId)
                || buddy.getReceiver().getId().equals(userId);

        if (!isParticipant) {
            throw new CustomException(ErrorCode.NOT_BUDDY_PARTICIPANT);
        }

        buddyRepository.delete(buddy);
    }
}
