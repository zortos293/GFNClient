import SwiftUI

/// S10 — Report a problem.
///
/// The order is deliberate: evidence first, then the form. Showing what will be attached before
/// asking for a description does two things — it tells the user they do not need to recall their
/// bitrate, and it surfaces a known-issue match before they spend three minutes writing up
/// something already on the board.
struct BugReportView: View {
    let deck: BugReportPreflightDeck
    let onSubmit: (BugReportDraft) async -> Result<String?, Error>

    @Environment(\.dismiss) private var dismiss
    @Environment(\.openNowAccent) private var accent

    @State private var draft = BugReportDraft()
    @State private var descriptionTouched = false
    @State private var isSubmitting = false
    @State private var isConfirmingSubmission = false
    @State private var failure: String?
    @State private var acceptedReference: String?
    @FocusState private var focusedField: Field?

    private enum Field: Hashable {
        case title
        case description
    }

    var body: some View {
        NavigationStack {
            Group {
                if let acceptedReference {
                    acceptedView(reference: acceptedReference)
                } else {
                    form
                }
            }
            .navigationTitle("Report a Problem")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .disabled(isSubmitting)
                }
            }
        }
        .confirmationDialog(
            "Send report with diagnostics?",
            isPresented: $isConfirmingSubmission,
            titleVisibility: .visible
        ) {
            Button("Send Report") { submitConfirmed() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("OpenNOW will attach a redacted debugging file with recent app, API, queue, and stream logs. Account credentials, email addresses, device IDs, session IDs, and network addresses are removed.")
        }
    }

    // MARK: Form

    private var form: some View {
        Form {
            Section {
                ForEach(deck.items) { item in
                    preflightRow(item)
                }
            } header: {
                Text("We'll attach this")
            } footer: {
                Text("Everything above is collected from this device. No account details, email address or IP address is included.")
            }

            if let knownIssue = deck.knownIssue {
                Section {
                    Toggle("This is different from that", isOn: $draft.overridesKnownIssue)
                } footer: {
                    Text("\(knownIssue.value) is already known and being worked on. Send a report anyway if what you saw does not match it — say how it differs in the description.")
                }
            }

            Section {
                TextField("Title", text: $draft.title)
                    .focused($focusedField, equals: .title)
                    .textInputAutocapitalization(.sentences)
                    .submitLabel(.next)
                    .onSubmit { focusedField = .description }

                ZStack(alignment: .topLeading) {
                    if draft.description.isEmpty {
                        Text("What happened, what you were doing, and what you expected instead.")
                            .foregroundStyle(.tertiary)
                            .padding(.top, 8)
                            .padding(.leading, 5)
                            .allowsHitTesting(false)
                    }
                    TextEditor(text: $draft.description)
                        .frame(minHeight: 140)
                        .focused($focusedField, equals: .description)
                        .scrollContentBackground(.hidden)
                }
                .onChangeCompat(of: focusedField) { field in
                    // Validate after the first blur, not on every keystroke — a message that
                    // appears while you are still typing the first word is just noise.
                    if field != .description, !draft.description.isEmpty { descriptionTouched = true }
                }
            } header: {
                Text("What went wrong")
            } footer: {
                descriptionFooter
            }

            Section {
                Button {
                    requestSubmission()
                } label: {
                    HStack {
                        if isSubmitting { ProgressView().padding(.trailing, 6) }
                        Text(isSubmitting ? "Sending…" : "Send Report")
                    }
                    .frame(maxWidth: .infinity)
                }
                .disabled(isSubmitting || blockingReason != nil)

                if let blockingReason, !isSubmitting {
                    // A disabled button always says why. Guessing is worse than being told.
                    Label(blockingReason, systemImage: "info.circle")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                if let failure {
                    Label(failure, systemImage: "exclamationmark.triangle.fill")
                        .font(.footnote)
                        .foregroundStyle(OpenNOWPalette.statusPoor)
                }
            } footer: {
                Text("Reports are read by the OpenNOW maintainers and may be quoted in a public issue. Don't include anything you would not post publicly.")
                    .foregroundStyle(OpenNOWPalette.statusNotice)
            }
        }
    }

    private func preflightRow(_ item: BugReportPreflightItem) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: OpenNOWSpacing.md) {
            Image(systemName: isKnownIssue(item) ? "exclamationmark.triangle.fill" : "checkmark.circle.fill")
                .font(.caption)
                .foregroundStyle(isKnownIssue(item) ? OpenNOWPalette.statusFair : accent.color)
                .accessibilityHidden(true)
            Text(item.label)
                .foregroundStyle(.secondary)
            Spacer(minLength: OpenNOWSpacing.md)
            Text(item.value)
                .multilineTextAlignment(.trailing)
                .monospacedDigit()
        }
        .font(.footnote)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(item.label): \(item.value)")
    }

    private func isKnownIssue(_ item: BugReportPreflightItem) -> Bool {
        if case .knownIssue = item.kind { return true }
        return false
    }

    @ViewBuilder
    private var descriptionFooter: some View {
        if let progress = BugReportValidation.descriptionProgress(draft.description) {
            Text(progress)
                .foregroundStyle(descriptionTouched && !draft.description.isEmpty
                                 ? OpenNOWPalette.statusFair
                                 : Color.secondary)
                .monospacedDigit()
        } else {
            Label("Looks good", systemImage: "checkmark.circle")
                .foregroundStyle(accent.color)
        }
    }

    /// Nil when the form can be sent. Otherwise the single most useful reason it cannot.
    private var blockingReason: String? {
        if let error = BugReportValidation.titleError(draft.title) { return error }
        if let error = BugReportValidation.descriptionError(draft.description) { return error }
        if let error = BugReportValidation.languageError(title: draft.title, description: draft.description) {
            return error
        }
        return nil
    }

    private func requestSubmission() {
        guard blockingReason == nil else { return }
        focusedField = nil
        isConfirmingSubmission = true
    }

    private func submitConfirmed() {
        guard blockingReason == nil else { return }
        isSubmitting = true
        failure = nil
        focusedField = nil
        Task {
            let result = await onSubmit(draft)
            isSubmitting = false
            switch result {
            case .success(let reference):
                Haptics.success()
                acceptedReference = reference ?? ""
            case .failure(let error):
                Haptics.error()
                failure = error.localizedDescription
            }
        }
    }

    // MARK: Accepted

    private func acceptedView(reference: String) -> some View {
        VStack(spacing: OpenNOWSpacing.lg) {
            Spacer()
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 52))
                .foregroundStyle(accent.color)
                .accessibilityHidden(true)
            Text("Report sent")
                .font(.title2.bold())
            Text(reference.isEmpty
                 ? "Thanks — the maintainers have what they need to look into it."
                 : "Thanks. Your reference is \(reference).")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            if !reference.isEmpty {
                Button {
                    #if canImport(UIKit)
                    UIPasteboard.general.string = reference
                    #endif
                } label: {
                    Label("Copy Reference", systemImage: "doc.on.doc")
                }
                .buttonStyle(.bordered)
            }
            Spacer()
            Button("Done") { dismiss() }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .frame(maxWidth: .infinity)
        }
        .padding(OpenNOWSpacing.xl)
        .accessibilityElement(children: .contain)
    }
}
