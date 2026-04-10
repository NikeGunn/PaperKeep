# =============================================================================
# ScanVault — Root Makefile
# Delegates to subdirectory build systems.
# =============================================================================

.PHONY: android backend intelligence test-all lint-all clean-all help

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

android: ## Build the Android app
	cd android && ./gradlew assembleDebug

backend: ## Build the Go backend
	cd backend && $(MAKE)

intelligence: ## Build/run the Python intelligence layer
	cd intelligence && $(MAKE)

test-all: ## Run all tests (Go + Python)
	cd backend && $(MAKE) test
	cd intelligence && $(MAKE) test

lint-all: ## Run all linters (Go + Python + Android)
	cd android && ./gradlew lint detekt
	cd backend && $(MAKE) lint
	cd intelligence && $(MAKE) lint

clean-all: ## Clean all build artifacts
	cd android && ./gradlew clean
	cd backend && $(MAKE) clean
	cd intelligence && $(MAKE) clean
