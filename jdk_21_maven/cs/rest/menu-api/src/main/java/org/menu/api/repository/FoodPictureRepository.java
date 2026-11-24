package org.menu.api.repository;

import org.menu.api.domain.FoodPicture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FoodPictureRepository extends JpaRepository<FoodPicture, UUID> {
}
