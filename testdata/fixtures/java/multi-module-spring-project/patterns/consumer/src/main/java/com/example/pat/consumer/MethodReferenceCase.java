package com.example.pat.consumer;

import com.example.pat.lib.Item;

import java.util.List;
import java.util.stream.Collectors;

/** パターン④: 生成 getter への method reference (`Item::getName`)。 */
public class MethodReferenceCase {
    public List<String> names(List<Item> items) {
        return items.stream().map(Item::getName).collect(Collectors.toList());
    }
}
