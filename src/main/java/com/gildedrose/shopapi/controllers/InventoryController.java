package com.gildedrose.shopapi.controllers;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.gildedrose.shopapi.domain.Item;
import com.gildedrose.shopapi.service.InventoryService;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CommonsLog
public class InventoryController {

    public static final String ENDPOINT_PATH_INVENTORY = "/inventory";
    public static final String ENDPOINT_PATH_ITEM = "/item";


    @Autowired
    private InventoryService inventoryService;

    @RequestMapping(value = "${gildedrose.application.contextPath}" + ENDPOINT_PATH_INVENTORY,  method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Item>> getInventory() throws IOException {
        return ResponseEntity.ok(inventoryService.getInventory());
    }

    @RequestMapping(value = "${gildedrose.application.contextPath}" + ENDPOINT_PATH_INVENTORY,  method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Item>> refreshInventory() throws IOException {
        inventoryService.refreshInventory();
        return ResponseEntity.ok(inventoryService.getInventory());
    }

    @RequestMapping(value = "${gildedrose.application.contextPath}" + ENDPOINT_PATH_ITEM + "/{itemId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Item> getItem(@PathVariable String itemId, HttpServletResponse response) throws Exception {
        try {
            UUID itemUuid = UUID.fromString(itemId);
            Item item = inventoryService.getItem(itemUuid);

            if (item != null) {
                return ResponseEntity.ok(item);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "The item is not currently available");
            }

        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Unable to find item from request");
        }
        return null;
    }

    @RequestMapping(value = "${gildedrose.application.contextPath}" + ENDPOINT_PATH_ITEM + "/{itemId}", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Item> purchaseItem(@PathVariable String itemId, @RequestParam String quantity, HttpServletRequest request,
                                        HttpServletResponse response) throws Exception {
        try {
            UUID itemUuid = UUID.fromString(itemId);
            Item item = inventoryService.purchaseItem(itemUuid, Integer.parseInt(quantity));

            if (item != null) {
                return ResponseEntity.ok(item);
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "The item is not currently available or there are insufficient items for your request");
            }

        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unable to find item from request");
        }
        return null;
    }

}
