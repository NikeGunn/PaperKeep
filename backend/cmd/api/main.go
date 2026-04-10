package main

import (
	"fmt"
	"os"

	"github.com/nikhil/scanvault-api/internal/config"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		fmt.Fprintf(os.Stderr, "config error: %v\n", err)
		os.Exit(1)
	}

	fmt.Printf("ScanVault API starting on :%s (env=%s)\n", cfg.ServerPort, cfg.Environment)
}
