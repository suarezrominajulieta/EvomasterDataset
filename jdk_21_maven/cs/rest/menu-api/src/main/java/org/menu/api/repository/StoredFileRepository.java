package org.menu.api.repository;

import org.menu.api.domain.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StoredFileRepository extends JpaRepository<StoredFile, String> {

    @Query("select coalesce(sum(f.size), 0) from StoredFile f")
    long totalBytes();

    /**
     * Just the names, oldest first. Eviction has no use for the bytes, and loading a megabyte of
     * them to decide which row to delete would defeat the point of having a budget.
     */
    @Query("select f.name from StoredFile f order by f.sequence asc")
    List<String> namesOldestFirst();
}
