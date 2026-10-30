# Backend imajı: Railway test ortamı ve Türkiye'deki VDS aynı imajı çalıştırır (ADR 0009).
FROM eclipse-temurin:21-jdk AS derleme
WORKDIR /kaynak
COPY . .
# Mobil modüller imajda derlenmez; yalnızca server ve ortak model.
ENV SOSYALOYUN_SADECE_SERVER=true
RUN ./gradlew :server:installDist --no-daemon --console=plain

FROM eclipse-temurin:21-jre
WORKDIR /uygulama
COPY --from=derleme /kaynak/server/build/install/server/ ./
EXPOSE 8080
CMD ["./bin/server"]
