// Package jsonl provides utilities for reading and writing JSON Lines (newline-delimited JSON).
package jsonl

import (
	"bufio"
	"encoding/json"
	"io"
)

// Encoder writes one JSON object per line.
type Encoder struct {
	w *bufio.Writer
}

// NewEncoder creates a new JSONL encoder.
func NewEncoder(w io.Writer) *Encoder {
	return &Encoder{w: bufio.NewWriter(w)}
}

// Encode marshals v as JSON and writes it as a single line.
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

// Decoder reads one JSON object per line.
type Decoder struct {
	s *bufio.Scanner
}

// NewDecoder creates a new JSONL decoder.
func NewDecoder(r io.Reader) *Decoder {
	return &Decoder{s: bufio.NewScanner(r)}
}

// Decode reads the next line and unmarshals it into v.
// Returns (false, nil) at EOF, (true, nil) on success, (false, err) on error.
func (d *Decoder) Decode(v any) (bool, error) {
	if !d.s.Scan() {
		if err := d.s.Err(); err != nil {
			return false, err
		}
		return false, nil
	}
	return true, json.Unmarshal(d.s.Bytes(), v)
}

