package com.catchtable.menu.service;

import com.catchtable.global.exception.CustomException;
import com.catchtable.global.exception.ErrorCode;
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

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private final MenuRepository menuRepository;
    private final StoreRepository storeRepository;

    // 메뉴 일괄 생성
    @Transactional
    public MenuCreateResponse create(Long storeId, MenuCreateRequest request) {
        Store store = storeRepository.findByIdAndIsDeletedFalse(storeId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));

        List<Menu> menuList = request.menus().stream()
                .map(item -> Menu.create(
                        store, item.menuName(), item.menuImage(), item.price(), item.description()
                ))
                .toList();

        List<Long> menuIds = menuRepository.saveAll(menuList).stream()
                .map(Menu::getId)
                .toList();

        return new MenuCreateResponse(menuIds);
    }

    // 메뉴 목록 조회
    public List<MenuResponse> getByStoreId(Long storeId) {
        return menuRepository.findByStore_IdAndIsDeletedFalse(storeId).stream()
                .map(MenuResponse::from)
                .toList();
    }

    // 메뉴 수정
    @Transactional
    public MenuUpdateResponse update(Long menuId, MenuUpdateRequest request) {
        Menu menu = menuRepository.findByIdAndIsDeletedFalse(menuId)
                .orElseThrow(() -> new CustomException(ErrorCode.MENU_NOT_FOUND));
        menu.update(request.menuName(), request.description(), request.price(), request.menuImage());
        return new MenuUpdateResponse(menuId, "메뉴 수정 완료");
    }

    // 메뉴 삭제
    @Transactional
    public MenuDeleteResponse delete(Long menuId) {
        Menu menu = menuRepository.findByIdAndIsDeletedFalse(menuId)
                .orElseThrow(() -> new CustomException(ErrorCode.MENU_NOT_FOUND));
        menu.softDelete();
        return new MenuDeleteResponse(menuId, "메뉴 삭제 완료");
    }
}
