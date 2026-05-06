package com.sprint.mission.discodeit.service.basic;

import com.sprint.mission.discodeit.dto.data.ChannelDto;
import com.sprint.mission.discodeit.dto.data.UserDto;
import com.sprint.mission.discodeit.dto.request.PrivateChannelCreateRequest;
import com.sprint.mission.discodeit.dto.request.PublicChannelCreateRequest;
import com.sprint.mission.discodeit.dto.request.PublicChannelUpdateRequest;
import com.sprint.mission.discodeit.entity.Channel;
import com.sprint.mission.discodeit.entity.ChannelType;
import com.sprint.mission.discodeit.entity.ReadStatus;
import com.sprint.mission.discodeit.entity.User;
import com.sprint.mission.discodeit.entity.UserStatus;
import com.sprint.mission.discodeit.mapper.ChannelMapper;
import com.sprint.mission.discodeit.mapper.UserMapper;
import com.sprint.mission.discodeit.repository.ChannelRepository;
import com.sprint.mission.discodeit.repository.MessageRepository;
import com.sprint.mission.discodeit.repository.ReadStatusRepository;
import com.sprint.mission.discodeit.repository.UserRepository;
import com.sprint.mission.discodeit.repository.UserStatusRepository;
import com.sprint.mission.discodeit.service.ChannelService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
@Service
public class BasicChannelService implements ChannelService {

  private final ChannelRepository channelRepository;
  private final ReadStatusRepository readStatusRepository;
  private final MessageRepository messageRepository;
  private final UserRepository userRepository;
  private final UserStatusRepository userStatusRepository;
  private final ChannelMapper channelMapper;
  private final UserMapper userMapper;

  @Transactional
  @Override
  public ChannelDto create(PublicChannelCreateRequest request) {
    log.debug("Creating public channel name={}", request.name());
    Channel channel = new Channel(ChannelType.PUBLIC, request.name(), request.description());
    Channel createdChannel = channelRepository.save(channel);
    log.info("Public channel created id={}, name={}", createdChannel.getId(), createdChannel.getName());
    return channelMapper.toDto(createdChannel, List.of(), createdChannel.getCreatedAt());
  }

  @Transactional
  @Override
  public ChannelDto create(PrivateChannelCreateRequest request) {
    log.debug("Creating private channel participantCount={}", request.participantIds().size());
    Channel channel = new Channel(ChannelType.PRIVATE, null, null);
    Channel createdChannel = channelRepository.save(channel);

    List<User> participants = request.participantIds().stream()
        .map(userId -> userRepository.findById(userId)
            .orElseThrow(() -> new NoSuchElementException("User with id " + userId + " not found")))
        .toList();

    participants.stream()
        .map(user -> new ReadStatus(user, createdChannel, createdChannel.getCreatedAt()))
        .forEach(readStatusRepository::save);

    Map<UUID, UserStatus> userStatusByUserId = loadUserStatuses(
        participants.stream().map(User::getId).toList()
    );
    List<UserDto> participantDtos = participants.stream()
        .map(user -> userMapper.toDto(user, userStatusByUserId.get(user.getId())))
        .toList();
    log.info("Private channel created id={}, participantCount={}",
        createdChannel.getId(), participantDtos.size());
    return channelMapper.toDto(createdChannel, participantDtos, createdChannel.getCreatedAt());
  }

  @Override
  public ChannelDto find(UUID channelId) {
    Channel channel = channelRepository.findById(channelId)
        .orElseThrow(() -> new NoSuchElementException("Channel with id " + channelId + " not found"));
    return toDto(channel, loadLastMessageAtByChannelId(List.of(channel.getId())),
        loadParticipantsByChannelId(List.of(channel.getId())));
  }

  @Override
  public List<ChannelDto> findAllByUserId(UUID userId) {
    List<UUID> subscribedChannelIds = readStatusRepository.findAllByUserId(userId).stream()
        .map(readStatus -> readStatus.getChannel().getId())
        .toList();

    List<Channel> channels = new ArrayList<>(channelRepository.findAllByType(ChannelType.PUBLIC));
    if (!subscribedChannelIds.isEmpty()) {
      channelRepository.findAllById(subscribedChannelIds).stream()
          .filter(channel -> channel.getType().equals(ChannelType.PRIVATE))
          .forEach(channels::add);
    }

    return toDtos(channels);
  }

