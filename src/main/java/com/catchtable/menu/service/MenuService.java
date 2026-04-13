package com.catchtable.menu.service;

import com.catchtable.global.exception.ResourceNotFoundException;
import com.catchtable.menu.dto.create.MenuCreateRequest;
import com.catchtable.menu.dto.create.MenuCreateResponse;
import com.catchtable.menu.dto.delete.MenuDeleteResponse;
import com.catchtable.menu.dto.read.MenuResponse;
import com.catchtable.menu.dto.update.MenuUpdateRequest;
import com.catchtable.menu.dto.update.MenuUpdateResponse;
import com.catchtable.menu.entity.Menu;
import com.catchtable.menu.repository.MenuRepository;
import com.catchtable.store.entity.Store;
import com.catchtable.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private final MenuRepository menuRepository;
    private final StoreRepository storeRepository;

    @Transactional
    public MenuCreateResponse create(Long storeId, MenuCreateRequest request, List<MultipartFile> menuImages) {
        Store store = storeRepository.findById(storeId)
                .filter(s -> !s.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("매장을 찾을 수 없습니다."));

        List<MenuCreateRequest.MenuItemRequest> menus = request.menus();
        List<Menu> menuList = java.util.stream.IntStream.range(0, menus.size())
                .mapToObj(i -> {
                    MenuCreateRequest.MenuItemRequest item = menus.get(i);
                    String menuImage = (menuImages != null && i < menuImages.size())
                            ? menuImages.get(i).getOriginalFilename() : null;
                    return Menu.create(store, item.menuName(), menuImage, item.price(), item.description());
                })
                .toList();

        List<Long> menuIds = menuRepository.saveAll(menuList).stream()
                .map(Menu::getId)
                .toList();

        return new MenuCreateResponse(menuIds);
    }

    public List<MenuResponse> getByStoreId(Long storeId) {
        return menuRepository.findByStore_IdAndIsDeletedFalse(storeId).stream()
                .map(MenuResponse::from)
                .toList();
    }

    @Transactional
    public MenuUpdateResponse update(Long menuId, MenuUpdateRequest request, MultipartFile menuImage) {
        Menu menu = menuRepository.findByIdAndIsDeletedFalse(menuId)
                .orElseThrow(() -> new ResourceNotFoundException("메뉴를 찾을 수 없습니다."));
        String menuImageName = menuImage != null ? menuImage.getOriginalFilename() : menu.getMenuImage();
        menu.update(request.menuName(), request.description(), request.price(), menuImageName);
        return new MenuUpdateResponse(menuId, "메뉴 수정 완료");
    }

    @Transactional
    public MenuDeleteResponse delete(Long menuId) {
        Menu menu = menuRepository.findByIdAndIsDeletedFalse(menuId)
                .orElseThrow(() -> new ResourceNotFoundException("메뉴를 찾을 수 없습니다."));
        menu.softDelete();
        return new MenuDeleteResponse(menuId, "메뉴 삭제 완료");
    }
}
