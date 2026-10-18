package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

/** solver の resolve が投げた例外を、call site 1 件の失敗として扱えるかで振り分ける。 */
final class ResolutionFailures {

    private ResolutionFailures() {
    }

    /**
     * 要素単位に隔離可能と確認済みの resolution failure だけを diagnostic 経路へ
     * 通し、それ以外の RuntimeException は request fatal (JAVA_INTERNAL_ERROR)
     * として伝播させる。
     * LinkageError はここへ来ず
     * Main の internal error 境界で処理される。
     */
    static void rethrowUnlessIsolable(RuntimeException e) {
        String packageName = e.getClass().getPackageName();
        if (packageName.startsWith("com.github.javaparser.resolution")) {
            return;
        }
        // JavaParser 内部 frame を起点とする汎用 RuntimeException
        // (UnsupportedOperationException / IllegalStateException /
        // ConcurrentModificationException 等) も、resolve 呼び出し境界で発生する
        // 限り要素単位に隔離できる library 側 resolution failure として扱う。
        // JDK collection 内で顕在化するケース (HashMap iterator 等) があるため、
        // JDK frame を除いた最初の frame で発生元 library を判定する。depwalk
        // 自身 (合成宣言等) を起点とする例外は analyzer 側バグとして伝播させ、
        // unresolved diagnostic へ化けさせない。
        for (StackTraceElement frame : e.getStackTrace()) {
            String className = frame.getClassName();
            if (className.startsWith("java.") || className.startsWith("jdk.") || className.startsWith("sun.")) {
                continue;
            }
            if (className.startsWith("com.github.javaparser.")) {
                return;
            }
            break;
        }
        throw e;
    }
}
