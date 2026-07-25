package analyze

import (
	"reflect"
	"testing"
)

func TestBuildMetadata(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name    string
		pairs   []string
		want    map[string]any
		wantErr bool
	}{
		{
			name:  "no flags yield no metadata",
			pairs: nil,
			want:  nil,
		},
		{
			name:  "a single value becomes a one-element array",
			pairs: []string{"classpath=/a.jar"},
			want:  map[string]any{"classpath": []string{"/a.jar"}},
		},
		{
			name:  "repeated keys append in flag order",
			pairs: []string{"classpath=/a.jar", "classpath=/b.jar"},
			want:  map[string]any{"classpath": []string{"/a.jar", "/b.jar"}},
		},
		{
			name:  "an empty value registers an empty array",
			pairs: []string{"classpath="},
			want:  map[string]any{"classpath": []string{}},
		},
		{
			name:    "an entry without = is a validation error",
			pairs:   []string{"classpath"},
			wantErr: true,
		},
		{
			name:  "the split happens on the first = only",
			pairs: []string{"filter=a=b"},
			want:  map[string]any{"filter": []string{"a=b"}},
		},
		{
			name:  "a value followed by an empty value keeps the existing value",
			pairs: []string{"classpath=/a.jar", "classpath="},
			want:  map[string]any{"classpath": []string{"/a.jar"}},
		},
		{
			name:  "an empty value followed by a value appends onto the empty array",
			pairs: []string{"classpath=", "classpath=/a.jar"},
			want:  map[string]any{"classpath": []string{"/a.jar"}},
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			got, err := BuildMetadata(tt.pairs)
			if (err != nil) != tt.wantErr {
				t.Fatalf("BuildMetadata() error = %v, wantErr %v", err, tt.wantErr)
			}
			if tt.wantErr {
				return
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Fatalf("BuildMetadata() = %#v, want %#v", got, tt.want)
			}
		})
	}
}
