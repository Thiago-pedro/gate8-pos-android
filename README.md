# Gate8 POS — Android (Smart POS)

[![Android Release](https://github.com/Thiago-pedro/gate8-pos-android/actions/workflows/android-release.yml/badge.svg)](https://github.com/Thiago-pedro/gate8-pos-android/actions/workflows/android-release.yml)

App Kotlin para maquininhas **Mercado Pago Point**, integrado à API Gate8 (Lovable + Supabase).

**Contrato API:** https://github.com/Thiago-pedro/qr7-backend/blob/main/docs/LOVABLE-API-POS.md

## Download APK (CI)

A cada push na branch `main`, o GitHub Actions gera um release com o APK **mockDebug**:

https://github.com/Thiago-pedro/gate8-pos-android/releases/latest

## Flavors

| Flavor | Uso |
|--------|-----|
| **mock** | Desenvolvimento — simula pagamento e envia venda para a API |
| **mercadopago** | Produção — integração Mercado Pago Point (API de Orders) |

## Build

```powershell
cd gate8-pos-android
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleMockDebug
.\gradlew.bat :app:assembleMercadopagoDebug
```

APKs:

```
app/build/outputs/apk/mock/debug/app-mock-debug.apk
app/build/outputs/apk/mercadopago/debug/app-mercadopago-debug.apk
```

## Configuração no app

1. Gere um **device_token** no painel Gate8: **Admin → POS → Maquininhas** (`g8pos_...`).
2. Faça login com o token de 6 caracteres do produtor.
3. Em **Configurações**, informe o operador e (flavor `mercadopago`) o **Terminal ID** do Point em modo PDV.

## Mercado Pago Point

Documentação da integração: `docs/MERCADOPAGO-POINT.md` (repo `qr7-backend`).

Fluxo previsto:

1. App cria order via backend Gate8 → API Mercado Pago (`POST /v1/orders`).
2. Terminal Point recebe a order e processa o pagamento.
3. App confirma e registra a venda em `/api/public/pos/sales`.

## Suporte

suporte@gate8.club
