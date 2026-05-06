package com.sprint.mission.discodeit.service.basic;

import com.sprint.mission.discodeit.dto.data.UserDto;
import com.sprint.mission.discodeit.dto.request.BinaryContentCreateRequest;
import com.sprint.mission.discodeit.dto.request.UserCreateRequest;
import com.sprint.mission.discodeit.dto.request.UserUpdateRequest;
import com.sprint.mission.discodeit.entity.BinaryContent;
import com.sprint.mission.discodeit.entity.User;
import com.sprint.mission.discodeit.entity.UserStatus;
import com.sprint.mission.discodeit.mapper.UserMapper;
import com.sprint.mission.discodeit.repository.BinaryContentRepository;
import com.sprint.mission.discodeit.repository.UserRepository;
import com.sprint.mission.discodeit.repository.UserStatusRepository;
import com.sprint.mission.discodeit.service.UserService;
import com.sprint.mission.discodeit.storage.BinaryContentStorage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
@Service
public class BasicUserService implements UserService {

  private final UserRepository userRepository;
  private final BinaryContentRepository binaryContentRepository;
  private final UserStatusRepository userStatusRepository;
  private final UserMapper userMapper;
  private final BinaryContentStorage binaryContentStorage;

  @Transactional
  @Override
  public UserDto create(UserCreateRequest userCreateRequest,
                        Optional<BinaryContentCreateRequest> optionalProfileCreateRequest) {
    String username = userCreateRequest.username();
    String email = userCreateRequest.email();
    log.debug("Creating user username={}, profileProvided={}", username,
        optionalProfileCreateRequest.isPresent());

    if (userRepository.existsByEmail(email)) {
      log.warn("User create rejected: duplicate email");
      throw new IllegalArgumentException("User with email " + email + " already exists");
    }
    if (userRepository.existsByUsername(username)) {
      log.warn("User create rejected: duplicate username={}", username);
      throw new IllegalArgumentException("User with username " + username + " already exists");
    }

    BinaryContent nullableProfile = optionalProfileCreateRequest
        .map(profileRequest -> saveProfile(profileRequest, "create", null))
        .orElse(null);

    User user = new User(username, email, userCreateRequest.password(), nullableProfile);
    User createdUser = userRepository.save(user);

    UserStatus userStatus = new UserStatus(createdUser, Instant.now());
    userStatusRepository.save(userStatus);

    log.info("User created id={}, username={}", createdUser.getId(), createdUser.getUsername());
    return userMapper.toDto(createdUser, userStatus);
  }

  @Override
  public UserDto find(UUID userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new NoSuchElementException("User with id " + userId + " not found"));
    UserStatus userStatus = userStatusRepository.findByUserId(userId).orElse(null);
    return userMapper.toDto(user, userStatus);
  }

  @Override
  public List<UserDto> findAll() {
    List<User> users = userRepository.findAll();

    List<UUID> userIds = users.stream()
        .map(User::getId)
        .toList();

    Map<UUID, UserStatus> userStatusMap = userStatusRepository.findAllByUserIdIn(userIds)
        .stream()
        .collect(Collectors.toMap(
            userStatus -> userStatus.getUser().getId(),
            userStatus -> userStatus
        ));

    return users.stream()
        .map(user -> userMapper.toDto(user, userStatusMap.get(user.getId())))
        .toList();
  }

  @Transactional
  @Override
  public UserDto update(UUID userId, UserUpdateRequest userUpdateRequest,
                        Optional<BinaryContentCreateRequest> optionalProfileCreateRequest) {
    log.debug("Updating user id={}, profileProvided={}", userId,
        optionalProfileCreateRequest.isPresent());
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new NoSuchElementException("User with id " + userId + " not found"));

    String newUsername = userUpdateRequest.newUsername();
    String newEmail = userUpdateRequest.newEmail();
    if (userRepository.existsByEmail(newEmail)) {
      log.warn("User update rejected: duplicate email userId={}", userId);
      throw new IllegalArgumentException("User with email " + newEmail + " already exists");
    }
    if (userRepository.existsByUsername(newUsername)) {
      log.warn("User update rejected: duplicate username={} userId={}", newUsername, userId);
      throw new IllegalArgumentException("User with username " + newUsername + " already exists");
    }

    BinaryContent nullableProfile = optionalProfileCreateRequest
        .map(profileRequest -> {
          Optional.ofNullable(user.getProfile())
              .ifPresent(profile -> {
                log.debug("Deleting previous profile userId={}, profileId={}", userId, profile.getId());
                binaryContentRepository.deleteById(profile.getId());
              });
          return saveProfile(profileRequest, "update", userId);
        })
        .orElse(null);

    user.update(newUsername, newEmail, userUpdateRequest.newPassword(), nullableProfile);
    User updatedUser = userRepository.save(user);

    UserStatus userStatus = userStatusRepository.findByUserId(userId).orElse(null);
    log.info("User updated id={}", updatedUser.getId());
    return userMapper.toDto(updatedUser, userStatus);
  }

  @Transactional
  @Override
  public void delete(UUID userId) {
    log.debug("Deleting user id={}", userId);
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new NoSuchElementException("User with id " + userId + " not found"));

    Optional.ofNullable(user.getProfile())
        .ifPresent(profile -> binaryContentRepository.deleteById(profile.getId()));
    userStatusRepository.deleteByUserId(userId);
    userRepository.deleteById(userId);
    log.info("User deleted id={}", userId);
  }

  private BinaryContent saveProfile(BinaryContentCreateRequest profileRequest, String action,
                                    UUID userId) {
    byte[] bytes = profileRequest.bytes();
    log.debug("Uploading profile action={}, userId={}, fileName={}, size={}, contentType={}",
        action, userId, profileRequest.fileName(), bytes.length, profileRequest.contentType());
    BinaryContent binaryContent = new BinaryContent(
        profileRequest.fileName(),
        (long) bytes.length,
        profileRequest.contentType()
    );
    BinaryContent saved = binaryContentRepository.save(binaryContent);
    binaryContentStorage.put(saved.getId(), bytes);
    return saved;
  }
}
