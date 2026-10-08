import SwiftUI

/// A failed load with its reason and a retry, instead of an empty state.
struct LoadErrorBanner: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(Color.errorText)
                .accessibilityHidden(true)
            Text(message)
                .font(.body)
                .foregroundStyle(Color.errorText)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button("Try again", action: onRetry)
                .font(.body.weight(.semibold))
                .frame(minHeight: 44)
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color.surface))
        .accessibilityElement(children: .combine)
    }
}
