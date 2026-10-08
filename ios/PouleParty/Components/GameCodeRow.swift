import SwiftUI

struct GameCodeRow: View {
    let gameCode: String
    @State private var codeCopied = false

    var body: some View {
        HStack {
            Spacer()
            Text(gameCode)
                .font(.gameboy(size: 24))
            Spacer()
            Button {
                UIPasteboard.general.string = gameCode
                withAnimation {
                    codeCopied = true
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
                    withAnimation {
                        codeCopied = false
                    }
                }
            } label: {
                Image(systemName: codeCopied ? "checkmark" : "doc.on.doc")
                    .foregroundStyle(codeCopied ? Color.success : .gray)
                    .contentTransition(.symbolEffect(.replace))
            }
            .accessibilityLabel(codeCopied ? String(localized: "Code copied") : String(localized: "Copy game code"))
            .frame(minWidth: 44, minHeight: 44)
            .contentShape(Rectangle())
            .buttonStyle(.plain)
        }
    }
}
