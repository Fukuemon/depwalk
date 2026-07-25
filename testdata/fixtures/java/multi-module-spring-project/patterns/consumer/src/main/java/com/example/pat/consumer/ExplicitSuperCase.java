package com.example.pat.consumer;

import com.example.pat.lib.BaseTask;

/** パターン⑤: 明示 `super(...)` の解決先が他 module の生成 constructor。 */
public class ExplicitSuperCase extends BaseTask {
    public ExplicitSuperCase(String id) {
        super(id);
    }
}
