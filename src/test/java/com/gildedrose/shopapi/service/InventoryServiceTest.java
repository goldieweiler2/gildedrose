package com.gildedrose.shopapi.service;

import java.util.List;
import com.gildedrose.shopapi.domain.Item;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@TestPropertySource(locations="classpath:application-test.properties")
public class InventoryServiceTest {

    @Value("${gildedrose.surge.window.ms}")
    private long timeToLive;

    @Value("${gildedrose.surge.size}")
    private int surgeSize;

    @Autowired
    private InventoryService inventoryService;

    private List<Item> inventory;

    @Before
    public void setup() {
        inventoryService.resetCache();
        inventory = inventoryService.getInventory();
    }

    @Test
    public void getItemReturnsItemAtCurrentPrice() throws Exception {
        Item item = inventoryService.getItem(inventory.get(0).getUuid());
        assertThat(item.getPrice(), equalTo(inventory.get(0).getPrice()));
    }

    @Test
    public void getSameItemIncrementsPrice() throws Exception {
        Item inventoryItem = inventory.get(0);
        for (int i = 0; i <= surgeSize; i++) {
            Item item = inventoryService.getItem(inventoryItem.getUuid());
            int expectedPrice = i + 1 > surgeSize ? (int) Math.round(inventoryItem.getPrice() * 1.1) : inventoryItem.getPrice();
            assertThat(item.getPrice(), equalTo(expectedPrice));
        }
    }

    @Test
    public void priceDropsWhenRequestsReduce() throws Exception {
        Item inventoryItem = inventory.get(0);

        for (int i = 0; i < surgeSize * 2; i++) {
            Item item = inventoryService.getItem(inventoryItem.getUuid());
            int expectedPrice = i + 1 > surgeSize ? (int) Math.round(inventoryItem.getPrice() * 1.1) : inventoryItem.getPrice();
            System.out.println(i + "-" + expectedPrice);
            assertThat(item.getPrice(), equalTo(expectedPrice));

            // Pause for 500ms before getting the next item
            Thread.sleep(500);
        }

        // Pause long enough for enough requests to have expired such that the next request returns the original price
        Thread.sleep(timeToLive - (surgeSize * 500));
        Item item = inventoryService.getItem(inventoryItem.getUuid());
        assertThat(item.getPrice(), equalTo(inventoryItem.getPrice()));

        // The next request, the price should have gone up again.
        item = inventoryService.getItem(inventoryItem.getUuid());
        assertThat(item.getPrice(), equalTo((int) Math.round(inventoryItem.getPrice() * 1.1)));
    }

    @Test
    public void purchaseItemReducesAvailableInventory() {
        Item inventoryItem = inventory.get(0);
        int initialQuantity = inventoryItem.getQuantity();

        Item purchasedItem = inventoryService.purchaseItem(inventoryItem.getUuid(), 1);
        assertNotNull(purchasedItem);
        assertThat(inventory.get(0).getQuantity(), equalTo(initialQuantity - 1));
    }

    @Test
    public void cannotPurchaseMoreItemsThanAvailable() {
        Item inventoryItem = inventory.get(0);
        int initialQuantity = inventoryItem.getQuantity();

        Item purchasedItem = inventoryService.purchaseItem(inventoryItem.getUuid(), initialQuantity + 1);
        assertNull(purchasedItem);
    }
}
