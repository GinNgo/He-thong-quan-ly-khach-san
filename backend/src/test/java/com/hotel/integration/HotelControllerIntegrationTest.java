package com.hotel.integration;

import com.hotel.controllers.HotelController;
import com.hotel.entities.User;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.services.HotelManagementService;
import com.hotel.services.RoomTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.hotel.config.SecurityConfig;
import com.hotel.security.JwtAuthFilter;
import com.hotel.security.JwtTokenProvider;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HotelController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtTokenProvider.class})
class HotelControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HotelManagementService hotelService;

    @MockBean
    private RoomTypeService roomTypeService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void getMyHotels_WithAuth_ShouldReturn200() throws Exception {
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("partner");
        mockUser.setPasswordHash("hash");
        mockUser.setRoles(new HashSet<>());
        
        CustomUserDetails userDetails = new CustomUserDetails(
                mockUser.getUsername(),
                mockUser.getPasswordHash(),
                new HashSet<>(),
                new HashMap<>(),
                mockUser.getId(),
                null,
                new HashMap<>()
        );

        mockMvc.perform(get("/api/v1/hotels/my-hotels")
                        .with(user(userDetails)))
                .andExpect(status().isOk());
    }

    @Test
    void getMyHotels_WithoutAuth_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/hotels/my-hotels"))
                .andExpect(status().isForbidden());
    }

}
