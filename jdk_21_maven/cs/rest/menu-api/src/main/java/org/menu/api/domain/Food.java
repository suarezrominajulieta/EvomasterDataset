package org.menu.api.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "food")
public class Food {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    /** The recipe. Spelled the way the .NET original spelled it. */
    @Column(length = 4000)
    private String recept;

    @Column(precision = 19, scale = 2)
    private BigDecimal price;

    private String currency;

    /** Stored name of the allergen sheet uploaded through /api/v1/Foods/{id}/allergens. */
    private String allergenSheetFileName;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    /**
     * Oldest first, always. Without the ordering the list came back in whatever order the
     * database happened to return, which makes two runs of the same fuzzer disagree about a
     * response body that is in fact the same.
     */
    @OneToMany(mappedBy = "food", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<FoodPicture> pictures = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRecept() {
        return recept;
    }

    public void setRecept(String recept) {
        this.recept = recept;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getAllergenSheetFileName() {
        return allergenSheetFileName;
    }

    public void setAllergenSheetFileName(String allergenSheetFileName) {
        this.allergenSheetFileName = allergenSheetFileName;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public UUID getCategoryId() {
        return category == null ? null : category.getId();
    }

    public List<FoodPicture> getPictures() {
        return pictures;
    }

    public void setPictures(List<FoodPicture> pictures) {
        this.pictures = pictures;
    }

    public void addPicture(FoodPicture picture) {
        picture.setFood(this);
        pictures.add(picture);
    }
}
