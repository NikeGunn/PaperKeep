// Package vault implements R2/S3-compatible object storage for encrypted document pages.
package vault

import (
	"context"
	"fmt"
	"net/url"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/s3"
)

// presignURLTTL is the lifetime of all pre-signed URLs (put and get).
const presignURLTTL = 5 * time.Minute

// ObjectKey returns the R2 object key for a page blob.
// Format: vault/{account_uuid}/{doc_uuid}/{page_uuid}.enc
func ObjectKey(accountUUID, docUUID, pageUUID string) string {
	return fmt.Sprintf("vault/%s/%s/%s.enc", accountUUID, docUUID, pageUUID)
}

// ObjectStorage defines the interface for interacting with R2/S3 object storage.
// Using an interface enables straightforward mocking in tests.
type ObjectStorage interface {
	// GeneratePresignedPutURL returns a pre-signed URL for uploading a page blob.
	// The URL expires after 5 minutes. maxBytes limits the content-length.
	GeneratePresignedPutURL(ctx context.Context, key string, maxBytes int64) (string, error)

	// GeneratePresignedGetURL returns a pre-signed URL for downloading a page blob.
	// The URL expires after 5 minutes.
	GeneratePresignedGetURL(ctx context.Context, key string) (string, error)

	// DeleteObject permanently removes an object from storage.
	DeleteObject(ctx context.Context, key string) error

	// HeadObject returns the size in bytes of an object, or an error if it does not exist.
	HeadObject(ctx context.Context, key string) (sizeBytes int64, err error)
}

// R2Storage implements ObjectStorage backed by Cloudflare R2 (S3-compatible API).
type R2Storage struct {
	client   *s3.Client
	presign  *s3.PresignClient
	bucket   string
}

// NewR2Storage constructs an R2Storage from explicit credentials and endpoint.
// endpoint should be the full R2 account URL, e.g. https://<accountid>.r2.cloudflarestorage.com
func NewR2Storage(ctx context.Context, endpoint, accessKey, secretKey, bucket string) (*R2Storage, error) {
	resolver := aws.EndpointResolverWithOptionsFunc(
		func(service, region string, options ...interface{}) (aws.Endpoint, error) {
			return aws.Endpoint{
				URL:               endpoint,
				SigningRegion:     "auto",
				HostnameImmutable: true,
			}, nil
		},
	)

	cfg, err := awsconfig.LoadDefaultConfig(ctx,
		awsconfig.WithRegion("auto"),
		awsconfig.WithCredentialsProvider(
			credentials.NewStaticCredentialsProvider(accessKey, secretKey, ""),
		),
		awsconfig.WithEndpointResolverWithOptions(resolver), //nolint:staticcheck // R2 requires custom endpoint
	)
	if err != nil {
		return nil, fmt.Errorf("load aws config: %w", err)
	}

	client := s3.NewFromConfig(cfg, func(o *s3.Options) {
		o.UsePathStyle = true // R2 requires path-style addressing
	})

	return &R2Storage{
		client:  client,
		presign: s3.NewPresignClient(client),
		bucket:  bucket,
	}, nil
}

// GeneratePresignedPutURL creates a signed S3 PutObject URL valid for 5 minutes.
// maxBytes is enforced via a Content-Length condition in the query string.
func (s *R2Storage) GeneratePresignedPutURL(ctx context.Context, key string, maxBytes int64) (string, error) {
	req, err := s.presign.PresignPutObject(ctx, &s3.PutObjectInput{
		Bucket:        aws.String(s.bucket),
		Key:           aws.String(key),
		ContentLength: aws.Int64(maxBytes),
	}, func(o *s3.PresignOptions) {
		o.Expires = presignURLTTL
	})
	if err != nil {
		return "", fmt.Errorf("presign put %q: %w", key, err)
	}
	return req.URL, nil
}

// GeneratePresignedGetURL creates a signed S3 GetObject URL valid for 5 minutes.
func (s *R2Storage) GeneratePresignedGetURL(ctx context.Context, key string) (string, error) {
	req, err := s.presign.PresignGetObject(ctx, &s3.GetObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String(key),
	}, func(o *s3.PresignOptions) {
		o.Expires = presignURLTTL
	})
	if err != nil {
		return "", fmt.Errorf("presign get %q: %w", key, err)
	}
	return req.URL, nil
}

// DeleteObject permanently deletes key from the bucket.
func (s *R2Storage) DeleteObject(ctx context.Context, key string) error {
	_, err := s.client.DeleteObject(ctx, &s3.DeleteObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String(key),
	})
	if err != nil {
		return fmt.Errorf("delete object %q: %w", key, err)
	}
	return nil
}

// HeadObject checks that key exists in the bucket and returns its size.
func (s *R2Storage) HeadObject(ctx context.Context, key string) (int64, error) {
	out, err := s.client.HeadObject(ctx, &s3.HeadObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String(key),
	})
	if err != nil {
		return 0, fmt.Errorf("head object %q: %w", key, err)
	}
	if out.ContentLength == nil {
		return 0, nil
	}
	return *out.ContentLength, nil
}

// ParsePresignedURLExpiry extracts the X-Amz-Expires query parameter from a
// pre-signed URL and returns it as a time.Duration.
// Returns an error if the URL cannot be parsed or lacks the parameter.
func ParsePresignedURLExpiry(rawURL string) (time.Duration, error) {
	u, err := url.Parse(rawURL)
	if err != nil {
		return 0, fmt.Errorf("parse url: %w", err)
	}
	expiresStr := u.Query().Get("X-Amz-Expires")
	if expiresStr == "" {
		return 0, fmt.Errorf("X-Amz-Expires not found in URL")
	}
	// X-Amz-Expires is in seconds as an integer string
	var secs int64
	if _, err := fmt.Sscanf(expiresStr, "%d", &secs); err != nil {
		return 0, fmt.Errorf("parse X-Amz-Expires %q: %w", expiresStr, err)
	}
	return time.Duration(secs) * time.Second, nil
}
