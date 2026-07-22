package com.example.pat.consumer;

import com.example.pat.lib.Item;

/**
 * パターン①: 生成 member 起点の chain で receiver 型が失われ、
 * 後続 call (`trim` / `isEmpty`) も未解決になる。
 */
public class FluentChainCase {
    public boolean run() {
        return new Item("nut", 1).getName().trim().isEmpty();
    }
}
