package org.menu.api.service;

import org.menu.api.domain.StoredFile;
import org.menu.api.repository.StoredFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Keeps the bytes of accepted uploads, under names this class picks and within a fixed budget.
 *
 * <p><b>Nothing is written to disk.</b> An upload becomes a row in the same in-memory database as
 * everything else, so it lives exactly as long as the process does. That is the whole point: a
 * restart is a clean slate, and there is never anything left to sweep up. On disk the files
 * outlived the run that made them, and because every path that deletes one starts from a row the
 * restart had already dropped, nothing was ever going to remove them again.
 *
 * <p>The name a client sent is still never used to build anything. It survives only as a column,
 * and the storage name is generated here.
 *
 * <p><b>The budget.</b> A client that has learned to emit real image bytes gets every upload
 * accepted, and each acceptance costs memory now rather than disk — so the limit matters more
 * than it did, not less. The store keeps at most {@code menu.pictures.max-files} rows and
 * {@code menu.pictures.max-bytes} bytes, and drops the oldest to stay inside both.
 *
 * <p>Dropping the oldest rather than refusing the newest is deliberate. A refusal would depend on
 * how much a run had already uploaded, which would make two runs of the same fuzzer answer
 * differently to the same request — the one thing a measurement setup cannot afford. Eviction
 * leaves every documented status code exactly where it was. A picture row whose bytes have been
 * evicted still reads back fine, because nothing here ever serves those bytes: it is the same
 * state the twenty seeded pictures are in from the start.
 */
@Service
@Transactional
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final StoredFileRepository repository;
    private final int maxFiles;
    private final long maxBytes;

    public FileStorageService(StoredFileRepository repository,
                              @Value("${menu.pictures.max-files:200}") int maxFiles,
                              @Value("${menu.pictures.max-bytes:33554432}") long maxBytes) {
        this.repository = repository;
        this.maxFiles = maxFiles;
        this.maxBytes = maxBytes;
    }

    /** Stores the part and answers with the name it was stored under. */
    public String store(MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            // Reading back a part the container already buffered is not something a client can
            // provoke, so this is a genuine fault and travels as one.
            throw new UncheckedIOException(e);
        }
        String storedName = storedName(file.getOriginalFilename());
        repository.save(new StoredFile(storedName, content, file.getContentType()));
        evictWhileOverBudget();
        return storedName;
    }

    /** Removes stored bytes. Bytes that are already gone are not an error. */
    public void delete(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return;
        }
        repository.deleteById(storedName);
    }

    /** Whether these bytes are still held. Nothing in the API exposes this; tests use it. */
    @Transactional(readOnly = true)
    public boolean exists(String storedName) {
        return storedName != null && repository.existsById(storedName);
    }

    @Transactional(readOnly = true)
    public int getStoredCount() {
        return (int) repository.count();
    }

    @Transactional(readOnly = true)
    public long getStoredBytes() {
        return repository.totalBytes();
    }

    /**
     * Drops the oldest rows until both limits hold again.
     *
     * <p>The row just written is never the one dropped: a single upload larger than the whole
     * budget would otherwise vanish before the response naming it had been built.
     */
    private void evictWhileOverBudget() {
        long count = repository.count();
        long bytes = repository.totalBytes();
        if (count <= 1 || (count <= maxFiles && bytes <= maxBytes)) {
            return;
        }
        List<String> oldestFirst = repository.namesOldestFirst();
        int dropped = 0;
        for (String name : oldestFirst) {
            if (count <= 1 || (count <= maxFiles && bytes <= maxBytes)) {
                break;
            }
            long size = repository.findById(name).map(StoredFile::getSize).orElse(0L);
            repository.deleteById(name);
            count--;
            bytes -= size;
            dropped++;
        }
        if (dropped > 0) {
            log.debug("evicted {} upload(s) to stay within {} files / {} bytes",
                    dropped, maxFiles, maxBytes);
        }
    }

    /**
     * {@code {timestamp}_{uuid}_{sanitised original}}: unique, ordered, and traceable back to
     * what the client called it without ever letting the client choose it.
     */
    private static String storedName(String originalName) {
        return System.currentTimeMillis() + "_" + UUID.randomUUID() + "_" + sanitise(originalName);
    }

    /** Keeps letters, digits, dot, dash and underscore. Everything else becomes an underscore. */
    private static String sanitise(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "upload";
        }
        String cleaned = originalName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        // A run of dots collapses to one. Nothing here is a path segment any more, so ".." is
        // already inert, but a stored name that cannot contain it at all is a property worth
        // being able to state plainly.
        cleaned = cleaned.replaceAll("\\.{2,}", ".");
        // Leading dots would otherwise read oddly in a filePath.
        cleaned = cleaned.replaceAll("^\\.+", "");
        if (cleaned.isBlank()) {
            return "upload";
        }
        // The column is 200 characters, and the prefix takes about 50 of them.
        return cleaned.length() > 100 ? cleaned.substring(cleaned.length() - 100) : cleaned;
    }
}
