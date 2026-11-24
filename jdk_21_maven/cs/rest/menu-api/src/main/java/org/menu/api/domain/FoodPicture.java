package org.menu.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Entity
@Table(name = "food_picture")
public class FoodPicture {

    private static final AtomicLong NEXT = new AtomicLong();

    @Id
    private UUID id;

    /** The name the file was stored under, which is never the name the client sent. */
    private String fileName;

    /** The name the client sent, kept verbatim for the record. */
    @Column(length = 512)
    private String originalFileName;

    private long length;

    private String contentType;

    private String caption;

    private Integer displayOrder;

    /**
     * The order this row was made in. Internal: it never reaches a DTO, so no client can read
     * or patch it. It decides both what {@code pictures[]} looks like and which picture goes
     * when a dish is over its limit.
     *
     * <p>A counter rather than a timestamp, because a clock is not fine enough for the job:
     * {@code Instant.now()} resolves to the millisecond on Windows, two uploads in the same
     * millisecond tie, and the tie then falls to a random UUID. The database is dropped at
     * shutdown, so starting from zero each run is exactly right.
     */
    @Column(nullable = false, updatable = false)
    private long sequence = NEXT.getAndIncrement();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "food_id")
    private Food food;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public long getSequence() {
        return sequence;
    }

    public Food getFood() {
        return food;
    }

    public void setFood(Food food) {
        this.food = food;
    }

    public UUID getFoodId() {
        return food == null ? null : food.getId();
    }
}
