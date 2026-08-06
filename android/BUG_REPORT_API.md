# Android bug-report API contract

The Android client sends a pseudonymous, installation-scoped `reporterId` with every bug report.
Use that value as the lookup key for report throttles or blocks. It is a namespaced SHA-256 digest;
the raw GFN device ID, account ID, and email address are not sent.

`reporterId` is useful for ordinary abuse control, but it is not a hardware-backed identity. Clearing
app data or reinstalling can create a new identifier, and a modified client can forge one. If stronger
enforcement is needed, combine it server-side with rate limits or authenticated account signals that
the service already has instead of asking the app to upload raw hardware identifiers.

## Custom rejection shown by the app

Return HTTP `403` with this JSON when the reporter is blocked:

```json
{
  "ok": false,
  "error": {
    "code": "REPORTER_BANNED",
    "message": "Bug reporting is disabled for this installation. Contact support if you believe this is a mistake.",
    "retryable": false
  }
}
```

The Android app displays `error.message` in its existing bug-report error panel. It normalizes
whitespace and limits the public message to 320 characters. It never displays an unstructured HTML
or proxy error body.

Use HTTP `429` and `code: "REPORT_RATE_LIMITED"` for a temporary throttle. Set `retryable` to `true`
and write the retry instruction in `message`.

## OpenAPI 3.1 schema

```yaml
openapi: 3.1.0
info:
  title: OpenNOW Android bug reports
  version: 1.0.0
paths:
  /releases/opennow/bug-reports:
    post:
      operationId: createOpenNowAndroidBugReport
      requestBody:
        required: true
        content:
          multipart/form-data:
            schema:
              type: object
              required:
                - title
                - description
                - versionName
                - versionCode
                - platform
                - reporterId
                - metadata
              properties:
                title:
                  type: string
                  minLength: 1
                description:
                  type: string
                  minLength: 50
                versionName:
                  type: string
                versionCode:
                  type: string
                platform:
                  type: string
                  const: android
                reporterId:
                  type: string
                  pattern: '^br1_[0-9a-f]{64}$'
                  description: Pseudonymous installation key used for abuse prevention.
                metadata:
                  type: string
                  contentMediaType: application/json
                  description: A JSON object serialized as a multipart text field.
                files:
                  type: array
                  maxItems: 5
                  items:
                    type: string
                    contentMediaType: application/octet-stream
                    maxLength: 10485760
      responses:
        '201':
          description: Report accepted.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/BugReportAccepted'
        '400':
          description: Invalid report.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/BugReportRejected'
        '403':
          description: This reporter is blocked.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/BugReportRejected'
        '429':
          description: This reporter is temporarily rate-limited.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/BugReportRejected'
components:
  schemas:
    BugReportAccepted:
      type: object
      required: [ok, reportId]
      properties:
        ok:
          type: boolean
          const: true
        reportId:
          type: string
    BugReportRejected:
      type: object
      required: [ok, error]
      properties:
        ok:
          type: boolean
          const: false
        error:
          type: object
          required: [code, message, retryable]
          properties:
            code:
              type: string
              maxLength: 80
            message:
              type: string
              minLength: 1
              maxLength: 320
            retryable:
              type: boolean
```

## Server-side block record

A minimal persistent record can use this shape:

```json
{
  "reporterId": "br1_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "enabled": true,
  "publicMessage": "Bug reporting is disabled for this installation.",
  "internalReason": "Repeated reports without actionable evidence",
  "createdAt": "2026-08-05T20:00:00Z",
  "expiresAt": null
}
```

Before accepting files, validate `reporterId`, load the enabled non-expired block, and return the
structured `403` response using `publicMessage`. Keep `internalReason` server-only. Store the
`reporterId` beside accepted reports so an administrator can copy it from a report into the block
table. Check the block before persisting attachments to avoid unnecessary storage and processing.
