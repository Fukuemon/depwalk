package com.example;

public class B {
    // 未解決の parameter 型により decl-level の JAVA_UNRESOLVED_SYMBOL diagnostic が
    // B の解析直後に flush される。
    public void bar(UnknownParamType input) {
    }
}
