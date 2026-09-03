import CryptoKit
import Foundation
import NaturalLanguage

#if canImport(UIKit)
import UIKit
#endif

/// Bug report submission, ported from `BugReports.kt` / `BugReportLanguage.kt`.
///
/// Two ideas carry over from the Android build and are worth restating, because they are what
/// makes the difference between a report a maintainer can act on and one that gets closed:
///
/// 1. **Evidence is attached automatically.** People describe symptoms from memory, badly. The
///    pre-flight deck below collects what the app already knows — device, negotiated stream, server,
///    codec probe — so nobody has to remember their bitrate.
/// 2. **The description is validated for substance, not length.** "it doesnt work" passes a
///    50-character check if you pad it. The rules below look for words, distinct words, and the
///    absence of keyboard mashing.

// MARK: - Endpoint

enum BugReportEndpoint {
    static let url = URL(string: "https://api.printedwaste.com/releases/opennow-ios/bug-reports")!
    static let maxFiles = 5
    static let maxFileBytes = 10 * 1024 * 1024
    static let reporterIdPrefix = "br1_"

    /// Distinct from the Android namespace on purpose: the same person on two devices is two
    /// reporters, and a block applied to one must not silently follow them to the other.
    static let reporterIdNamespace = "opennow-ios-bug-report-v1"
}

// MARK: - Validation

enum BugReportValidation {
    static let minimumMeaningfulCharacters = 50
    static let minimumWords = 8
    static let minimumUniqueWords = 6
    static let minimumEnglishConfidence = 0.50

    /// Letters and digits only. Padding with punctuation or newlines does not count.
    static func meaningfulCharacterCount(_ description: String) -> Int {
        description.reduce(into: 0) { count, character in
            if character.isLetter || character.isNumber { count += 1 }
        }
    }

    static func titleError(_ title: String) -> String? {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return "Enter a short title." }
        if trimmed.count < 8 { return "Give the title enough detail to tell reports apart." }
        if hasRepeatedRun(trimmed) { return "Remove the repeated characters from the title." }
        return nil
    }

    static func descriptionError(_ description: String) -> String? {
        let meaningful = meaningfulCharacterCount(description)
        if meaningful < minimumMeaningfulCharacters {
            return "Describe what happened using at least \(minimumMeaningfulCharacters) letters or numbers."
        }
        let words = wordsIn(description)
        if words.count < minimumWords || Set(words).count < minimumUniqueWords {
            return "Say what you did, what happened, and what you expected instead."
        }
        if hasRepeatedRun(description) || isPadding(words) {
            return "Remove the repeated text and describe the actual problem."
        }
        return nil
    }

    /// Progress copy for under the description field. Nil once the text is acceptable.
    static func descriptionProgress(_ description: String) -> String? {
        let meaningful = meaningfulCharacterCount(description)
        if meaningful < minimumMeaningfulCharacters {
            return "\(meaningful) / \(minimumMeaningfulCharacters) characters — add what you were doing when it happened"
        }
        return descriptionError(description)
    }

    /// English-only, because the maintainers read English and a mistranslated report wastes
    /// everybody's time. Uses `NLLanguageRecognizer` — the platform equivalent of the ML Kit
    /// identifier Android uses, with no extra dependency.
    static func languageError(title: String, description: String) -> String? {
        let sample = "\(title)\n\(description)".trimmingCharacters(in: .whitespacesAndNewlines)
        guard sample.count >= 20 else { return nil }
        let recognizer = NLLanguageRecognizer()
        recognizer.processString(sample)
        let hypotheses = recognizer.languageHypotheses(withMaximum: 3)
        let english = hypotheses[.english] ?? 0
        // Only reject when another language is confidently winning. A short, terse but valid
        // English report should not be blocked by a low-confidence guess.
        guard let best = hypotheses.max(by: { $0.value < $1.value }) else { return nil }
        if best.key == .english || english >= minimumEnglishConfidence { return nil }
        guard best.value >= 0.75 else { return nil }
        return "Reports have to be in English so the maintainers can read them."
    }

    private static func wordsIn(_ text: String) -> [String] {
        text.lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { $0.count >= 2 }
    }

    /// Five or more of the same character in a row — "aaaaaa", "!!!!!!".
    private static func hasRepeatedRun(_ text: String) -> Bool {
        var run = 0
        var previous: Character?
        for character in text where !character.isWhitespace {
            if character == previous {
                run += 1
                if run >= 4 { return true }
            } else {
                run = 0
                previous = character
            }
        }
        return false
    }

    /// The same word over and over is padding, not a description.
    private static func isPadding(_ words: [String]) -> Bool {
        guard words.count >= 4 else { return false }
        return Double(Set(words).count) / Double(words.count) < 0.4
    }
}

