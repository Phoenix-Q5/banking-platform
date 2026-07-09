import SwiftUI

struct MoreView: View {
    @EnvironmentObject private var session: SessionStore

    var body: some View {
        NavigationStack {
            List {
                NavigationLink("Cards") { CardsView() }
                NavigationLink("Loans") { LoansView() }
                Section("Profile") {
                    LabeledContent("User", value: session.username)
                    LabeledContent("Roles", value: session.roles.joined(separator: ", "))
                    if let id = session.customerId {
                        LabeledContent("Customer", value: String(id.prefix(8)) + "…")
                    }
                }
                Section {
                    Button("Sign out", role: .destructive) { session.logout() }
                }
            }
            .navigationTitle("More")
        }
    }
}
