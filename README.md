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

Fluxo:

1. App cria order via `POST /api/public/pos/payments/mp/orders` (proxy Gate8 → MP).
2. Terminal Point recebe a cobrança (em PDV, abra **Inserir valor** se não aparecer sozinha).
3. App faz polling em `GET /payments/mp/orders/{id}` até pagamento aprovado.
4. App registra a venda em `POST /api/public/pos/sales` com `acquirer`.

Webhook MP (configurar no painel Developers): `https://gate8.club/api/public/webhooks/mercadopago/point`

## Suporte

suporte@gate8.club
