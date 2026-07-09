import SwiftUI

struct AlertsView: View {
    @EnvironmentObject private var session: SessionStore
    @EnvironmentObject private var push: PushRegistrationService
    @State private var bankingAlerts: [AppNotification] = []
    @State private var serviceAlerts: [AppNotification] = []
    @State private var incidents: [ServiceIncident] = []
    @State private var error: String?
    @State private var tab = 0

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if session.isAdmin || session.isSupport {
                    Picker("Inbox", selection: $tab) {
                        if session.isCustomer { Text("Banking").tag(0) }
                        Text("Service").tag(1)
                    }
                    .pickerStyle(.segmented)
                    .padding()
                }

                List {
                    Section {
                        Text(push.statusMessage)
                            .font(.caption)
                            .foregroundStyle(HarborTheme.muted)
                    }

                    if showBanking {
                        Section("Transaction & banking alerts") {
                            if bankingAlerts.isEmpty {
                                Text("No banking alerts yet. Complete a transfer to generate one.")
                                    .foregroundStyle(.secondary)
                            }
                            ForEach(bankingAlerts) { n in
                                alertRow(n)
                            }
                        }
                    }

                    if showService {
                        Section("Service / ops alerts") {
                            if serviceAlerts.isEmpty && incidents.isEmpty {
                                Text("No service alerts. Trigger an ops-agent investigation or stop a service to fire Alertmanager rules.")
                                    .foregroundStyle(.secondary)
                            }
                            ForEach(serviceAlerts) { n in
                                alertRow(n)
                            }
                            ForEach(incidents) { i in
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack {
                                        Text(i.title).fontWeight(.semibold)
                                        Spacer()
                                        Text(i.severity).font(.caption2).padding(4)
                                            .background(severityColor(i.severity).opacity(0.15))
                                            .clipShape(RoundedRectangle(cornerRadius: 3))
                                    }
                                    Text(i.summary ?? i.rootCauseHypothesis ?? "")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                    Text("\(i.affectedService ?? "platform") · \(i.status)")
                                        .font(.caption2)
                                        .foregroundStyle(HarborTheme.muted)
                                }
                                .padding(.vertical, 2)
                            }
                        }
                    }

                    if let error {
                        Section { Text(error).foregroundStyle(.red) }
                    }
                }
                .listStyle(.insetGrouped)
            }
            .background(HarborTheme.paper.ignoresSafeArea())
            .navigationTitle("Alerts")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Refresh") { Task { await load() } }
                }
            }
            .task { await load() }
            .refreshable { await load() }
        }
    }

    private var showBanking: Bool {
        session.isCustomer && (!(session.isAdmin || session.isSupport) || tab == 0)
    }

    private var showService: Bool {
        (session.isAdmin || session.isSupport) && (!session.isCustomer || tab == 1)
    }

    @ViewBuilder
    private func alertRow(_ n: AppNotification) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(n.title).fontWeight(.semibold)
                Spacer()
                Text(n.channel).font(.caption2).foregroundStyle(HarborTheme.muted)
            }
            Text(n.body).font(.caption).foregroundStyle(.secondary)
            HStack {
                Text(n.category).font(.caption2)
                if let eventType = n.eventType {
                    Text(eventType).font(.caption2).foregroundStyle(HarborTheme.muted)
                }
                Spacer()
                if n.status != "READ" {
                    Button("Mark read") {
                        Task { await markRead(n) }
                    }.font(.caption)
                }
            }
        }
    }

    private func severityColor(_ s: String) -> Color {
        switch s.uppercased() {
        case "CRITICAL": return .red
        case "WARNING": return .orange
        default: return HarborTheme.sea
        }
    }

    private func load() async {
        guard let token = session.accessToken else { return }
        error = nil
        do {
            if let customerId = session.customerId {
                bankingAlerts = try await HarborAPI.notifications(token: token, customerId: customerId)
            }
            if session.isAdmin || session.isSupport {
                serviceAlerts = try await HarborAPI.serviceNotifications(token: token)
                incidents = (try? await HarborAPI.incidents(token: token)) ?? []
            }
            await push.syncIfPossible()
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func markRead(_ n: AppNotification) async {
        guard let token = session.accessToken else { return }
        _ = try? await HarborAPI.markRead(token: token, id: n.id)
        await load()
    }
}
