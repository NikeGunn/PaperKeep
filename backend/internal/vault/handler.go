package vault

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	appmiddleware "github.com/nikhil/scanvault-api/internal/middleware"
)

// Handler exposes vault operations over HTTP.
type Handler struct {
	svc *Service
}

// NewHandler wraps a Service in an HTTP handler.
func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

// RegisterRoutes mounts vault routes on r. All routes require authentication.
func (h *Handler) RegisterRoutes(r chi.Router) {
	r.Post("/vault/documents", h.HandleCreateDocument)
	r.Get("/vault/documents", h.HandleListDocuments)
	r.Get("/vault/documents/{uuid}", h.HandleGetDocument)
}

// -------------------------------------------------------------------------
// POST /v1/vault/documents
// -------------------------------------------------------------------------

// HandleCreateDocument creates a new vault document.
//
//	201 — created
//	400 — invalid input (missing fields, bad base64, invalid UUID format)
//	401 — unauthenticated (handled by middleware)
//	409 — duplicate UUID
func (h *Handler) HandleCreateDocument(w http.ResponseWriter, r *http.Request) {
	account := appmiddleware.AccountFromContext(r.Context())
	if account == nil {
		respondErr(w, http.StatusUnauthorized, "vault.unauthorized", "Authentication required")
		return
	}

	var req CreateDocumentRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondErr(w, http.StatusBadRequest, "vault.bad_request", "Invalid JSON body")
		return
	}

	if req.UUID == "" || req.EncryptedMetadata == "" || req.MetadataNonce == "" {
		respondErr(w, http.StatusBadRequest, "vault.missing_fields", "uuid, encrypted_metadata, and metadata_nonce are required")
		return
	}

	doc, err := h.svc.CreateDocument(r.Context(), account.ID, req)
	if err != nil {
		switch {
		case errors.Is(err, ErrInvalidUUID):
			respondErr(w, http.StatusBadRequest, "vault.invalid_uuid", "uuid must be a valid v7 UUID")
		case errors.Is(err, ErrDuplicateUUID):
			respondErr(w, http.StatusConflict, "vault.duplicate_uuid", "A document with this UUID already exists")
		default:
			respondErr(w, http.StatusInternalServerError, "vault.internal", "Internal server error")
		}
		return
	}

	respond(w, http.StatusCreated, doc)
}

// -------------------------------------------------------------------------
// GET /v1/vault/documents
// -------------------------------------------------------------------------

// HandleListDocuments returns a cursor-paginated list of documents.
//
//	200 — success
//	401 — unauthenticated
func (h *Handler) HandleListDocuments(w http.ResponseWriter, r *http.Request) {
	account := appmiddleware.AccountFromContext(r.Context())
	if account == nil {
		respondErr(w, http.StatusUnauthorized, "vault.unauthorized", "Authentication required")
		return
	}

	after := r.URL.Query().Get("after")
	limitStr := r.URL.Query().Get("limit")
	limit := int32(50)
	if limitStr != "" {
		if n, err := strconv.Atoi(limitStr); err == nil && n > 0 {
			limit = int32(n)
		}
	}

	resp, err := h.svc.ListDocuments(r.Context(), account.ID, after, limit)
	if err != nil {
		respondErr(w, http.StatusInternalServerError, "vault.internal", "Internal server error")
		return
	}

	respond(w, http.StatusOK, resp)
}

// -------------------------------------------------------------------------
// GET /v1/vault/documents/{uuid}
// -------------------------------------------------------------------------

// HandleGetDocument returns a single document with its page manifests.
//
//	200 — success
//	401 — unauthenticated
//	404 — not found or belongs to another account
type getDocumentResponse struct {
	Document DocumentResponse `json:"document"`
	Pages    []pageManifest   `json:"pages"`
}

type pageManifest struct {
	UUID          string `json:"uuid"`
	PageIndex     int32  `json:"page_index"`
	EncryptedSize int64  `json:"encrypted_size"`
	Checksum      string `json:"checksum"` // hex-encoded
	Version       int64  `json:"version"`
	Status        string `json:"status"`
}

func (h *Handler) HandleGetDocument(w http.ResponseWriter, r *http.Request) {
	account := appmiddleware.AccountFromContext(r.Context())
	if account == nil {
		respondErr(w, http.StatusUnauthorized, "vault.unauthorized", "Authentication required")
		return
	}

	docUUID := chi.URLParam(r, "uuid")
	if docUUID == "" {
		respondErr(w, http.StatusNotFound, "vault.not_found", "Document not found")
		return
	}

	doc, pages, err := h.svc.GetDocument(r.Context(), account.ID, docUUID)
	if err != nil {
		if errors.Is(err, ErrDocumentNotFound) {
			respondErr(w, http.StatusNotFound, "vault.not_found", "Document not found")
			return
		}
		respondErr(w, http.StatusInternalServerError, "vault.internal", "Internal server error")
		return
	}

	manifests := make([]pageManifest, len(pages))
	for i, p := range pages {
		manifests[i] = pageManifest{
			UUID:          uuidToString(p.Uuid),
			PageIndex:     p.PageIndex,
			EncryptedSize: p.EncryptedSize,
			Checksum:      fmt.Sprintf("%x", p.Checksum),
			Version:       p.Version,
			Status:        p.Status,
		}
	}

	respond(w, http.StatusOK, getDocumentResponse{Document: doc, Pages: manifests})
}

// -------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------

type errEnvelope struct {
	Error errBody `json:"error"`
}

type errBody struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

func respondErr(w http.ResponseWriter, status int, code, message string) {
	respond(w, status, errEnvelope{Error: errBody{Code: code, Message: message}})
}

func respond(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
