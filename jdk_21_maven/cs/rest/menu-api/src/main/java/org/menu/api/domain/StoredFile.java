package org.menu.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The bytes of an accepted upload, held as a row rather than as a file.
 *
 * <p>Everything else in this application is dropped when the process ends, because the database
 * is in memory and the schema is created and dropped with it. Uploads used to be the one
 * exception: they outlived the run that made them, and since every path that deletes one starts
 * from a row that no longer existed, nobody was ever going to remove them. Keeping them here
 * puts them under the same rule as the rest — a restart is a clean slate, with nothing to sweep
 * up afterwards.
 *
 * <p>The cost is that the bytes now sit in the heap instead of on a disk, which is why the budget
 * in {@link org.menu.api.service.FileStorageService} matters more than it did, not less.
 */
@Entity
@Table(name = "stored_file")
public class StoredFile {

    private static final AtomicLong NEXT = new AtomicLong();

    /** The generated storage name, which is also how a picture row refers to its bytes. */
    @Id
    @Column(length = 200)
    private String name;

    @Lob
    @Column(nullable = false)
    private byte[] content;

    @Column(nullable = false)
    private long size;

    private String contentType;

    /** Insertion order, so the oldest can be found without trusting a clock. */
    @Column(nullable = false, updatable = false)
    private long sequence = NEXT.getAndIncrement();

    protected StoredFile() {
    }

    public StoredFile(String name, byte[] content, String contentType) {
        this.name = name;
        this.content = content;
        this.size = content.length;
        this.contentType = contentType;
    }

    public String getName() {
        return name;
    }

    public byte[] getContent() {
        return content;
    }

    public long getSize() {
        return size;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSequence() {
        return sequence;
    }
}
