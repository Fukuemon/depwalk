package com.example.pat.lib;

import lombok.AllArgsConstructor;

/** 生成 constructor を明示 `super(...)` から呼ばれる基底型 (explicit super × cross-module Lombok 生成 member のパターン)。 */
@AllArgsConstructor
public class BaseTask {
    private final String id;
}
