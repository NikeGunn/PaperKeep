// Package security provides cryptographic helpers used across the ScanVault API.
package security

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
)

// HashIP returns a deterministic, non-reversible HMAC-SHA256 hash of the raw IP.
// Use cfg.IPHashKey as the key. The output is safe to store in audit logs.
func HashIP(rawIP, key string) string {
	mac := hmac.New(sha256.New, []byte(key))
	mac.Write([]byte(rawIP))
	return hex.EncodeToString(mac.Sum(nil))
}
