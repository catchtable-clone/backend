package com.catchtable.menu.service;

import com.catchtable.menu.dto.MenuActionResponse;
import com.catchtable.menu.dto.MenuCreateResponse;
import com.catchtable.menu.dto.MenuResponse;
import com.catchtable.menu.entity.Menu;
import com.catchtable.menu.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private final MenuRepository menuRepository;

    //메뉴 생성 메서드
    @Transactional
    public MenuCreateResponse create(Long storeId, List<String> menuNames, List<MultipartFile> menuImages,
                                     List<Integer> prices, List<String> descriptions) {
        List<Long> menuIds = new ArrayList<>();
        for (int i = 0; i < menuNames.size(); i++) {
            String menuImage = (menuImages != null && i < menuImages.size())
                    ? menuImages.get(i).getOriginalFilename() : null;
            String description = (descriptions != null && i < descriptions.size())
                    ? descriptions.get(i) : null;
            Menu menu = Menu.create(storeId, menuNames.get(i), menuImage, prices.get(i), description);
            menuIds.add(menuRepository.save(menu).getId());
        }
        return new MenuCreateResponse(menuIds);
    }

    //메뉴 조회 메서드
    public List<MenuResponse> getByStoreId(Long storeId) {
        return menuRepository.findByStoreIdAndIsDeletedFalse(storeId).stream()
                .map(MenuResponse::from)
                .toList();
    }

    //메뉴 수정 메서드
    @Transactional
    public MenuActionResponse update(Long menuId, String menuName, MultipartFile menuImage,
                                     Integer price, String description) {
        Menu menu = menuRepository.findByIdAndIsDeletedFalse(menuId)
                .orElseThrow(() -> new RuntimeException("Menu not found: " + menuId));
        String menuImageName = menuImage != null ? menuImage.getOriginalFilename() : menu.getMenuImage();
        menu.update(menuName, description, price, menuImageName);
        return new MenuActionResponse(menuId, "메뉴 수정 완료");
    }

    //메뉴 삭제 메서드
    @Transactional
    public MenuActionResponse delete(Long menuId) {
        Menu menu = menuRepository.findByIdAndIsDeletedFalse(menuId)
                .orElseThrow(() -> new RuntimeException("Menu not found: " + menuId));
        menu.softDelete();
        return new MenuActionResponse(menuId, "메뉴 삭제 완료");
    }
}
