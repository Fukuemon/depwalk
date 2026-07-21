package com.example.pat.consumer;

import com.example.pat.lib.Item;

import java.util.Map;

/** パターン⑦: generic メソッド戻り値を `var` で受けた後続 call。 */
public class VarGenericCase {
    public String run(Map<String, Item> itemsById) {
        final var item = itemsById.get("k");
        return item.getName();
    }
}
