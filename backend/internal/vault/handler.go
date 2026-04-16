// Package vault exposes vault operations over HTTP.
package vault

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strconv"
	"time"

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

// RegisterRoutes mounts all vault routes on r. All routes require authentication.
func (h *Handler) RegisterRoutes(r chi.Router) {
	// Documents
	r.Post("/vault/documents", h.HandleCreateDocument)
	r.Get("/vault/documents", h.HandleListDocuments)
	r.Get("/vault/documents/{uuid}", h.HandleGetDocument)
	r.Put("/vault/documents/{uuid}", h.HandleUpdateDocument)
	r.Delete("/vault/documents/{uuid}", h.HandleDeleteDocument)

	// Pages
	r.Post("/vault/documents/{doc_uuid}/pages/upload-url", h.HandleRequestUploadURL)
	r.Post("/vault/documents/{doc_uuid}/pages/{page_uuid}/confirm", h.HandleConfirmUpload)
	r.Get("/vault/documents/{doc_uuid}/pages/{page_uuid}/download-url", h.HandleDownloadURL)

	// Sync manifest
	r.Get("/vault/manifest", h.HandleManifest)

	// Batch operations (3A.2)
	r.Post("/vault/batch", h.HandleBatch)
}

// -------------------------------------------------------------------------
// POST /v1/vault/documents
// -------------------------------------------------------------------------

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
		case errors.Is(err, ErrQuotaDocuments):
			respondErr(w, http.StatusPaymentRequired, "vault.quota_exceeded", "Document quota exceeded")
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

