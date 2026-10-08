import SwiftUI

@main
struct GeminiButtonApp: App {
    @StateObject private var router = LaunchRouter.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(router)
                // geminibutton:// — виклик з Команд / Back Tap / віджета
                .onOpenURL { _ in router.requestLaunch() }
        }
    }
}

/// Черга запитів «відкрий Gemini», що приходять ззовні (App Intent, URL).
@MainActor
final class LaunchRouter: ObservableObject {
    static let shared = LaunchRouter()
    @Published var pending = false

    func requestLaunch() { pending = true }
}
