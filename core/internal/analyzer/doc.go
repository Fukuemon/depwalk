// Package analyzer は Analyzer process を動かす。起動、stdin / stdout / stderr の
// streaming、exit code の扱いだけを持つ。
//
// JSONL の中身はここでは opaque な byte 列である。解析要求の組み立てと Analyzer
// Protocol record の parse / 検証は protocol package (ACL) の責務。
// 本 package は他の internal package に依存しない。
package analyzer
