package cobra

import (
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
)

// NOTE: This is a minimal subset of Cobra's API surface to unblock development in environments
// where fetching modules is restricted. Replace this with the real cobra module later.

type Command struct {
	Use   string
	Short string
	Long  string

	SilenceUsage  bool
	SilenceErrors bool

	Args func(cmd *Command, args []string) error
	RunE func(cmd *Command, args []string) error

	flags          *flag.FlagSet
	persistent     *flag.FlagSet
	subCommands    []*Command
	output         io.Writer
	errOutput      io.Writer
	parent         *Command
	parsedChildren bool
}

func (c *Command) SetOut(w io.Writer) { c.output = w }
func (c *Command) SetErr(w io.Writer) { c.errOutput = w }
func (c *Command) OutOrStdout() io.Writer {
	if c.output != nil {
		return c.output
	}
	return os.Stdout
}
func (c *Command) ErrOrStderr() io.Writer {
	if c.errOutput != nil {
		return c.errOutput
	}
	return os.Stderr
}

func (c *Command) Flags() *flag.FlagSet {
	if c.flags == nil {
		c.flags = flag.NewFlagSet(c.nameOrUse(), flag.ContinueOnError)
		c.flags.SetOutput(c.ErrOrStderr())
	}
	return c.flags
}

func (c *Command) PersistentFlags() *flag.FlagSet {
	if c.persistent == nil {
		c.persistent = flag.NewFlagSet(c.nameOrUse()+"-persistent", flag.ContinueOnError)
		c.persistent.SetOutput(c.ErrOrStderr())
	}
	return c.persistent
}

func (c *Command) AddCommand(cmds ...*Command) {
	for _, sc := range cmds {
		sc.parent = c
		c.subCommands = append(c.subCommands, sc)
	}
}

func (c *Command) Execute() error {
	return c.ExecuteWithArgs(os.Args[1:])
}

func (c *Command) ExecuteWithArgs(args []string) error {
	if len(args) == 0 {
		return c.executeHere([]string{})
	}

	// subcommand dispatch: first arg matches subcommand name (first token of Use)
	if sc := c.findSubcommand(args[0]); sc != nil {
		return sc.ExecuteWithArgs(args[1:])
	}
	return c.executeHere(args)
}

func (c *Command) executeHere(args []string) error {
	// Parse persistent flags in chain (root -> ... -> current)
	for _, fs := range c.persistentFlagChain() {
		if fs != nil {
			_ = fs.Parse(args) // ignore errors here; user can call cmd.Usage for details
		}
	}

	// Parse local flags
	if c.flags != nil {
		if err := c.flags.Parse(args); err != nil {
			return err
		}
		args = c.flags.Args()
	}

	if c.Args != nil {
		if err := c.Args(c, args); err != nil {
			return err
		}
	}
	if c.RunE == nil {
		if len(c.subCommands) > 0 {
			return c.Usage()
		}
		return errors.New("no command implementation")
	}
	return c.RunE(c, args)
}

func (c *Command) Usage() error {
	w := c.ErrOrStderr()
	_, _ = fmt.Fprintf(w, "Usage:\n  %s\n\n", c.Use)
	if c.Short != "" {
		_, _ = fmt.Fprintf(w, "%s\n\n", c.Short)
	}
	if len(c.subCommands) > 0 {
		_, _ = fmt.Fprintf(w, "Commands:\n")
		for _, sc := range c.subCommands {
			_, _ = fmt.Fprintf(w, "  %s\t%s\n", sc.useName(), sc.Short)
		}
		_, _ = fmt.Fprintln(w)
	}
	if c.flags != nil {
		_, _ = fmt.Fprintln(w, "Flags:")
		c.flags.PrintDefaults()
		_, _ = fmt.Fprintln(w)
	}
	if c.persistent != nil {
		_, _ = fmt.Fprintln(w, "Global Flags:")
		c.persistent.PrintDefaults()
		_, _ = fmt.Fprintln(w)
	}
	return nil
}

func MinimumNArgs(n int) func(cmd *Command, args []string) error {
	return func(cmd *Command, args []string) error {
		if len(args) < n {
			return fmt.Errorf("requires at least %d arg(s)", n)
		}
		return nil
	}
}

func ExactArgs(n int) func(cmd *Command, args []string) error {
	return func(cmd *Command, args []string) error {
		if len(args) != n {
			return fmt.Errorf("requires exactly %d arg(s)", n)
		}
		return nil
	}
}

func (c *Command) findSubcommand(name string) *Command {
	for _, sc := range c.subCommands {
		if sc.useName() == name {
			return sc
		}
	}
	return nil
}

func (c *Command) useName() string {
	// take first token of Use
	use := c.Use
	for i := 0; i < len(use); i++ {
		if use[i] == ' ' || use[i] == '\t' || use[i] == '\n' {
			return use[:i]
		}
	}
	return use
}

func (c *Command) nameOrUse() string {
	if c.Use == "" {
		return "cmd"
	}
	return c.useName()
}

func (c *Command) persistentFlagChain() []*flag.FlagSet {
	var chain []*flag.FlagSet
	cur := c
	for cur != nil {
		chain = append([]*flag.FlagSet{cur.persistent}, chain...)
		cur = cur.parent
	}
	return chain
}
