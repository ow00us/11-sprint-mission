package com.sprint.mission.discodeit.controller.api;

import com.sprint.mission.discodeit.dto.data.MessageDto;
import com.sprint.mission.discodeit.dto.request.MessageCreateRequest;
import com.sprint.mission.discodeit.dto.request.MessageUpdateRequest;
import com.sprint.mission.discodeit.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Message", description = "Message API")
public interface MessageApi {

  @Operation(summary = "Create message")
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "201",
          description = "Message created",
          content = @Content(schema = @Schema(implementation = MessageDto.class))
      ),
      @ApiResponse(
          responseCode = "404",
          description = "Channel or author not found",
          content = @Content(examples = @ExampleObject(value = "Channel | Author with id {channelId | authorId} not found"))
      ),
  })
  ResponseEntity<MessageDto> create(
      @Parameter(
          description = "Message create request",
          content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
      ) MessageCreateRequest messageCreateRequest,
      @Parameter(
          description = "Attachments",
          content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
      ) List<MultipartFile> attachments
  );

  @Operation(summary = "Update message")
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Message updated",
          content = @Content(schema = @Schema(implementation = MessageDto.class))
      ),
      @ApiResponse(
          responseCode = "404",
          description = "Message not found",
          content = @Content(examples = @ExampleObject(value = "Message with id {messageId} not found"))
      ),
  })
  ResponseEntity<MessageDto> update(
      @Parameter(description = "Message ID") UUID messageId,
      @Parameter(description = "Message update request") MessageUpdateRequest request
  );

  @Operation(summary = "Delete message")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "204", description = "Message deleted"),
      @ApiResponse(
          responseCode = "404",
          description = "Message not found",
          content = @Content(examples = @ExampleObject(value = "Message with id {messageId} not found"))
      ),
  })
  ResponseEntity<Void> delete(@Parameter(description = "Message ID") UUID messageId);

  @Operation(summary = "Find messages by channel")
  @ApiResponse(
      responseCode = "200",
      description = "Messages found",
      content = @Content(schema = @Schema(implementation = PageResponse.class))
  )
  ResponseEntity<PageResponse<MessageDto>> findAllByChannelId(
      @Parameter(description = "Channel ID") UUID channelId,
      @Parameter(description = "Cursor") Instant cursor,
      @Parameter(description = "Pageable") Pageable pageable
  );
}
