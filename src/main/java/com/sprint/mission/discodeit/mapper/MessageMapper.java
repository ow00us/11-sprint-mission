package com.sprint.mission.discodeit.mapper;

import com.sprint.mission.discodeit.dto.data.MessageDto;
import com.sprint.mission.discodeit.dto.data.UserDto;
import com.sprint.mission.discodeit.entity.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", uses = BinaryContentMapper.class)
public abstract class MessageMapper {

    @Autowired
    protected UserMapper userMapper;

    @Mapping(source = "channel.id", target = "channelId")
    @Mapping(target = "author", expression = "java(toAuthorDto(message))")
    public abstract MessageDto toDto(Message message);

    protected UserDto toAuthorDto(Message message) {
        return message.getAuthor() == null ? null : userMapper.toDto(message.getAuthor(), null);
    }
}
