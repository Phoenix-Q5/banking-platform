import SwiftUI

struct MainTabView: View {
    @EnvironmentObject private var session: SessionStore

    var body: some View {
        TabView {
            if session.isCustomer {
                HomeView()
                    .tabItem { Label("Home", systemImage: "house.fill") }
                TransfersView()
                    .tabItem { Label("Move", systemImage: "arrow.left.arrow.right") }
                PaymentsView()
                    .tabItem { Label("Pay", systemImage: "dollarsign.circle") }
                MoreView()
                    .tabItem { Label("More", systemImage: "ellipsis.circle") }
            }
            AlertsView()
                .tabItem { Label("Alerts", systemImage: "bell.fill") }
            if session.isAdmin || session.isSupport {
                AdminHomeView()
                    .tabItem { Label("Ops", systemImage: "wrench.and.screwdriver") }
            }
        }
    }
}
