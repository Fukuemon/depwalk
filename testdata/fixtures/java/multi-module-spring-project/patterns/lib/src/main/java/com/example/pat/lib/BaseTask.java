package com.example.pat.lib;

import lombok.AllArgsConstructor;

/** 生成 constructor を明示 `super(...)` から呼ばれる基底型 (spec #27 パターン⑤×⑧)。 */
@AllArgsConstructor
public class BaseTask {
    private final String id;
}
