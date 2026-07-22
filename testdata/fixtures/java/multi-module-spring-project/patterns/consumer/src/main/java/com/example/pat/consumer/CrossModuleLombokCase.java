package com.example.pat.consumer;

import com.example.pat.lib.Item;

/** パターン⑧: 他 module の Lombok 生成 constructor / getter の呼び出し。 */
public class CrossModuleLombokCase {
    public String run() {
        Item item = new Item("bolt", 3);
        return item.getName();
    }
}
