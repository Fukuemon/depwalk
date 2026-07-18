package com.fukuemon.depwalk.gradleprovider;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import javax.inject.Inject;

/**
 * depwalk の自動 discovery が一時 init script から root project へ適用する
 * plugin。custom tooling model builder を登録するだけで、task の追加・実行や
 * 対象 build の設定変更は行わない。
 */
public class DepwalkModelPlugin implements Plugin<Project> {

    private final ToolingModelBuilderRegistry registry;

    @Inject
    public DepwalkModelPlugin(ToolingModelBuilderRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void apply(Project project) {
        registry.register(new DepwalkModelBuilder());
    }
}
