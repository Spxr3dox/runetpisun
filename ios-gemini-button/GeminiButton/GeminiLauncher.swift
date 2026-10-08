import UIKit

enum GeminiLauncher {
    static let appURL = URL(string: "googlegemini://")!
    static let webURL = URL(string: "https://gemini.google.com/app")!

    /// Відкриває застосунок Gemini (у ньому вже є вхід у Google-акаунт).
    /// Повертає false, якщо застосунок не встановлено — тоді потрібна веб-версія з входом через Google.
    @MainActor
    static func openApp() async -> Bool {
        let app = UIApplication.shared
        if app.canOpenURL(appURL), await app.open(appURL) { return true }
        // Universal link: відкриється застосунок Gemini, якщо він обслуговує домен.
        return await app.open(webURL, options: [.universalLinksOnly: true])
    }

    static var isAppInstalled: Bool {
        UIApplication.shared.canOpenURL(appURL)
    }
}
