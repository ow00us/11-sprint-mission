package com.sprint.mission.discodeit.service.basic;

import com.sprint.mission.discodeit.dto.data.ReadStatusDto;
import com.sprint.mission.discodeit.dto.request.ReadStatusCreateRequest;
import com.sprint.mission.discodeit.dto.request.ReadStatusUpdateRequest;
import com.sprint.mission.discodeit.entity.Channel;
import com.sprint.mission.discodeit.entity.ReadStatus;
import com.sprint.mission.discodeit.entity.User;
import com.sprint.mission.discodeit.mapper.ReadStatusMapper;
import com.sprint.mission.discodeit.repository.ChannelRepository;
import com.sprint.mission.discodeit.repository.ReadStatusRepository;
import com.sprint.mission.discodeit.repository.UserRepository;
import com.sprint.mission.discodeit.service.ReadStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
@Service
public class BasicReadStatusService implements ReadStatusService {

  private final ReadStatusRepository readStatusRepository;
  private final UserRepository userRepository;
  private final ChannelRepository channelRepository;
  private final ReadStatusMapper readStatusMapper;

  @Transactional
  @Override
  public ReadStatusDto create(ReadStatusCreateRequest request) {
    UUID userId = request.userId();
    UUID channelId = request.channelId();
    log.debug("Creating read status userId={}, channelId={}", userId, channelId);

    User user = userRepository.findById(userId)
            .orElseThrow(() -> new NoSuchElementException("User with id " + userId + " does not exist"));
    Channel channel = channelRepository.findById(channelId)
            .orElseThrow(() -> new NoSuchElementException("Channel with id " + channelId + " does not exist"));

    boolean alreadyExists = readStatusRepository.findAllByUserId(userId).stream()
            .anyMatch(rs -> rs.getChannel().getId().equals(channelId));

    if (alreadyExists) {
      log.debug("Read status already exists userId={}, channelId={}", userId, channelId);
      ReadStatus existingReadStatus = readStatusRepository.findAllByUserId(userId).stream()
              .filter(rs -> rs.getChannel().getId().equals(channelId))
              .findFirst().get();
      return readStatusMapper.toDto(existingReadStatus);
    }

    ReadStatus readStatus = new ReadStatus(user, channel, request.lastReadAt());
    ReadStatus createdReadStatus = readStatusRepository.save(readStatus);
    log.info("Read status created id={}, userId={}, channelId={}",
        createdReadStatus.getId(), userId, channelId);
    return readStatusMapper.toDto(createdReadStatus);
  }

  @Override
  public ReadStatusDto find(UUID readStatusId) {
    ReadStatus readStatus = readStatusRepository.findById(readStatusId)
        .orElseThrow(
            () -> new NoSuchElementException("ReadStatus with id " + readStatusId + " not found"));
    return readStatusMapper.toDto(readStatus);
  }

  @Override
  public List<ReadStatusDto> findAllByUserId(UUID userId) {
    return readStatusRepository.findAllByUserId(userId).stream()
        .map(readStatusMapper::toDto)
        .toList();
  }

  @Transactional
  @Override
  public ReadStatusDto update(UUID readStatusId, ReadStatusUpdateRequest request) {
    Instant newLastReadAt = request.newLastReadAt();
    log.debug("Updating read status id={}", readStatusId);
    ReadStatus readStatus = readStatusRepository.findById(readStatusId)
        .orElseThrow(
            () -> new NoSuchElementException("ReadStatus with id " + readStatusId + " not found"));
    readStatus.update(newLastReadAt);
    ReadStatus updatedReadStatus = readStatusRepository.save(readStatus);
    log.info("Read status updated id={}", updatedReadStatus.getId());
    return readStatusMapper.toDto(updatedReadStatus);
  }

  @Transactional
  @Override
  public void delete(UUID readStatusId) {
    if (!readStatusRepository.existsById(readStatusId)) {
      log.warn("Read status delete rejected: not found id={}", readStatusId);
      throw new NoSuchElementException("ReadStatus with id " + readStatusId + " not found");
    }
    readStatusRepository.deleteById(readStatusId);
    log.info("Read status deleted id={}", readStatusId);
  }
}
