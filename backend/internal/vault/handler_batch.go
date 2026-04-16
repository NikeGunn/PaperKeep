package vault

import (
	"encoding/json"
	"net/http"

	appmiddleware "github.com/nikhil/scanvault-api/internal/middleware"
)

const maxBatchItems = 100

// BatchOp is one operation in a batch request.
type BatchOp struct {
	Op       string          `json:"op"`
	Document json.RawMessage `json:"document"`
}

// BatchRequest is the JSON body for POST /v1/vault/batch.
type BatchRequest struct {
	Operations []BatchOp `json:"operations"`
}

// BatchItemResult is a per-item result in the 207 Multi-Status response.
type BatchItemResult struct {
	Index  int    `json:"index"`
	Status int    `json:"status"`
	Error  string `json:"error,omitempty"`
}

// BatchResponse is the 207 Multi-Status body.
type BatchResponse struct {
	Results []BatchItemResult `json:"results"`
}

// HandleBatch processes a batch of vault operations.
// POST /v1/vault/batch
// Returns 207 Multi-Status with per-item success/failure.
func (h *Handler) HandleBatch(w http.ResponseWriter, r *http.Request) {
	account := appmiddleware.AccountFromContext(r.Context())
	if account == nil {
		respondErr(w, http.StatusUnauthorized, "vault.unauthorized", "Authentication required")
		return
	}

	var req BatchRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondErr(w, http.StatusBadRequest, "vault.bad_request", "Invalid JSON body")
		return
	}
	if len(req.Operations) == 0 {
		respondErr(w, http.StatusBadRequest, "vault.bad_request", "operations array is required and must not be empty")
		return
	}
	if len(req.Operations) > maxBatchItems {
		respondErr(w, http.StatusBadRequest, "vault.batch_too_large", "Batch exceeds maximum of 100 operations")
		return
	}

	results := make([]BatchItemResult, len(req.Operations))

	for i, op := range req.Operations {
		result := BatchItemResult{Index: i}
		switch op.Op {
		case "create":
			var docReq CreateDocumentRequest
			if err := json.Unmarshal(op.Document, &docReq); err != nil {
				result.Status = http.StatusBadRequest
				result.Error = "invalid document payload: " + err.Error()
				results[i] = result
				continue
			}
			if docReq.UUID == "" || docReq.EncryptedMetadata == "" || docReq.MetadataNonce == "" {
				result.Status = http.StatusBadRequest
				result.Error = "uuid, encrypted_metadata, and metadata_nonce are required"
				results[i] = result
				continue
			}
			_, err := h.svc.CreateDocument(r.Context(), account.ID, docReq)
			if err != nil {
				result.Status = http.StatusUnprocessableEntity
				result.Error = err.Error()
			} else {
				result.Status = http.StatusCreated
			}

		case "update":
			type updatePayload struct {
				UUID string `json:"uuid"`
				UpdateDocumentRequest
			}
			var p updatePayload
			if err := json.Unmarshal(op.Document, &p); err != nil {
				result.Status = http.StatusBadRequest
				result.Error = "invalid document payload: " + err.Error()
				results[i] = result
				continue
			}
			if p.UUID == "" {
				result.Status = http.StatusBadRequest
				result.Error = "uuid is required for update"
				results[i] = result
				continue
			}
			_, _, err := h.svc.UpdateDocument(r.Context(), account.ID, p.UUID, p.UpdateDocumentRequest)
			if err != nil {
				result.Status = http.StatusUnprocessableEntity
				result.Error = err.Error()
			} else {
				result.Status = http.StatusOK
			}

		case "delete":
			type deletePayload struct {
				UUID string `json:"uuid"`
			}
			var p deletePayload
			if err := json.Unmarshal(op.Document, &p); err != nil {
				result.Status = http.StatusBadRequest
				result.Error = "invalid document payload: " + err.Error()
				results[i] = result
				continue
			}
			if p.UUID == "" {
				result.Status = http.StatusBadRequest
				result.Error = "uuid is required for delete"
				results[i] = result
				continue
			}
			if err := h.svc.DeleteDocument(r.Context(), account.ID, p.UUID); err != nil {
				result.Status = http.StatusUnprocessableEntity
				result.Error = err.Error()
			} else {
				result.Status = http.StatusNoContent
			}

		default:
			result.Status = http.StatusBadRequest
			result.Error = "unknown op: must be create, update, or delete"
		}
		results[i] = result
	}

	respond(w, http.StatusMultiStatus, BatchResponse{Results: results})
}
