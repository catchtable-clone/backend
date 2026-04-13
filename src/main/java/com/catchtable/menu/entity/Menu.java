package com.catchtable.menu.entity;

import com.catchtable.store.entity.Store;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

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
    private Menu(Store store, String menuName, String description, Integer price, String menuImage) {
        this.store = store;
        this.menuName = menuName;
        this.description = description;
        this.price = price;
        this.menuImage = menuImage;
    }

    public static Menu create(Store store, String menuName, String menuImage, Integer price, String description) {
        return Menu.builder()
                .store(store)
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
