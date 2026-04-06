package com.catchtable.menu.service;

import com.catchtable.menu.dto.MenuActionResponse;
import com.catchtable.menu.dto.MenuCreateRequest;
import com.catchtable.menu.dto.MenuCreateResponse;
import com.catchtable.menu.dto.MenuResponse;
import com.catchtable.menu.dto.MenuUpdateRequest;
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
    public MenuCreateResponse create(Long storeId, MenuCreateRequest request, List<MultipartFile> menuImages) {
        List<Long> menuIds = new ArrayList<>();
        List<MenuCreateRequest.MenuItemRequest> menus = request.menus();
        for (int i = 0; i < menus.size(); i++) {
            MenuCreateRequest.MenuItemRequest item = menus.get(i);
            String menuImage = (menuImages != null && i < menuImages.size())
                    ? menuImages.get(i).getOriginalFilename() : null;
            Menu menu = Menu.create(storeId, item.menuName(), menuImage, item.price(), item.description());
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
    public MenuActionResponse update(Long menuId, MenuUpdateRequest request, MultipartFile menuImage) {
        Menu menu = menuRepository.findByIdAndIsDeletedFalse(menuId)
                .orElseThrow(() -> new RuntimeException("Menu not found: " + menuId));
        String menuImageName = menuImage != null ? menuImage.getOriginalFilename() : menu.getMenuImage();
        menu.update(request.menuName(), request.description(), request.price(), menuImageName);
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
