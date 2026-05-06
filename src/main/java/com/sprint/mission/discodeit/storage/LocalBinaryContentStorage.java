package com.sprint.mission.discodeit.storage;

import com.sprint.mission.discodeit.dto.data.BinaryContentDto;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "discodeit.storage.type", havingValue = "local")
public class LocalBinaryContentStorage implements BinaryContentStorage {

    private final Path root;

    public LocalBinaryContentStorage(
            @Value("${discodeit.storage.local.root-path}") String rootPath) {
        this.root = Paths.get(rootPath);
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(root);
            log.info("Storage initialized at {}", root.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to initialize storage root={}", root.toAbsolutePath(), e);
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    private Path resolvePath(UUID id) {
        return root.resolve(id.toString());
    }

    @Override
    public UUID put(UUID id, byte[] bytes) {
        try {
            Files.write(resolvePath(id), bytes);
            log.debug("Binary content stored id={}, size={}", id, bytes.length);
            return id;
        } catch (IOException e) {
            log.error("Failed to store binary content id={}", id, e);
            throw new RuntimeException("Failed to store binary content: " + id, e);
        }
    }

    @Override
    public InputStream get(UUID id) {
        try {
            log.debug("Loading binary content id={}", id);
            return Files.newInputStream(resolvePath(id));
        } catch (IOException e) {
            log.error("Failed to load binary content id={}", id, e);
            throw new RuntimeException("Failed to load binary content: " + id, e);
        }
    }

    @Override
    public ResponseEntity<?> download(BinaryContentDto dto) {
        log.info("Downloading binary content id={}, fileName={}, size={}",
                dto.id(), dto.fileName(), dto.size());
        InputStream inputStream = get(dto.id());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(dto.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .contentType(MediaType.parseMediaType(dto.contentType()))
                .contentLength(dto.size())
                .body(new InputStreamResource(inputStream));
    }
}
