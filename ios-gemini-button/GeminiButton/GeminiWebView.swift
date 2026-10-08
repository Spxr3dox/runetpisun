import SafariServices
import SwiftUI

/// Веб-Gemini з входом через Google-акаунт. SFSafariViewController (а не WKWebView),
/// бо Google блокує вхід у вбудованих WebView. Сесія зберігається між запусками.
struct GeminiWebView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> SFSafariViewController {
        let config = SFSafariViewController.Configuration()
        config.barCollapsingEnabled = true
        let vc = SFSafariViewController(url: GeminiLauncher.webURL, configuration: config)
        vc.dismissButtonStyle = .close
        return vc
    }

    func updateUIViewController(_ vc: SFSafariViewController, context: Context) {}
}
