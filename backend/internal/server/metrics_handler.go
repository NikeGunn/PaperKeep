package server

import (
	"net/http"

	"github.com/nikhil/scanvault-api/internal/metrics"
)

// handleMetrics returns a JSON snapshot of accumulated request metrics.
// This endpoint is internal-only (not advertised in docs) and does not
// require authentication — it returns aggregate statistics, not user data.
func (s *Server) handleMetrics(w http.ResponseWriter, r *http.Request) {
	if s.collector == nil {
		respond(w, http.StatusOK, metrics.Snapshot{})
		return
	}
	respond(w, http.StatusOK, s.collector.Snapshot())
}
