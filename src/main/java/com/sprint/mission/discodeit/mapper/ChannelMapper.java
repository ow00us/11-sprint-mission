package com.sprint.mission.discodeit.mapper;

import com.sprint.mission.discodeit.dto.data.ChannelDto;
import com.sprint.mission.discodeit.dto.data.UserDto;
import com.sprint.mission.discodeit.entity.Channel;
import java.time.Instant;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ChannelMapper {

    @Mapping(source = "participants", target = "participants")
    @Mapping(source = "lastMessageAt", target = "lastMessageAt")
    ChannelDto toDto(Channel channel, List<UserDto> participants, Instant lastMessageAt);
}
