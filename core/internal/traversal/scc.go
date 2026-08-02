package traversal

// sccComponents は各 node に強連結成分の ID を割り当てる。Tarjan 法で O(V + E)。
//
// 再帰ではなく明示的な反復で書いてある。呼び出し連鎖が深い graph で
// stack overflow を起こさせないため。
// ID の値自体に意味はなく、同じ ID かどうかだけが意味を持つ。
func sccComponents(nodes map[string]bool, adjacency map[string][]string) map[string]int {
	index := map[string]int{}
	lowlink := map[string]int{}
	onStack := map[string]bool{}
	component := map[string]int{}
	var stack []string
	nextIndex := 0
	nextComponent := 0

	type frame struct {
		node     string
		edges    []string
		nextEdge int
	}

	for root := range nodes {
		if _, seen := index[root]; seen {
			continue
		}
		index[root] = nextIndex
		lowlink[root] = nextIndex
		nextIndex++
		stack = append(stack, root)
		onStack[root] = true
		frames := []frame{{node: root, edges: adjacency[root]}}

		for len(frames) > 0 {
			top := len(frames) - 1
			node := frames[top].node

			if frames[top].nextEdge < len(frames[top].edges) {
				next := frames[top].edges[frames[top].nextEdge]
				frames[top].nextEdge++
				if _, seen := index[next]; !seen {
					index[next] = nextIndex
					lowlink[next] = nextIndex
					nextIndex++
					stack = append(stack, next)
					onStack[next] = true
					frames = append(frames, frame{node: next, edges: adjacency[next]})
				} else if onStack[next] && index[next] < lowlink[node] {
					lowlink[node] = index[next]
				}
				continue
			}

			frames = frames[:top]
			if len(frames) > 0 {
				parent := frames[len(frames)-1].node
				if lowlink[node] < lowlink[parent] {
					lowlink[parent] = lowlink[node]
				}
			}
			if lowlink[node] == index[node] {
				for {
					member := stack[len(stack)-1]
					stack = stack[:len(stack)-1]
					onStack[member] = false
					component[member] = nextComponent
					if member == node {
						break
					}
				}
				nextComponent++
			}
		}
	}
	return component
}
