package com.gildedrose.shopapi.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.gildedrose.shopapi.domain.Item;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    @Value("${gildedrose.surge.window.ms}")
    private long timeToLive;

    @Value("${gildedrose.surge.size}")
    private int surgeSize;

    private LoadingCache<ItemCacheKey, Item> itemCache;
    private Map<UUID, Item> inventory = new ConcurrentHashMap<>();

    public InventoryService() throws IOException {
        refreshInventory();
    }

    private LoadingCache<ItemCacheKey, Item> getItemCache() {
        if (itemCache == null) {
            itemCache = CacheBuilder.newBuilder()
                    .expireAfterWrite(timeToLive, TimeUnit.MILLISECONDS)
                    .build(new CacheLoader<ItemCacheKey, Item>() {
                        @Override
                        public Item load(ItemCacheKey key) {
                            return key.getItem();
                        }
                    });
        }
        return itemCache;
    }

    public void refreshInventory() throws IOException {
        Gson gson = new Gson();
        ClassPathResource resource = new ClassPathResource("inventory.json");
        JsonReader reader = new JsonReader(new InputStreamReader(resource.getInputStream()));
        Map<String, List<Item>> objects = gson.fromJson(reader, new TypeToken<Map<String, List<Item>>>(){}.getType());
        inventory = objects.get("items").stream()
                .collect(Collectors.toMap(Item::getUuid, Function.identity()));
    }

    public List<Item> getInventory() {
        return inventory.values().stream().collect(Collectors.toList());
    }

    public Item getItem(UUID itemId) throws Exception {
        Item item = inventory.get(itemId);
        if (item != null) {
            // Add the item to the cache
            Item cachedItem = getItemCache().get(ItemCacheKey.builder()
                    .requestId(UUID.randomUUID())
                    .item(item)
                    .build());

            // Retrieve the items matching the request
            List<Item> items = getCachedItems(itemId);

            // Create a copy of the item (surge pricing only applies once)
            return getPriceAdjustedItem(cachedItem, items.size());
        }
        return null;
    }

    private List<Item> getCachedItems(final UUID itemId) {
        return getItemCache().asMap().values()
                .stream()
                .filter(i -> i.getUuid().equals(itemId))
                .collect(Collectors.toList());
    }

    private Item getPriceAdjustedItem(Item item, int cachedItemsCount) {
        return Item.builder()
                .uuid(item.getUuid())
                .name(item.getName())
                .description(item.getDescription())
                .price(cachedItemsCount > surgeSize ? (int) Math.round(item.getPrice() * 1.1) : item.getPrice())
                .quantity(item.getQuantity())
                .build();
    }

    public Item purchaseItem(UUID itemId, int quantity) {
        Item item = inventory.get(itemId);
        if (item != null) {
            if (item.getQuantity() > 0 && item.getQuantity() >= quantity) {
                item.setQuantity(item.getQuantity() - quantity);
                return getPriceAdjustedItem(item, getCachedItems(itemId).size());
            }
        }
        return null;
    }

    public void resetCache() {
        getItemCache().invalidateAll();
    }

    @Getter
    @Setter
    @Builder
    private static class ItemCacheKey {
        private UUID requestId;
        private Item item;
    }
}
