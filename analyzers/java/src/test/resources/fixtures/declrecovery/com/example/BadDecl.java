package com.example;

public class BadDecl {

    // 呼び出しを含む instance の field の初期化子は、囲み型の constructor を caller にする。
    // constructor の resolve の失敗は、この初期化子の走査と constructor 自身の走査の両方で起きる。
    private final int x = helper();

    public void broken(UnknownParam u) {
    }

    public BadDecl(UnknownParam u) {
    }

    public void safe() {
    }

    int helper() {
        return 0;
    }
}
