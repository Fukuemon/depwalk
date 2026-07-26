package com.fukuemon.depwalk.javaanalyzer.analysis.graph;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code callEdge} の metadata (dispatch 種別 / lambda・method reference 経由の標識) を組み立てる。
 */
final class CallEdgeMetadata {

    private CallEdgeMetadata() {
    }

    static Map<String, Object> forCall(String dispatch, boolean viaLambda) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (dispatch != null) {
            metadata.put("dispatch", dispatch);
        }
        if (viaLambda) {
            metadata.put("viaLambda", true);
        }
        return metadata.isEmpty() ? null : metadata;
    }

    static Map<String, Object> forMethodReference(String dispatch, boolean viaLambda) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (dispatch != null) {
            metadata.put("dispatch", dispatch);
        }
        if (viaLambda) {
            metadata.put("viaLambda", true);
        }
        metadata.put("viaMethodReference", true);
        return metadata;
    }
}
