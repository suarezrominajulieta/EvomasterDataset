package org.menu.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.menu.api.repository.StoredFileRepository;
import org.menu.api.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The storage budget, driven against the real tables but without the web layer: what is being
 * checked is that a long run cannot grow without limit, and proving a property of twenty lines
 * through thousands of HTTP requests would be a poor trade.
 */
@DataJpaTest
class FileStorageServiceTest {

    @Autowired
    private StoredFileRepository repository;

    private FileStorageService storage(int maxFiles, long maxBytes) {
        return new FileStorageService(repository, maxFiles, maxBytes);
    }

    private static MockMultipartFile file(String name, int bytes) {
        return new MockMultipartFile("file", name, "image/png", new byte[bytes]);
    }

    @Test
    @DisplayName("past the file count the oldest go, and the newest stays")
    void evictsByCount() {
        FileStorageService storage = storage(3, Long.MAX_VALUE);

        String first = storage.store(file("a.png", 10));
        storage.store(file("b.png", 10));
        storage.store(file("c.png", 10));
        String fourth = storage.store(file("d.png", 10));

        assertThat(storage.getStoredCount()).isEqualTo(3);
        assertThat(storage.exists(first)).isFalse();
        assertThat(storage.exists(fourth)).isTrue();
    }

    @Test
    @DisplayName("past the byte budget the oldest go too")
    void evictsByBytes() {
        FileStorageService storage = storage(1000, 250);

        storage.store(file("a.png", 100));
        storage.store(file("b.png", 100));
        storage.store(file("c.png", 100));

        assertThat(storage.getStoredBytes()).isLessThanOrEqualTo(250);
        assertThat(storage.getStoredCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a single upload larger than the whole budget is still kept")
    void keepsTheNewestWhateverItsSize() {
        FileStorageService storage = storage(10, 100);

        String only = storage.store(file("huge.png", 5_000));

        // Otherwise the response naming it would point at bytes already gone.
        assertThat(storage.exists(only)).isTrue();
        assertThat(storage.getStoredCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("deleting an upload gives its room back")
    void deleteFreesBudget() {
        FileStorageService storage = storage(2, Long.MAX_VALUE);

        String first = storage.store(file("a.png", 10));
        storage.delete(first);
        assertThat(storage.getStoredCount()).isZero();

        storage.store(file("b.png", 10));
        String third = storage.store(file("c.png", 10));

        assertThat(storage.getStoredCount()).isEqualTo(2);
        assertThat(storage.exists(third)).isTrue();
    }

    @Test
    @DisplayName("deleting something that is not there is not an error")
    void deleteOfAbsentIsQuiet() {
        FileStorageService storage = storage(10, Long.MAX_VALUE);

        storage.delete("1700000000000_" + java.util.UUID.randomUUID() + "_gone.png");
        storage.delete(null);
        storage.delete("  ");

        assertThat(storage.getStoredCount()).isZero();
    }

    @Test
    @DisplayName("the bytes are kept, and keyed by the name that was handed back")
    void bytesAreRetrievable() {
        FileStorageService storage = storage(10, Long.MAX_VALUE);
        byte[] content = {1, 2, 3, 4, 5};

        String name = storage.store(
                new MockMultipartFile("file", "a.png", "image/png", content));

        assertThat(repository.findById(name)).isPresent().get()
                .satisfies(stored -> {
                    assertThat(stored.getContent()).isEqualTo(content);
                    assertThat(stored.getSize()).isEqualTo(content.length);
                    assertThat(stored.getContentType()).isEqualTo("image/png");
                });
    }

    @Test
    @DisplayName("the stored name never carries anything the client chose about a path")
    void storedNameIsSafe() {
        FileStorageService storage = storage(10, Long.MAX_VALUE);

        String name = storage.store(file("../../etc/passwd.png", 10));

        assertThat(name)
                .doesNotContain("..")
                .doesNotContain("/")
                .doesNotContain("\\");
    }

    @Test
    @DisplayName("an upload leaves nothing behind on disk")
    void nothingTouchesTheFilesystem() throws Exception {
        FileStorageService storage = storage(10, Long.MAX_VALUE);
        java.nio.file.Path legacy = java.nio.file.Paths.get("pictures");
        boolean existedBefore = java.nio.file.Files.exists(legacy);

        storage.store(file("a.png", 10));

        // The directory the old disk-backed store used must not reappear.
        assertThat(java.nio.file.Files.exists(legacy)).isEqualTo(existedBefore);
    }
}
