package traversal

// sccComponents assigns a strongly connected component ID to every node
// using an iterative Tarjan algorithm in O(V + E). The iteration is
// explicit (no recursion) so deep call chains cannot overflow the stack.
// Component IDs are arbitrary; only membership equality is meaningful.
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
		frames := []frame{{node: root}}

		for len(frames) > 0 {
			top := len(frames) - 1
			node := frames[top].node

			if frames[top].nextEdge < len(adjacency[node]) {
				next := adjacency[node][frames[top].nextEdge]
				frames[top].nextEdge++
				if _, seen := index[next]; !seen {
					index[next] = nextIndex
					lowlink[next] = nextIndex
					nextIndex++
					stack = append(stack, next)
					onStack[next] = true
					frames = append(frames, frame{node: next})
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
