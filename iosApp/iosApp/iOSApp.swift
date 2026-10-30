import SwiftUI
import GoogleSignIn

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // Google giriş sayfası Safari'den uygulamaya ters istemci kimliği şemasıyla döner.
                .onOpenURL { url in GIDSignIn.sharedInstance.handle(url) }
        }
    }
}
