package com.hotel.favorites;

import com.hotel.security.CustomUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FavoriteControllerTest {

    private final FavoriteService favoriteService = mock(FavoriteService.class);
    private final FavoriteController controller = new FavoriteController(favoriteService);
    private final CustomUserDetails customer = new CustomUserDetails(
            "customer", "password", List.of(), Map.of(), 73L, null, Map.of());

    @Test
    void endpointsUseTheAuthenticatedOwnerInsteadOfRequestOwnedIdentity() {
        when(favoriteService.listForCustomer(73L)).thenReturn(List.of());

        assertEquals(200, controller.list(customer).getStatusCode().value());
        assertEquals(204, controller.remove(19L, customer).getStatusCode().value());

        verify(favoriteService).listForCustomer(73L);
        verify(favoriteService).removeForCustomer(73L, 19L);
    }

    @Test
    void endpointsAreRestrictedToCustomerAccounts() {
        assertEquals("hasAuthority('CUSTOMER')",
                FavoriteController.class.getAnnotation(PreAuthorize.class).value());
    }
}
