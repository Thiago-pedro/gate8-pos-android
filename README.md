# Gate8 POS — Android (Smart POS)

[![Android Release](https://github.com/Thiago-pedro/gate8-pos-android/actions/workflows/android-release.yml/badge.svg)](https://github.com/Thiago-pedro/gate8-pos-android/actions/workflows/android-release.yml)

App Kotlin para maquininhas **Stone** / Smart POS, integrado à API Gate8 (Lovable + Supabase).

**Contrato API:** https://github.com/Thiago-pedro/qr7-backend/blob/main/docs/LOVABLE-API-POS.md

## Download APK (CI)

A cada push na branch `main`, o GitHub Actions gera um release com o APK **mockDebug**:

https://github.com/Thiago-pedro/gate8-pos-android/releases/latest

Arquivo: `gate8-pos-mock-debug.apk`

## Requisitos

- Android Studio Ladybug+ (ou Koala)
- JDK 17
- Android SDK 34

## Configuração

1. Abra a pasta `gate8-pos-android` no Android Studio.
2. Gere um **device_token** no painel Gate8: **Admin → POS → Maquininhas** (`g8pos_...`).
3. No app, tela **Configuração**:
   - Base URL: `https://gate8.club` (ou `https://qr7.lovable.app`)
   - Token: `g8pos_...`
   - Nome do operador e ID curto (ex. `POS01`)

## Build MOCK (fase atual)

Sem SDK Stone real — simula pagamento e envia venda para a API.

```bash
cd gate8-pos-android
./gradlew :app:assembleMockDebug
```

APK:

```
app/build/outputs/apk/mock/debug/app-mock-debug.apk
```

No Windows (requer JDK 17):

```powershell
.\gradlew.bat :app:assembleMockDebug
```

Ou baixe o APK pronto em [Releases](https://github.com/Thiago-pedro/gate8-pos-android/releases/latest).

## Instalar na maquininha Stone

1. Ative **Depuração USB** no terminal (se aplicável) ou use instalação via MDM/ADB.
2. Conecte a maquina ao PC ou copie o APK via pendrive/cloud interno.
3. ADB:

```bash
adb install -r app/build/outputs/apk/mock/debug/app-mock-debug.apk
```

4. Abra **Gate8 POS**, configure token e teste:
   - **PDV** → carrega catálogo, venda mock, sync `/sales`
   - **Check-in** → informe o `code` (32 hex) do ingresso
   - **Pendentes** → reenvia vendas se a API falhou após pagamento mock

## Endpoints usados (exatos)

| Método | Path |
|--------|------|
| GET | `/api/public/pos/catalog` |
| POST | `/api/public/pos/sales` |
| POST | `/api/public/pos/checkin` |

Header: `Authorization: Bearer {g8pos_token}`

## Ativar SDK Stone real (flavor `stone`)

1. Adicionar repositório PackageCloud Stone em `settings.gradle.kts` (ver [sdkdocs.stone.com.br](https://sdkdocs.stone.com.br)).
2. Dependência `co.stone.posmobile.sdk` no flavor `stone`.
3. Implementar `StonePaymentGateway` em `stone/payment/`.
4. Build:

```bash
./gradlew :app:assembleStoneRelease
```

5. Homologação Stone: APK, fluxos crédito/débito/pix, comprovante, NSU/autorização, tela administrativa.

## Estrutura

Ver `docs/ANDROID-ESTRUTURA-PASTAS.md` no repo qr7-backend.

## Testes unitários

```bash
./gradlew :app:testMockDebugUnitTest
```

## Fale Conosco

suporte@gate8.club (também exibido no app — requisito Stone).
