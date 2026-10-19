package com.healthcare.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ViewRoutingTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void inventoryRoutesShouldBeAvailable() throws Exception {
        mockMvc.perform(get("/inventory")).andExpect(status().isOk());
        mockMvc.perform(get("/inventory/add")).andExpect(status().isOk());
        mockMvc.perform(get("/medicines")).andExpect(status().isOk());
    }
}
