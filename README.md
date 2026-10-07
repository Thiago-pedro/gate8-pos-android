# Gate8 POS — Android (Cielo Smart)

App Kotlin para maquininhas **Cielo Smart**, integrado à API Gate8.

**Contrato API:** https://github.com/Thiago-pedro/qr7-backend/blob/main/docs/LOVABLE-API-POS.md

## Download APK (CI)

A cada push na branch `main`, o GitHub Actions gera um release com o APK **cieloDebug**:

https://github.com/Thiago-pedro/gate8-pos-android/releases/latest

## Build

```powershell
cd gate8-pos-android
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleCieloDebug
```

APK:

```
app/build/outputs/apk/cielo/debug/app-cielo-debug.apk
```

Credenciais Cielo (`CIELO_CLIENT_ID`, `CIELO_ACCESS_TOKEN`, `CIELO_MERCHANT_ID`) vêm de `local.properties` ou `local.properties.cielo.txt` na raiz do projeto.

## Configuração no app

1. Gere um **device_token** no painel Gate8: **Admin → POS → Maquininhas** (`g8pos_...`).
2. Faça login com o token de 6 caracteres do produtor.
3. Em **Configurações**, informe o operador.

O pagamento de cartão e Pix abre o app da Cielo via deep link (`lio://payment`). As vias do comprovante da adquirente ficam com a Cielo.

## Suporte

suporte@gate8.club