// MARK: - Reporter identity

enum BugReportReporter {
    /// Installation-scoped, pseudonymous. The raw device ID is never uploaded — a namespaced
    /// SHA-256 digest gives the service something stable to rate-limit without letting a report
    /// be linked back to the GFN credential.
    static func reporterId(stableDeviceId: String) -> String? {
        let trimmed = stableDeviceId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let input = Data("\(BugReportEndpoint.reporterIdNamespace):\(trimmed)".utf8)
        let digest = SHA256.hash(data: input)
        return BugReportEndpoint.reporterIdPrefix + digest.map { String(format: "%02x", $0) }.joined()
    }

    static func isValid(_ reporterId: String) -> Bool {
        guard reporterId.hasPrefix(BugReportEndpoint.reporterIdPrefix) else { return false }
        let hex = reporterId.dropFirst(BugReportEndpoint.reporterIdPrefix.count)
        return hex.count == 64 && hex.allSatisfy { $0.isHexDigit && !$0.isUppercase }
    }
}

// MARK: - Pre-flight deck

/// One fact the maintainer would otherwise have to ask for.
struct BugReportPreflightItem: Identifiable, Equatable {
    enum Kind: Equatable {
        case fact
        /// Matches something already known to be broken. Shown in amber with an override.
        case knownIssue(key: String)
    }

    let label: String
    let value: String
    var kind: Kind = .fact

    var id: String { label }
}

struct BugReportPreflightDeck: Identifiable, Equatable {
    /// Identity is the content: presenting the same deck twice is presenting the same report.
    var id: String { items.map(\.id).joined(separator: "|") }

    var items: [BugReportPreflightItem] = []

    var knownIssue: BugReportPreflightItem? {
        items.first { if case .knownIssue = $0.kind { return true } else { return false } }
    }

    var knownIssueKey: String? {
        guard case .knownIssue(let key)? = knownIssue?.kind else { return nil }
        return key
    }
}

// MARK: - Report

struct BugReportAttachment: Equatable {
    let fileName: String
    let contentType: String
    let data: Data
}

struct BugReportDraft: Equatable {
    var title: String = ""
    var description: String = ""
    /// Set when the user asserts their problem is distinct from the matched known issue.
    var overridesKnownIssue: Bool = false
}

struct BugReportSubmission: Equatable {
    let title: String
    let description: String
    let versionName: String
    let versionCode: String
    let reporterId: String
    let metadata: String
    let attachments: [BugReportAttachment]
}

enum BugReportError: LocalizedError {
    case invalid(String)
    case server(code: String?, message: String, retryable: Bool)

    var errorDescription: String? {
        switch self {
        case .invalid(let message): return message
        case .server(_, let message, _): return message
        }
    }

    var isRetryable: Bool {
        switch self {
        case .invalid: return false
        case .server(_, _, let retryable): return retryable
        }
    }
}

enum BugReportClient {

