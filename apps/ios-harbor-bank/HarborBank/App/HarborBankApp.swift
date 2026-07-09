import SwiftUI

@main
struct HarborBankApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var session = SessionStore()
    @StateObject private var push = PushRegistrationService.shared

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(session)
                .environmentObject(push)
                .tint(HarborTheme.sea)
                .onAppear {
                    push.attach(session: session)
                }
        }
    }
}
