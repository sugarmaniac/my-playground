import UIKit
import GoogleSignIn
import ComposeApp

/// Kotlin'deki `IosGoogleGirisi`nin GoogleSignIn SDK'sıyla uygulaması (#32).
/// iOS istemci kimliği Info.plist'teki `GIDClientID`'den gelir; o da `Config.xcconfig`'teki `GOOGLE_IOS_ISTEMCI_KIMLIGI`'nden.
final class GoogleSignInGirisi: IosGoogleGirisi {
    private let istemciKimligi: String? = {
        guard let deger = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String, !deger.isEmpty else { return nil }
        return deger
    }()

    var kullanilabilir: Bool { istemciKimligi != nil }

    func girisYap(sunucuIstemciKimligi: String, sonuc: IosGoogleGirisSonucu) {
        guard let istemciKimligi, let sunan = GoogleSignInGirisi.enUstteki() else {
            sonuc.hata()
            return
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(
            clientID: istemciKimligi,
            serverClientID: sunucuIstemciKimligi.isEmpty ? nil : sunucuIstemciKimligi
        )
        GIDSignIn.sharedInstance.signIn(withPresenting: sunan) { cikti, hata in
            if let hata = hata as NSError? {
                if hata.domain == kGIDSignInErrorDomain && hata.code == GIDSignInError.canceled.rawValue {
                    sonuc.iptal()
                } else {
                    sonuc.hata()
                }
                return
            }
            guard let token = cikti?.user.idToken?.tokenString else {
                sonuc.hata()
                return
            }
            sonuc.token(deger: token)
        }
    }

    /// Google sayfasını sunacak, o an en üstte görünen ekran.
    private static func enUstteki() -> UIViewController? {
        let pencere = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first
        var ekran = pencere?.rootViewController
        while let sunulan = ekran?.presentedViewController {
            ekran = sunulan
        }
        return ekran
    }
}