    static func buildRequest(_ submission: BugReportSubmission) throws -> URLRequest {
        let title = submission.title.trimmingCharacters(in: .whitespacesAndNewlines)
        let description = submission.description.trimmingCharacters(in: .whitespacesAndNewlines)

        if let error = BugReportValidation.titleError(title) { throw BugReportError.invalid(error) }
        if let error = BugReportValidation.descriptionError(description) { throw BugReportError.invalid(error) }
        if let error = BugReportValidation.languageError(title: title, description: description) {
            throw BugReportError.invalid(error)
        }
        guard BugReportReporter.isValid(submission.reporterId) else {
            throw BugReportError.invalid("This installation has no valid reporting ID.")
        }
        guard submission.attachments.count <= BugReportEndpoint.maxFiles else {
            throw BugReportError.invalid("Reports support up to \(BugReportEndpoint.maxFiles) attachments.")
        }
        for attachment in submission.attachments where attachment.data.count > BugReportEndpoint.maxFileBytes {
            throw BugReportError.invalid("\(attachment.fileName) is larger than 10 MB.")
        }

        let boundary = "opennow.\(UUID().uuidString)"
        var request = URLRequest(url: BugReportEndpoint.url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        var body = Data()
        func appendField(_ name: String, _ value: String) {
            body.append(Data("--\(boundary)\r\n".utf8))
            body.append(Data("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".utf8))
            body.append(Data("\(value)\r\n".utf8))
        }

        appendField("title", title)
        appendField("description", description)
        appendField("versionName", submission.versionName)
        appendField("versionCode", submission.versionCode)
        appendField("platform", "ios")
        appendField("reporterId", submission.reporterId)
        appendField("metadata", submission.metadata)

        for attachment in submission.attachments {
            body.append(Data("--\(boundary)\r\n".utf8))
            body.append(Data(
                "Content-Disposition: form-data; name=\"files\"; filename=\"\(attachment.fileName)\"\r\n".utf8
            ))
            body.append(Data("Content-Type: \(attachment.contentType)\r\n\r\n".utf8))
            body.append(attachment.data)
            body.append(Data("\r\n".utf8))
        }
        body.append(Data("--\(boundary)--\r\n".utf8))
        request.httpBody = body
        return request
    }

    /// A successful upload is only complete once the service returns its report ID.
    static func upload(
        _ submission: BugReportSubmission,
        session: URLSession = .shared
    ) async throws -> String {
        let request = try buildRequest(submission)
        let (data, response) = try await session.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        let body = String(decoding: data.prefix(64 * 1024), as: UTF8.self)

        if !(200...299).contains(status) {
            throw parseServerError(body: body, status: status)
        }
        // A 200 with `ok: false` is still a rejection; the contract allows it.
        if let json = jsonObject(body), (json["ok"] as? Bool) == false {
            throw parseServerError(body: body, status: status)
        }
        guard let reference = reference(from: body) else {
            throw BugReportError.server(
                code: "INVALID_RESPONSE",
                message: "The bug report service did not return a report ID.",
                retryable: false
            )
        }
        return reference
    }

    static func reference(from body: String) -> String? {
        guard let json = jsonObject(body) else { return nil }
        for key in ["id", "reportId", "bugReportId"] {
            if let value = json[key] as? String {
                let normalized = value
                    .components(separatedBy: .whitespacesAndNewlines)
                    .filter { !$0.isEmpty }
                    .joined(separator: " ")
                if !normalized.isEmpty { return String(normalized.prefix(160)) }
            }
        }
        return nil
    }

    /// The contract puts a human-readable reason in `error.message`; show that rather than an
    /// HTTP status. Whitespace is normalised and the message capped so a proxy error page cannot
    /// take over the screen.
    static func parseServerError(body: String, status: Int) -> BugReportError {
        let payload = (jsonObject(body)?["error"] as? [String: Any]) ?? jsonObject(body)
        let rawMessage = payload?["message"] as? String
        let message = rawMessage?
            .components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
            .prefix(320)
            .description
            .nilIfEmpty

        let fallback: String
        switch status {
        case 403: fallback = "Reporting is turned off for this installation."
        case 429: fallback = "Too many reports were sent. Try again later."
        case 400: fallback = "The server rejected this report."
        default: fallback = "Could not send the report (HTTP \(status))."
        }

        return .server(
            code: (payload?["code"] as? String).map { String($0.prefix(80)) },
            message: message ?? fallback,
            retryable: (payload?["retryable"] as? Bool) ?? (status == 429 || status >= 500)
        )
    }

    private static func jsonObject(_ body: String) -> [String: Any]? {
        guard let data = body.data(using: .utf8) else { return nil }
        return (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}

// MARK: - Metadata

enum BugReportMetadata {
    static func build(deck: BugReportPreflightDeck, overridesKnownIssue: Bool) -> String {
        var payload: [String: Any] = [
            "source": "ios-report-a-problem",
            "platform": "ios"
        ]
        var facts: [String: String] = [:]
        for item in deck.items {
            facts[item.label] = item.value
        }
        payload["preflight"] = facts
        if let key = deck.knownIssueKey {
            payload["knownIssueKey"] = key
            payload["knownIssueOverride"] = overridesKnownIssue
        }
        guard let data = try? JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys]) else {
            return "{}"
        }
        return String(decoding: data, as: UTF8.self)
    }
}
