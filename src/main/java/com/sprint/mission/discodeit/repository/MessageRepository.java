package com.sprint.mission.discodeit.repository;

import com.sprint.mission.discodeit.entity.Message;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

  interface ChannelLastMessageAt {
    UUID getChannelId();

    Instant getLastMessageAt();
  }

  List<Message> findAllByChannelId(UUID channelId);

  void deleteAllByChannelId(UUID channelId);

  @Override
  @EntityGraph(attributePaths = {"channel", "author", "author.profile", "attachments"})
  java.util.Optional<Message> findById(UUID id);

  @EntityGraph(attributePaths = {"channel", "author", "author.profile", "attachments"})
  Slice<Message> findAllByChannelIdOrderByCreatedAtDesc(UUID channelId, Pageable pageable);

  @EntityGraph(attributePaths = {"channel", "author", "author.profile", "attachments"})
  Slice<Message> findAllByChannelIdAndCreatedAtBeforeOrderByCreatedAtDesc(
      UUID channelId,
      Instant cursor,
      Pageable pageable
  );

  @Query("""
      select m.channel.id as channelId, max(m.createdAt) as lastMessageAt
      from Message m
      where m.channel.id in :channelIds
      group by m.channel.id
      """)
  List<ChannelLastMessageAt> findLastMessageAtByChannelIds(
      @Param("channelIds") Collection<UUID> channelIds
  );

}
