package logger

type Config struct {
	Level      string `yaml:"level" env:"LOG_LEVEL" env-default:"info" env-description:"ログレベル (debug, info, warn, error)"`
	AddSource  bool   `yaml:"add_source" env:"LOG_ADD_SOURCE" env-default:"true" env-description:"ログにソースコードの場所を含めるかどうか"`
	JSONFormat bool   `yaml:"json_format" env:"LOG_JSON_FORMAT" env-default:"true" env-description:"ログをJSON形式で出力するかどうか"`
}
