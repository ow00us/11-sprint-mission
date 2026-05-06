package com.sprint.mission.discodeit.service.basic;

import com.sprint.mission.discodeit.dto.data.MessageDto;
import com.sprint.mission.discodeit.dto.request.BinaryContentCreateRequest;
import com.sprint.mission.discodeit.dto.request.MessageCreateRequest;
import com.sprint.mission.discodeit.dto.request.MessageUpdateRequest;
import com.sprint.mission.discodeit.dto.response.PageResponse;
import com.sprint.mission.discodeit.entity.BinaryContent;
import com.sprint.mission.discodeit.entity.Channel;
import com.sprint.mission.discodeit.entity.Message;
import com.sprint.mission.discodeit.entity.User;
import com.sprint.mission.discodeit.mapper.MessageMapper;
import com.sprint.mission.discodeit.mapper.PageMapper;
import com.sprint.mission.discodeit.repository.BinaryContentRepository;
import com.sprint.mission.discodeit.repository.ChannelRepository;
import com.sprint.mission.discodeit.repository.MessageRepository;
import com.sprint.mission.discodeit.repository.UserRepository;
import com.sprint.mission.discodeit.service.MessageService;
import com.sprint.mission.discodeit.storage.BinaryContentStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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
public class BasicMessageService implements MessageService {

  private final MessageRepository messageRepository;
  private final ChannelRepository channelRepository;
  private final UserRepository userRepository;
  private final BinaryContentRepository binaryContentRepository;
  private final MessageMapper messageMapper;
  private final BinaryContentStorage binaryContentStorage;
  private final PageMapper pageMapper;

  @Transactional
  @Override
  public MessageDto create(MessageCreateRequest messageCreateRequest,
                        List<BinaryContentCreateRequest> binaryContentCreateRequests) {
    UUID channelId = messageCreateRequest.channelId();
    UUID authorId = messageCreateRequest.authorId();
    log.debug("Creating message channelId={}, authorId={}, attachmentCount={}",
            channelId, authorId, binaryContentCreateRequests.size());

    Channel channel = channelRepository.findById(channelId)
            .orElseThrow(() -> new NoSuchElementException("Channel with id " + channelId + " does not exist"));
    User author = userRepository.findById(authorId)
            .orElseThrow(() -> new NoSuchElementException("Author with id " + authorId + " does not exist"));

    List<BinaryContent> attachments = binaryContentCreateRequests.stream()
            .map(req -> {
              byte[] bytes = req.bytes();
              log.debug("Uploading message attachment channelId={}, fileName={}, size={}, contentType={}",
                      channelId, req.fileName(), bytes.length, req.contentType());
              BinaryContent saved = binaryContentRepository.save(
                      new BinaryContent(req.fileName(), (long) bytes.length, req.contentType())
              );
              binaryContentStorage.put(saved.getId(), bytes);
              return saved;
            })
            .toList();

    Message message = new Message(messageCreateRequest.content(), channel, author, attachments);
    Message createdMessage = messageRepository.save(message);
    log.info("Message created id={}, channelId={}, authorId={}, attachmentCount={}",
            createdMessage.getId(), channelId, authorId, attachments.size());
    return messageMapper.toDto(createdMessage);
  }

  @Override
  public MessageDto find(UUID messageId) {
    Message message = messageRepository.findById(messageId)
            .orElseThrow(() -> new NoSuchElementException("Message with id " + messageId + " not found"));
    return messageMapper.toDto(message);
  }

  @Override
  public PageResponse<MessageDto> findAllByChannelId(UUID channelId, Instant cursor, Pageable pageable) {
    Slice<Message> slice = cursor == null
            ? messageRepository.findAllByChannelIdOrderByCreatedAtDesc(channelId, pageable)
            : messageRepository.findAllByChannelIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                    channelId,
                    cursor,
                    pageable
            );
    if (cursor != null) {
      return pageMapper.toCursorResponse(slice, messageMapper::toDto, Message::getCreatedAt);
    }
    return pageMapper.toResponse(slice, messageMapper::toDto);
  }

  @Transactional
  @Override
  public MessageDto update(UUID messageId, MessageUpdateRequest request) {
    log.debug("Updating message id={}", messageId);
    Message message = messageRepository.findById(messageId)
            .orElseThrow(() -> new NoSuchElementException("Message with id " + messageId + " not found"));
    message.update(request.newContent());
    Message updatedMessage = messageRepository.save(message);
    log.info("Message updated id={}", updatedMessage.getId());
    return messageMapper.toDto(updatedMessage);
  }

  @Transactional
  @Override
  public void delete(UUID messageId) {
    log.debug("Deleting message id={}", messageId);
    Message message = messageRepository.findById(messageId)
            .orElseThrow(() -> new NoSuchElementException("Message with id " + messageId + " not found"));

    message.getAttachments()
            .forEach(attachment -> binaryContentRepository.deleteById(attachment.getId()));

    messageRepository.deleteById(messageId);
    log.info("Message deleted id={}, attachmentCount={}",
            messageId, message.getAttachments().size());
  }
}
