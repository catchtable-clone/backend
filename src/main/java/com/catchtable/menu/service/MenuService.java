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
import com.catchtable.user.repository.UserRepository;
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
    private final UserRepository userRepository;

    // 메뉴 일괄 생성 (관리자 전용)
    @Transactional
    public MenuCreateResponse create(Long userId, Long storeId, MenuCreateRequest request) {
        userRepository.getAdminOrThrow(userId, ErrorCode.ADMIN_ONLY_MENU_CREATE);

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

    // 메뉴 수정 (관리자 전용 + 매장 소속 검증)
    @Transactional
    public MenuUpdateResponse update(Long userId, Long storeId, Long menuId, MenuUpdateRequest request) {
        userRepository.getAdminOrThrow(userId, ErrorCode.ADMIN_ONLY_MENU_UPDATE);
        Menu menu = getMenuOfStoreOrThrow(storeId, menuId);
        menu.update(request.menuName(), request.description(), request.price(), request.menuImage());
        return new MenuUpdateResponse(menuId, "메뉴 수정 완료");
    }

    // 메뉴 삭제 (관리자 전용 + 매장 소속 검증)
    @Transactional
    public MenuDeleteResponse delete(Long userId, Long storeId, Long menuId) {
        userRepository.getAdminOrThrow(userId, ErrorCode.ADMIN_ONLY_MENU_DELETE);
        Menu menu = getMenuOfStoreOrThrow(storeId, menuId);
        menu.softDelete();
        return new MenuDeleteResponse(menuId, "메뉴 삭제 완료");
    }

    /**
     * menuId 의 메뉴를 조회하고, 해당 메뉴가 path의 storeId 소속인지 검증한다.
     * 다른 매장의 menuId를 임의로 조작해 수정/삭제하는 행위를 차단.
     */
    private Menu getMenuOfStoreOrThrow(Long storeId, Long menuId) {
        Menu menu = menuRepository.findByIdAndIsDeletedFalse(menuId)
                .orElseThrow(() -> new CustomException(ErrorCode.MENU_NOT_FOUND));
        if (!menu.getStore().getId().equals(storeId)) {
            throw new CustomException(ErrorCode.MENU_STORE_MISMATCH);
        }
        return menu;
    }
}
