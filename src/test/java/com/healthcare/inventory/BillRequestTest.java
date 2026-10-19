package com.healthcare.inventory;

import com.healthcare.inventory.dto.BillRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillRequestTest {

    @Test
    void stripQuantityShouldConvertToTabletCountBeforeBilling() {
        BillRequest.BillItemRequest item = new BillRequest.BillItemRequest();
        item.setQuantity(3);
        item.setQuantityType("STRIP");
        item.setTabletsPerStrip(10);

        assertThat(item.getEffectiveQuantity()).isEqualTo(30);
    }

    @Test
    void tabletQuantityShouldRemainUnchanged() {
        BillRequest.BillItemRequest item = new BillRequest.BillItemRequest();
        item.setQuantity(7);
        item.setQuantityType("TABLET");
        item.setTabletsPerStrip(10);

        assertThat(item.getEffectiveQuantity()).isEqualTo(7);
    }
}