func (h *Handler) HandleListDocuments(w http.ResponseWriter, r *http.Request) {
	account := appmiddleware.AccountFromContext(r.Context())
	if account == nil {
		respondErr(w, http.StatusUnauthorized, "vault.unauthorized", "Authentication required")
		return
	}

	after := r.URL.Query().Get("after")
	limit := int32(50)
	if s := r.URL.Query().Get("limit"); s != "" {
		if n, err := strconv.Atoi(s); err == nil && n > 0 {
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

type getDocumentResponse struct {
	Document DocumentResponse `json:"document"`
	Pages    []pageManifest   `json:"pages"`
}

type pageManifest struct {
	UUID          string `json:"uuid"`
	PageIndex     int32  `json:"page_index"`
	EncryptedSize int64  `json:"encrypted_size"`
	Checksum      string `json:"checksum"`
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
// PUT /v1/vault/documents/{uuid}  — 2A.5 optimistic concurrency
// -------------------------------------------------------------------------

func (h *Handler) HandleUpdateDocument(w http.ResponseWriter, r *http.Request) {
	account := appmiddleware.AccountFromContext(r.Context())
	if account == nil {
		respondErr(w, http.StatusUnauthorized, "vault.unauthorized", "Authentication required")
		return
	}

	docUUID := chi.URLParam(r, "uuid")

	var req UpdateDocumentRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondErr(w, http.StatusBadRequest, "vault.bad_request", "Invalid JSON body")
		return
	}
	if req.EncryptedMetadata == "" || req.MetadataNonce == "" {
		respondErr(w, http.StatusBadRequest, "vault.missing_fields", "encrypted_metadata, metadata_nonce, and expected_version are required")
		return
	}

	doc, conflict, err := h.svc.UpdateDocument(r.Context(), account.ID, docUUID, req)
	if err != nil {
		switch {
		case errors.Is(err, ErrDocumentNotFound):
			respondErr(w, http.StatusNotFound, "vault.not_found", "Document not found")
		case errors.Is(err, ErrDocumentConflict):
			respond(w, http.StatusConflict, conflict)
		default:
			respondErr(w, http.StatusInternalServerError, "vault.internal", "Internal server error")
		}
		return
	}
	respond(w, http.StatusOK, doc)
}

// -------------------------------------------------------------------------
// DELETE /v1/vault/documents/{uuid}  — 2A.6 soft delete
// -------------------------------------------------------------------------

func (h *Handler) HandleDeleteDocument(w http.ResponseWriter, r *http.Request) {
	account := appmiddleware.AccountFromContext(r.Context())
	if account == nil {
		respondErr(w, http.StatusUnauthorized, "vault.unauthorized", "Authentication required")
		return
	}

	docUUID := chi.URLParam(r, "uuid")
	if err := h.svc.DeleteDocument(r.Context(), account.ID, docUUID); err != nil {
		if errors.Is(err, ErrDocumentNotFound) {
			respondErr(w, http.StatusNotFound, "vault.not_found", "Document not found")
			return
		}
		respondErr(w, http.StatusInternalServerError, "vault.internal", "Internal server error")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// -------------------------------------------------------------------------
// POST /v1/vault/documents/{doc_uuid}/pages/upload-url  — 2A.7
// -------------------------------------------------------------------------

func (h *Handler) HandleRequestUploadURL(w http.ResponseWriter, r *http.Request) {
	account := appmiddleware.AccountFromContext(r.Context())
	if account == nil {
		respondErr(w, http.StatusUnauthorized, "vault.unauthorized", "Authentication required")
		return
	}

	docUUID := chi.URLParam(r, "doc_uuid")

	var req RequestUploadURLRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondErr(w, http.StatusBadRequest, "vault.bad_request", "Invalid JSON body")
		return
	}
	if req.PageUUID == "" || req.EncryptedSize <= 0 || req.Checksum == "" {
		respondErr(w, http.StatusBadRequest, "vault.missing_fields", "page_uuid, encrypted_size, and checksum are required")
		return
	}

	resp, err := h.svc.RequestUploadURL(r.Context(), account.ID, docUUID, req)
	if err != nil {
		switch {
		case errors.Is(err, ErrDocumentNotFound):
			respondErr(w, http.StatusNotFound, "vault.not_found", "Document not found")
		case errors.Is(err, ErrPageSizeExceeded):
			respondErr(w, http.StatusRequestEntityTooLarge, "vault.page_too_large", "Page exceeds 50 MB limit")
		case errors.Is(err, ErrQuotaStorage):
			respondErr(w, http.StatusPaymentRequired, "vault.quota_exceeded", "Storage quota exceeded")
		case errors.Is(err, ErrPageIndexTaken):
			respondErr(w, http.StatusConflict, "vault.page_index_taken", "Page index already exists")
		default:
			respondErr(w, http.StatusInternalServerError, "vault.internal", "Internal server error")
		}
		return
	}
	respond(w, http.StatusCreated, resp)
}

// -------------------------------------------------------------------------
// POST /v1/vault/documents/{doc_uuid}/pages/{page_uuid}/confirm  — 2A.7
// -------------------------------------------------------------------------

func (h *Handler) HandleConfirmUpload(w http.ResponseWriter, r *http.Request) {
	account := appmiddleware.AccountFromContext(r.Context())
	if account == nil {
		respondErr(w, http.StatusUnauthorized, "vault.unauthorized", "Authentication required")
		return
	}

	docUUID := chi.URLParam(r, "doc_uuid")
	pageUUID := chi.URLParam(r, "page_uuid")

	if err := h.svc.ConfirmPageUpload(r.Context(), account.ID, docUUID, pageUUID); err != nil {
		switch {
		case errors.Is(err, ErrPageNotFound):
			respondErr(w, http.StatusNotFound, "vault.page_not_found", "Page not found")
		case errors.Is(err, ErrPageNotInR2):
			respondErr(w, http.StatusUnprocessableEntity, "vault.upload_missing", "Page blob not found in R2; re-upload required")
		case errors.Is(err, ErrPageSizeMismatch):
			respondErr(w, http.StatusUnprocessableEntity, "vault.size_mismatch", "Confirmed size does not match R2 object")
		default:
			respondErr(w, http.StatusInternalServerError, "vault.internal", "Internal server error")
		}
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// -------------------------------------------------------------------------
// GET /v1/vault/documents/{doc_uuid}/pages/{page_uuid}/download-url  — 2A.8
// -------------------------------------------------------------------------

func (h *Handler) HandleDownloadURL(w http.ResponseWriter, r *http.Request) {
	account := appmiddleware.AccountFromContext(r.Context())
	if account == nil {
		respondErr(w, http.StatusUnauthorized, "vault.unauthorized", "Authentication required")
		return
	}

	docUUID := chi.URLParam(r, "doc_uuid")
	pageUUID := chi.URLParam(r, "page_uuid")

	resp, err := h.svc.GetPageDownloadURL(r.Context(), account.ID, docUUID, pageUUID)
	if err != nil {
		if errors.Is(err, ErrPageNotFound) {
			respondErr(w, http.StatusNotFound, "vault.page_not_found", "Page not found")
			return
		}
		respondErr(w, http.StatusInternalServerError, "vault.internal", "Internal server error")
		return
	}
	respond(w, http.StatusOK, resp)
}

// -------------------------------------------------------------------------
// GET /v1/vault/manifest  — 2A.11
// -------------------------------------------------------------------------

func (h *Handler) HandleManifest(w http.ResponseWriter, r *http.Request) {
	account := appmiddleware.AccountFromContext(r.Context())
	if account == nil {
		respondErr(w, http.StatusUnauthorized, "vault.unauthorized", "Authentication required")
		return
	}

	sinceStr := r.URL.Query().Get("since")
	since := time.Time{}
	if sinceStr != "" {
		parsed, err := time.Parse(time.RFC3339, sinceStr)
		if err != nil {
			respondErr(w, http.StatusBadRequest, "vault.invalid_since", "since must be an ISO-8601 timestamp")
			return
		}
		since = parsed
	}

	after := r.URL.Query().Get("after")
	limit := int32(200)
	if s := r.URL.Query().Get("limit"); s != "" {
		if n, err := strconv.Atoi(s); err == nil && n > 0 {
			limit = int32(n)
		}
	}

	resp, err := h.svc.GetManifest(r.Context(), account.ID, since, after, limit)
	if err != nil {
		respondErr(w, http.StatusInternalServerError, "vault.internal", "Internal server error")
		return
	}
	respond(w, http.StatusOK, resp)
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
