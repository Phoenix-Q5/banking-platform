import SwiftUI

struct LoginView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var username = "demo.customer"
    @State private var password = "password"
    @State private var busy = false

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [HarborTheme.paper, Color.white, HarborTheme.paper],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ).ignoresSafeArea()

            VStack(spacing: 28) {
                VStack(spacing: 8) {
                    Text("Harbor Bank")
                        .font(.system(size: 40, weight: .bold, design: .serif))
                        .foregroundStyle(HarborTheme.navy)
                    Text("Mobile banking for real operations")
                        .foregroundStyle(HarborTheme.muted)
                        .multilineTextAlignment(.center)
                }
                .padding(.top, 48)

                VStack(spacing: 14) {
                    TextField("Username", text: $username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .padding()
                        .background(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                    SecureField("Password", text: $password)
                        .padding()
                        .background(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 4))

                    Button {
                        Task {
                            busy = true
                            await session.login(username: username, password: password)
                            busy = false
                        }
                    } label: {
                        HStack {
                            if busy { ProgressView().tint(.white) }
                            Text(busy ? "Signing in…" : "Sign in")
                                .fontWeight(.semibold)
                        }
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(HarborTheme.sea)
                        .foregroundStyle(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                    }
                    .disabled(busy)

                    HStack {
                        demoButton("Customer", "demo.customer")
                        demoButton("Admin", "demo.admin")
                        demoButton("Support", "demo.support")
                    }
                }
                .padding(20)
                .background(.white.opacity(0.9))
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .shadow(color: .black.opacity(0.06), radius: 18, y: 8)
                .padding(.horizontal)

                if let err = session.lastError {
                    Text(err).foregroundStyle(.red).font(.footnote).padding(.horizontal)
                }

                Spacer()
            }
        }
    }

    private func demoButton(_ title: String, _ user: String) -> some View {
        Button(title) {
            username = user
            password = "password"
        }
        .font(.caption.weight(.semibold))
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(HarborTheme.paper)
        .clipShape(RoundedRectangle(cornerRadius: 4))
    }
}
