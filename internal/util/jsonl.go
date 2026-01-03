package util

import (
	"bufio"
	"encoding/json"
	"io"
)

// Encoder writes 1 JSON object per line.
type Encoder struct {
	w *bufio.Writer
}

func NewEncoder(w io.Writer) *Encoder {
	return &Encoder{w: bufio.NewWriter(w)}
}

func (e *Encoder) Encode(v any) error {
	b, err := json.Marshal(v)
	if err != nil {
		return err
	}
	if _, err := e.w.Write(b); err != nil {
		return err
	}
	if err := e.w.WriteByte('\n'); err != nil {
		return err
	}
	return e.w.Flush()
}

// Decoder reads 1 JSON object per line.
type Decoder struct {
	s *bufio.Scanner
}

func NewDecoder(r io.Reader) *Decoder {
	return &Decoder{s: bufio.NewScanner(r)}
}

func (d *Decoder) Decode(v any) (bool, error) {
	if !d.s.Scan() {
		if err := d.s.Err(); err != nil {
			return false, err
		}
		return false, nil
	}
	return true, json.Unmarshal(d.s.Bytes(), v)
}

