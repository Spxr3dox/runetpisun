import AppIntents

/// Дія для Команд: її можна повісити на Back Tap (подвійний/потрійний тап по спинці)
/// або на Action Button / Пункт керування.
struct OpenGeminiIntent: AppIntent {
    static var title: LocalizedStringResource = "Викликати Gemini"
    static var description = IntentDescription("Відкриває Gemini: застосунок, якщо встановлений, інакше вхід через Google.")
    static var openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        LaunchRouter.shared.requestLaunch()
        return .result()
    }
}

struct GeminiShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: OpenGeminiIntent(),
            phrases: [
                "Open Gemini with \(.applicationName)",
                "Виклич Gemini у \(.applicationName)"
            ],
            shortTitle: "Gemini",
            systemImageName: "sparkles"
        )
    }
}
