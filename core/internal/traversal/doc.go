// Package traversal computes caller / callee reachability over the call
// graph held by [graph.Graph].
//
// The result contract is defined as a property of the graph, not of the
// walk that produced it (design/features/traversal/DesignDoc_traversal.md):
// the reached node set is every node whose shortest distance from the
// start (minDepth, start = 0) is within the optional depth limit. The
// BFS / DFS order option only controls the internal visit order and never
// changes the observable result.
package traversal
