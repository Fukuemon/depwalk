package config

import (
	"fmt"
	"os"

	"github.com/Fukuemon/depwalk/pkg/logger"
	"github.com/ilyakaznacheev/cleanenv"
)

type Config struct {
	Logger logger.Config `yaml:"logger"`
}

func NewConfig[T Config]() (*T, error) {
	return NewConfigWithPath[T]("./config/config.yaml")
}

func NewConfigWithPath[T Config](cfgFile string) (*T, error) {
	var cfg T

	if _, err := os.Stat(cfgFile); err == nil {
		err := cleanenv.ReadConfig(cfgFile, &cfg)
		if err != nil {
			return nil, fmt.Errorf("設定ファイルの読み込みに失敗しました: %w", err)
		}
	}

	err := cleanenv.ReadEnv(&cfg)
	if err != nil {
		return nil, fmt.Errorf("環境変数の読み込みに失敗しました: %w", err)
	}

	return &cfg, nil
}
