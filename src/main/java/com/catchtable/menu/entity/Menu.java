package com.catchtable.menu.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "menu")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private String menuName;

    @Column(nullable = false)
    private Integer price;

    private String description;

    private String menuImage;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private boolean isDeleted = false;

    @Builder
    private Menu(Long storeId, String menuName, String description, Integer price, String menuImage) {
        this.storeId = storeId;
        this.menuName = menuName;
        this.description = description;
        this.price = price;
        this.menuImage = menuImage;
    }

    public static Menu create(Long storeId, String menuName, String menuImage, Integer price, String description) {
        return Menu.builder()
                .storeId(storeId)
                .menuName(menuName)
                .menuImage(menuImage)
                .price(price)
                .description(description)
                .build();
    }

    public void update(String menuName, String description, Integer price, String menuImage) {
        this.menuName = menuName;
        this.description = description;
        this.price = price;
        this.menuImage = menuImage;
    }

    public void softDelete() {
        this.isDeleted = true;
    }
}
