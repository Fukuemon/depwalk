package app

import (
	"context"
	"fmt"

	"github.com/Fukuemon/depwalk/internal/domain"
	"github.com/Fukuemon/depwalk/internal/ports"
)

type RunConfig struct {
	Depth    int
	Format   string
	Verbose  bool
	MaxNodes int
}

type Dependencies struct {
	Parser   ports.Parser
	Resolver ports.Resolver
	Index    ports.Index
	Renderer ports.Renderer
	Cache    ports.Cache
}

func Run(ctx context.Context, mode, selectorRaw string, cfg RunConfig, deps Dependencies) error {
	sel, err := ParseSelector(selectorRaw)
	if err != nil {
		return err
	}
	if deps.Parser == nil || deps.Resolver == nil {
		return fmt.Errorf("adapters are not wired yet (need Parser + Resolver). selector=%s", sel.Raw)
	}

	switch mode {
	case "callees":
		_, _ = cfg, deps
		return fmt.Errorf("callees is not implemented yet")
	case "callers":
		_, _ = cfg, deps
		return fmt.Errorf("callers is not implemented yet")
	default:
		return &domain.SelectorError{
			Kind:     domain.SelectorErrorUnsupported,
			Selector: mode,
			Message:  "mode must be 'callees' or 'callers'",
		}
	}
}
