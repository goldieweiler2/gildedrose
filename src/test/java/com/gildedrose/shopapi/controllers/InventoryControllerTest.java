package com.gildedrose.shopapi.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.gildedrose.shopapi.domain.Item;
import com.gildedrose.shopapi.service.InventoryService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.util.UriComponentsBuilder;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(locations="classpath:application-test.properties")
public class InventoryControllerTest {

    @Value("${local.server.port}")
    private String port;

    @Value("${gildedrose.application.contextPath}")
    private String contextPath;

    @Value("${gildedrose.surge.window.ms}")
    private long timeToLive;

    @Value("${gildedrose.surge.size}")
    private int surgeSize;

    @Value("${security.user.name}")
    private String userName;

    @Value("${security.user.password}")
    private String password;

    @Autowired
    private InventoryService inventoryService;

    private static final String BASE_URL = "http://localhost:%s%s/%s";

    private List<Item> inventory;

    @Before
    public void setup() {
        inventoryService.resetCache();
        inventory = inventoryService.getInventory();
    }

    @Test
    public void canGetInventoryWithoutAuthentication() {
        TestRestTemplate template = new TestRestTemplate();
        String url = String.format(BASE_URL, port, contextPath, InventoryController.ENDPOINT_PATH_INVENTORY);

        ResponseEntity<List<Item>> response = template.exchange(url, HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Item>>() { });

        assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
        assertThat(response.getBody().size(), equalTo(3));
    }

    @Test
    public void getItemReturnsItemIfValid() {
        TestRestTemplate template = new TestRestTemplate();
        String url = String.format(BASE_URL, port, contextPath, InventoryController.ENDPOINT_PATH_ITEM + "/" + inventory.get(0).getUuid());

        ResponseEntity<Item> response = template.exchange(url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Item>() { });
        assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
        assertThat(response.getBody().getName(), equalTo(inventory.get(0).getName()));
        assertThat(response.getBody().getPrice(), equalTo(inventory.get(0).getPrice()));
    }

    @Test
    public void getItemReturnsNotFoundIfUnknownId() {
        TestRestTemplate template = new TestRestTemplate();
        String url = String.format(BASE_URL, port, contextPath, InventoryController.ENDPOINT_PATH_ITEM + "/3c76d2bd-d9d5-4feb-9084-5bc2be931742");

        ResponseEntity<Item> response = template.exchange(url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Item>() { });
        assertThat(response.getStatusCode(), equalTo(HttpStatus.NOT_FOUND));
    }

    @Test
    public void getItemReturnsNotFoundIfInvalidUUID() {
        TestRestTemplate template = new TestRestTemplate();
        String url = String.format(BASE_URL, port, contextPath, InventoryController.ENDPOINT_PATH_ITEM + "/invalid_uuid");

        ResponseEntity<Item> response = template.exchange(url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Item>() { });
        assertThat(response.getStatusCode(), equalTo(HttpStatus.NOT_FOUND));
    }

    @Test
    public void getItemRequestsInitiateSurgePricing() {
        Item inventoryItem = inventory.get(0);
        TestRestTemplate template = new TestRestTemplate();
        String url = String.format(BASE_URL, port, contextPath, InventoryController.ENDPOINT_PATH_ITEM + "/" + inventoryItem.getUuid());

        for (int i = 0; i <= surgeSize; i++) {
            int expectedPrice = i + 1 > surgeSize ? (int) Math.round(inventoryItem.getPrice() * 1.1) : inventoryItem.getPrice();
            ResponseEntity<Item> response = template.exchange(url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<Item>() { });
            assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
            assertThat(response.getBody().getPrice(), equalTo(expectedPrice));
        }
    }

    @Test
    public void purchaseItemRequiresAuthentication() {
        Item inventoryItem = inventory.get(0);
        TestRestTemplate template = new TestRestTemplate();
        String url = String.format(BASE_URL, port, contextPath, InventoryController.ENDPOINT_PATH_ITEM + "/" + inventoryItem.getUuid());
        Map<String, String> params = new HashMap<>();
        params.put("quantity", "1");

        ResponseEntity<Item> response = template.postForEntity(url, HttpMethod.POST, null,
                new ParameterizedTypeReference<Item>() { }, params);

        assertThat(response.getStatusCode(), equalTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    public void purchaseItemSucceedsWithAuthentication() {
        Item inventoryItem = inventory.get(0);
        TestRestTemplate template = new TestRestTemplate(userName, password);
        String url = String.format(BASE_URL, port, contextPath, InventoryController.ENDPOINT_PATH_ITEM + "/" + inventoryItem.getUuid());

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("quantity", "1");

        ResponseEntity<Item> response = template.exchange(builder.toUriString(), HttpMethod.POST, null,
                new ParameterizedTypeReference<Item>() { });

        assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
        assertThat(response.getBody().getName(), equalTo(inventoryItem.getName()));
    }

    @Test
    public void cannotPurchaseMoreItemsThanAvailable() {
        Item inventoryItem = inventory.get(0);
        TestRestTemplate template = new TestRestTemplate(userName, password);
        String url = String.format(BASE_URL, port, contextPath, InventoryController.ENDPOINT_PATH_ITEM + "/" + inventoryItem.getUuid());
        int initialQuantity = inventoryItem.getQuantity();

        for (int i = 0; i <= initialQuantity; i++) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("quantity", 1);

            ResponseEntity<Item> response = template.exchange(builder.toUriString(), HttpMethod.POST, null,
                    new ParameterizedTypeReference<Item>() { });

            if (i < initialQuantity) {
                assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
            } else {
                assertThat(response.getStatusCode(), equalTo(HttpStatus.BAD_REQUEST));
            }
        }
    }
}
