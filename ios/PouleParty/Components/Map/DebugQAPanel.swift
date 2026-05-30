import SwiftUI

/// QA-only floating panel, rendered only on debug games (`Game.isDebugGame`,
/// created via the `qa_debug_code` long-press). Drives the game through its
/// whole lifecycle without waiting on the clock: "Next" advances one step
/// (ready-to-launch → launch → shrink + power-up spawn → … → game over),
/// "End" terminates immediately. Both route through the `debugAdvanceGame`
/// callable, which itself refuses any game where `isDebugGame != true`, so
/// the panel can never affect a real game even if its visibility gate
/// regressed.
struct DebugQAPanel: View {
    let onNextStep: () -> Void
    let onEndNow: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("QA DEBUG")
                .font(.system(size: 11, weight: .heavy, design: .monospaced))
                .foregroundStyle(.white)
            HStack(spacing: 8) {
                Button(action: onNextStep) {
                    Label("Next", systemImage: "forward.fill")
                        .font(.system(size: 12, weight: .bold))
                }
                Button(action: onEndNow) {
                    Label("End", systemImage: "flag.checkered")
                        .font(.system(size: 12, weight: .bold))
                }
            }
            .buttonStyle(.borderedProminent)
            .tint(.purple)
        }
        .padding(10)
        .background(.black.opacity(0.55))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(.purple.opacity(0.8), lineWidth: 1.5)
        )
        .accessibilityElement(children: .contain)
        .accessibilityLabel("QA debug panel")
    }
}