  @Transactional
  @Override
  public ChannelDto update(UUID channelId, PublicChannelUpdateRequest request) {
    log.debug("Updating channel id={}", channelId);
    Channel channel = channelRepository.findById(channelId)
        .orElseThrow(() -> new NoSuchElementException("Channel with id " + channelId + " not found"));
    if (channel.getType().equals(ChannelType.PRIVATE)) {
      log.warn("Private channel update rejected id={}", channelId);
      throw new IllegalArgumentException("Private channel cannot be updated");
    }
    channel.update(request.newName(), request.newDescription());
    log.info("Channel updated id={}", channelId);
    return toDto(channel, loadLastMessageAtByChannelId(List.of(channel.getId())), Map.of());
  }

  @Transactional
  @Override
  public void delete(UUID channelId) {
    log.debug("Deleting channel id={}", channelId);
    Channel channel = channelRepository.findById(channelId)
        .orElseThrow(() -> new NoSuchElementException("Channel with id " + channelId + " not found"));

    messageRepository.deleteAllByChannelId(channel.getId());
    readStatusRepository.deleteAllByChannelId(channel.getId());
    channelRepository.deleteById(channelId);
    log.info("Channel deleted id={}", channelId);
  }

  private List<ChannelDto> toDtos(List<Channel> channels) {
    List<UUID> channelIds = channels.stream()
        .map(Channel::getId)
        .toList();
    Map<UUID, Instant> lastMessageAtByChannelId = loadLastMessageAtByChannelId(channelIds);
    List<UUID> privateChannelIds = channels.stream()
        .filter(channel -> channel.getType().equals(ChannelType.PRIVATE))
        .map(Channel::getId)
        .toList();
    Map<UUID, List<UserDto>> participantsByChannelId = loadParticipantsByChannelId(privateChannelIds);

    return channels.stream()
        .map(channel -> toDto(channel, lastMessageAtByChannelId, participantsByChannelId))
        .toList();
  }

  private ChannelDto toDto(Channel channel, Map<UUID, Instant> lastMessageAtByChannelId,
                           Map<UUID, List<UserDto>> participantsByChannelId) {
    Instant lastMessageAt = lastMessageAtByChannelId.getOrDefault(
        channel.getId(),
        channel.getCreatedAt()
    );
    List<UserDto> participants = participantsByChannelId.getOrDefault(channel.getId(), List.of());
    return channelMapper.toDto(channel, participants, lastMessageAt);
  }

  private Map<UUID, Instant> loadLastMessageAtByChannelId(Collection<UUID> channelIds) {
    if (channelIds.isEmpty()) {
      return Map.of();
    }
    return messageRepository.findLastMessageAtByChannelIds(channelIds).stream()
        .collect(Collectors.toMap(
            MessageRepository.ChannelLastMessageAt::getChannelId,
            MessageRepository.ChannelLastMessageAt::getLastMessageAt
        ));
  }

  private Map<UUID, List<UserDto>> loadParticipantsByChannelId(Collection<UUID> channelIds) {
    if (channelIds.isEmpty()) {
      return Map.of();
    }
    List<ReadStatus> readStatuses = readStatusRepository.findAllByChannelIdIn(channelIds);
    Map<UUID, UserStatus> userStatusByUserId = loadUserStatuses(
        readStatuses.stream()
            .map(readStatus -> readStatus.getUser().getId())
            .toList()
    );

    return readStatuses.stream()
        .collect(Collectors.groupingBy(
            readStatus -> readStatus.getChannel().getId(),
            LinkedHashMap::new,
            Collectors.mapping(
                readStatus -> userMapper.toDto(
                    readStatus.getUser(),
                    userStatusByUserId.get(readStatus.getUser().getId())
                ),
                Collectors.toList()
            )
        ));
  }

  private Map<UUID, UserStatus> loadUserStatuses(Collection<UUID> userIds) {
    if (userIds.isEmpty()) {
      return Map.of();
    }
    return userStatusRepository.findAllByUserIdIn(userIds).stream()
        .collect(Collectors.toMap(
            userStatus -> userStatus.getUser().getId(),
            Function.identity()
        ));
  }
}
