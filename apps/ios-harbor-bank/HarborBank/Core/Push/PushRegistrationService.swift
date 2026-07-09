import Foundation
import UIKit
import UserNotifications
import Combine

@MainActor
final class PushRegistrationService: ObservableObject {
    static let shared = PushRegistrationService()

    @Published private(set) var deviceToken: String?
    @Published private(set) var statusMessage: String = "Push not registered"

    private weak var session: SessionStore?
    private let defaultsKey = "harbor.ios.deviceToken"

    func attach(session: SessionStore) {
        self.session = session
        if let existing = UserDefaults.standard.string(forKey: defaultsKey) {
            deviceToken = existing
        }
    }

    func requestAuthorization() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            DispatchQueue.main.async {
                if granted {
                    UIApplication.shared.registerForRemoteNotifications()
                    self.statusMessage = "Notifications authorized"
                } else {
                    self.statusMessage = "Notifications denied — using local device token"
                    self.ensureFallbackToken()
                }
            }
        }
    }

    func didRegister(deviceToken data: Data) {
        let token = data.map { String(format: "%02.2hhx", $0) }.joined()
        self.deviceToken = token
        UserDefaults.standard.set(token, forKey: defaultsKey)
        statusMessage = "APNs token registered"
        Task { await syncIfPossible() }
    }

    func didFailRegistration(error: Error) {
        statusMessage = "APNs unavailable (\(error.localizedDescription)) — using simulator token"
        ensureFallbackToken()
        Task { await syncIfPossible() }
    }

    private func ensureFallbackToken() {
        if deviceToken == nil {
            let token = "ios-sim-\(UUID().uuidString)"
            deviceToken = token
            UserDefaults.standard.set(token, forKey: defaultsKey)
        }
    }

    func syncIfPossible() async {
        guard let session, session.isAuthenticated, let token = session.accessToken, let deviceToken else { return }

        // Customers register under their customer id; admins/support register under the service-alert audience
        // so they receive ops-agent service alerts on the same PUSH channel.
        var audienceIds: [String] = []
        if let customerId = session.customerId {
            audienceIds.append(customerId)
        }
        if session.isAdmin || session.isSupport {
            audienceIds.append(HarborAPI.serviceAlertAudienceId)
        }
        for audience in Set(audienceIds) {
            do {
                _ = try await HarborAPI.registerDevice(
                    token: token,
                    body: .init(customerId: audience, platform: "IOS", token: "\(deviceToken)-\(audience.prefix(8))")
                )
                statusMessage = "Device synced for alerts"
            } catch {
                statusMessage = "Device sync failed: \(error.localizedDescription)"
            }
        }
    }
}
