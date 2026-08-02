package analyzer

import (
	"bufio"
	"bytes"
	"errors"
	"fmt"
	"io"
	"os/exec"
)

// Command は Analyzer process の起動方法を表す。
type Command struct {
	Path string
	Args []string
	Dir  string
	// Stderr は Analyzer の stderr を届いた順にそのまま渡す先 (optional)。
	// Core は stderr を protocol データとして解釈しない。
	// nil のときは [Result.Stderr] に溜めるだけになる。
	Stderr io.Writer
}

// Runner は解析要求 1 件につき Analyzer process を 1 つ起動する。
type Runner struct {
	command Command
}

func New(command Command) Runner {
	return Runner{command: command}
}

// Result は Analyzer process の終了状態。
//
// stdout の内容はここに溜めない。[Runner.Run] の onLine へ 1 行ずつ届いた順に
// 渡す。全部読み終えてから返すと、大きな graph で待ち時間とメモリが膨らむため。
type Result struct {
	ExitCode int
	Stderr   string
	// ReadError は stdout の最初の読み取り失敗 (あれば)。
	// 失敗前に届いた行は既に onLine へ渡してある。読み取りはそこで止まる。
	ReadError error
}

// Run は Analyzer process を起動し、stdin へ input を書き、stdout を EOF まで
// 1 行ずつ onLine へ流す (区切り文字を含む。終端のない最後の行はそのまま渡す)。
// stdout を使わない呼び出し側は onLine に nil を渡してよい。
//
// 中身は本 package にとって opaque である。要求行の組み立ても stdout の解釈も
// protocol package の責務であり、ここに持ち込むと process 制御と wire 表現が
// 混ざる。
func (r Runner) Run(input []byte, onLine func(line []byte)) (Result, error) {
	var result Result
	if r.command.Path == "" {
		return result, errors.New("analyzer command path is required")
	}

	cmd := exec.Command(r.command.Path, r.command.Args...)
	cmd.Dir = r.command.Dir

	stdin, err := cmd.StdinPipe()
	if err != nil {
		return result, err
	}
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return result, err
	}
	stderr, err := cmd.StderrPipe()
	if err != nil {
		return result, err
	}

	if err := cmd.Start(); err != nil {
		return result, err
	}

	stderrDone := make(chan []byte, 1)
	go func() {
		var buf bytes.Buffer
		source := io.Reader(stderr)
		if r.command.Stderr != nil {
			source = io.TeeReader(stderr, r.command.Stderr)
		}
		if _, err := buf.ReadFrom(source); err != nil {
			// 転送に失敗しても読み捨てを続ける。ここで読むのをやめると pipe が
			// 詰まり、Analyzer が終了できなくなる。
			_, _ = buf.ReadFrom(stderr)
		}
		stderrDone <- buf.Bytes()
	}()

	// finish は Wait の前に stderr を EOF まで読み切る。os/exec が Wait 前の
	// 読み切りを要求しており、怠ると末尾の stderr が失われる。
	finish := func() error {
		result.Stderr = string(<-stderrDone)
		waitErr := cmd.Wait()
		result.ExitCode = exitCode(waitErr)
		return waitErr
	}

	if _, err := stdin.Write(input); err != nil {
		_ = stdin.Close()
		result.ReadError = readStdout(stdout, onLine)
		_ = finish()
		return result, err
	}
	if err := stdin.Close(); err != nil {
		result.ReadError = readStdout(stdout, onLine)
		_ = finish()
		return result, err
	}

	result.ReadError = readStdout(stdout, onLine)
	waitErr := finish()

	if waitErr != nil && result.ExitCode == 0 {
		return result, waitErr
	}
	return result, nil
}

// readStdout は stdout を 1 行ずつ onLine へ流し、最初の読み取り失敗を返す。
// 失敗した時点で読み取りを止める。
func readStdout(stdout io.Reader, onLine func(line []byte)) error {
	reader := bufio.NewReader(stdout)
	for {
		line, err := reader.ReadBytes('\n')
		if len(line) > 0 && onLine != nil {
			onLine(line)
		}
		if err == nil {
			continue
		}
		if errors.Is(err, io.EOF) {
			return nil
		}
		return fmt.Errorf("read analyzer stdout: %w", err)
	}
}

func exitCode(err error) int {
	if err == nil {
		return 0
	}
	var exitErr *exec.ExitError
	if errors.As(err, &exitErr) {
		return exitErr.ExitCode()
	}
	return 0
}
