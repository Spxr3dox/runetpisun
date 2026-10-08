import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var router: LaunchRouter
    @Environment(\.scenePhase) private var scenePhase

    @AppStorage("holdSeconds") private var holdSeconds = 0.5
    @AppStorage("autoOpen") private var autoOpen = false

    @State private var pressing = false
    @State private var showWeb = false
    @State private var showHelp = false
    @State private var didAutoOpen = false

    private let gradient = LinearGradient(
        colors: [Color(red: 0.26, green: 0.52, blue: 0.96), Color(red: 0.61, green: 0.45, blue: 0.80)],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )

    var body: some View {
        NavigationStack {
            VStack(spacing: 28) {
                Spacer()
                holdButton
                Text(pressing ? "Тримай…" : "Затисни, щоб викликати Gemini")
                    .font(.headline)
                    .foregroundStyle(.secondary)
                Text(GeminiLauncher.isAppInstalled
                     ? "Застосунок Gemini знайдено — вхід автоматичний"
                     : "Gemini не встановлено — вхід через Google-акаунт")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                Spacer()
                settings
            }
            .padding()
            .navigationTitle("Gemini Кнопка")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                Button { showHelp = true } label: { Image(systemName: "questionmark.circle") }
            }
            .fullScreenCover(isPresented: $showWeb) { GeminiWebView().ignoresSafeArea() }
            .sheet(isPresented: $showHelp) { HelpView() }
        }
        .onAppear { handlePending() }
        .onChange(of: router.pending) { _, _ in handlePending() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                handlePending()
                if autoOpen, !didAutoOpen {
                    didAutoOpen = true
                    launch()
                }
            } else if phase == .background {
                didAutoOpen = false
            }
        }
    }

    /// Запит від Команд/URL виконуємо лише коли програма на екрані — інакше iOS не відкриє інший застосунок.
    private func handlePending() {
        guard router.pending, scenePhase == .active else { return }
        router.pending = false
        didAutoOpen = true
        launch()
    }

    private var holdButton: some View {
        ZStack {
            Circle()
                .fill(gradient)
                .frame(width: 220, height: 220)
                .shadow(color: .blue.opacity(pressing ? 0.6 : 0.25), radius: pressing ? 30 : 12)
            Image(systemName: "sparkles")
                .font(.system(size: 80, weight: .semibold))
                .foregroundStyle(.white)
            Circle()
                .trim(from: 0, to: pressing ? 1 : 0)
                .stroke(.white.opacity(0.9), style: StrokeStyle(lineWidth: 6, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .frame(width: 236, height: 236)
                .animation(pressing ? .linear(duration: holdSeconds) : .easeOut(duration: 0.15), value: pressing)
        }
        .scaleEffect(pressing ? 0.94 : 1)
        .animation(.spring(duration: 0.25), value: pressing)
        .onLongPressGesture(minimumDuration: holdSeconds, maximumDistance: 40) {
            UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
            launch()
        } onPressingChanged: { isPressing in
            pressing = isPressing
            if isPressing { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        }
        .accessibilityLabel("Викликати Gemini")
        .accessibilityAddTraits(.isButton)
    }

    private var settings: some View {
        VStack(spacing: 12) {
            HStack {
                Text("Утримання")
                Slider(value: $holdSeconds, in: 0.2...1.5, step: 0.1)
                Text(String(format: "%.1f с", holdSeconds)).monospacedDigit().frame(width: 48)
            }
            Toggle("Відкривати Gemini одразу при запуску", isOn: $autoOpen)
        }
        .font(.subheadline)
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16))
    }

    private func launch() {
        Task { @MainActor in
            if !(await GeminiLauncher.openApp()) { showWeb = true }
        }
    }
}

private struct HelpView: View {
    var body: some View {
        NavigationStack {
            List {
                Section("Виклик без відкриття програми (як кнопка на Samsung)") {
                    Text("1. Команди → «+» → Додати дію → «Gemini Кнопка» → «Викликати Gemini». Збережи команду.")
                    Text("2. Параметри → Доступність → Дотик → Торкання ззаду → Двічі (або Тричі) → вибери цю команду.")
                    Text("Тепер подвійний тап по спинці iPhone викликає Gemini.")
                }
                Section("Інші варіанти") {
                    Text("Siri: «Open Gemini with Gemini Кнопка».")
                    Text("iOS 18: команду можна додати в Пункт керування або на екран блокування.")
                }
                Section("Чому не бокова кнопка") {
                    Text("iOS не дозволяє стороннім програмам змінювати дію бокової кнопки — довге натискання завжди викликає Siri. На iPhone 15 (не Pro) немає Action Button, тому найближчий аналог — Торкання ззаду.")
                }
            }
            .navigationTitle("Як налаштувати")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
