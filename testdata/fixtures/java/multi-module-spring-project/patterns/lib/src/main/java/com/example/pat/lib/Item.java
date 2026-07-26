package com.example.pat.lib;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 生成 member (constructor / getter) を持つ cross-module 呼び出し先。
 * source に明示 constructor / getter を書かないことが再現条件 (cross-module Lombok 生成 member のパターン)。
 */
@Getter
@AllArgsConstructor
public class Item {
    private final String name;
    private final int quantity;
}
