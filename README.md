# Gate8 POS — Android (Smart POS)

[![Android Release](https://github.com/Thiago-pedro/gate8-pos-android/actions/workflows/android-release.yml/badge.svg)](https://github.com/Thiago-pedro/gate8-pos-android/actions/workflows/android-release.yml)

App Kotlin para maquininhas **Stone** / Smart POS, integrado à API Gate8 (Lovable + Supabase).

**Contrato API:** https://github.com/Thiago-pedro/qr7-backend/blob/main/docs/LOVABLE-API-POS.md

## Download APK (CI)

A cada push na branch `main`, o GitHub Actions gera um release com o APK **mockGenericDebug**:

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
./gradlew :app:assembleMockGenericDebug
```

APK:

```
app/build/outputs/apk/mock/generic/debug/app-mock-generic-debug.apk
```

No Windows (requer JDK 17):

```powershell
.\gradlew.bat :app:assembleMockGenericDebug
```

Ou baixe o APK pronto em [Releases](https://github.com/Thiago-pedro/gate8-pos-android/releases/latest).

## Instalar na maquininha Stone

### Terminal debug vs produtivo

| Tipo | Identificação | Instalar APK dev |
|------|---------------|------------------|
| **Debug** | Marca d'água `debug`, `mockup` ou `not commercial use` | ✅ USB + ADB / Android Studio |
| **Produtivo** | Loja real, sem marca d'água | ❌ só app homologado via **Partner Hub** |

Homologação SDK Stone exige terminal **debug**. Maquininhas de operação (ex. loja com CNPJ ativo) não aceitam `adb install`.

### Passos (terminal debug)

1. Ative **Depuração USB** (Configurações → Sobre → toque 7× em **Versão do software** → Opções do desenvolvedor).
2. Conecte ao PC; no Android Studio use o variant correto (**`stoneSunmiDebug`** para P2, **`stonePositivoSeriesLPositivo`** para L3).
3. Após mudanças no Gradle: **Sync Project with Gradle Files** (evita erro `Cannot locate tasks assembleMock...`).
4. ADB (exemplo P2):

```bash
adb install -r app/build/outputs/apk/stone/sunmi/debug/app-stone-sunmi-debug.apk
```

5. Abra **Gate8 POS**, configure token `g8pos_...` e StoneCode de testes em Configurações.

### Emulador (sem Stone hardware)

Variant **`mockGenericDebug`** — simula pagamento; testa UI e API Gate8.

## Endpoints usados (exatos)

| Método | Path |
|--------|------|
| GET | `/api/public/pos/catalog` |
| POST | `/api/public/pos/sales` |
| POST | `/api/public/pos/checkin` |

Header: `Authorization: Bearer {g8pos_token}`

## Ativar SDK Stone real (flavor `stone`)

Terminais de homologação Gate8: **Positivo L3** e **Sunmi P2**.

| Terminal | Product flavor `model` | Variant para instalar na maquininha |
|----------|------------------------|-------------------------------------|
| Positivo Série L (L300 / **L400**) | `positivoSeriesL` | `stonePositivoSeriesLPositivo` |
| Sunmi P2 | `sunmi` | `stoneSunmiDebug` |

> A Série L (L300 = L3, L400 = L4) usa o mesmo flavor e a mesma dependência `stone-sdk-posandroid-positivo`. O build type `positivo` assina com a JKS platform da Positivo (`positivo/`), obrigatória nos terminais de debug.

1. Token PackageCloud em `local.properties` (`packageCloudReadToken`) — Stone Partner Community.
2. **Positivo L3:** JKS em `positivo/` (ver `positivo/README.md` e [Assinatura Debug Positivo](https://drive.google.com/drive/folders/1Roxc3NsYmcT2I-ne3zTCJRScvkY048Yz?usp=sharing)).
3. Build na maquininha correspondente:

```bash
./gradlew :app:assembleStonePositivoSeriesLPositivo   # L3 (build type positivo + JKS)
./gradlew :app:assembleStoneSunmiDebug                 # P2
```

4. Homologação Stone: fluxos crédito/débito/pix, comprovante, NSU/autorização, tela administrativa.

### Ambiente Sandbox (debug)

- **Debug** → SDK usa **Sandbox** automaticamente.
- **Release** → Produção.
- Só em **debug**: dependência `envconfig` permite trocar o ambiente via ADB (não usar em release).

Trocar ambiente manualmente na maquininha (APK debug):

```bash
adb shell "am start -n br.com.gate8.pos.terminal.debug/br.com.stone.sdk.android.envconfig.data.EnvironmentActivity"
```

#### Como o sandbox simula retornos

O **centavo** do valor define o Action Code (qualquer bandeira). Exemplos:

| Valor | Resultado |
|-------|-----------|
| R$ 1,00 | Aprovado (`0000`) |
| R$ 1,01 | Recusado (`1007` — verifique dados do cartão) |
| R$ 1,51 | Não autorizada (`1016`) |
| R$ 1,55 | Senha inválida (`1017`) |
| R$ 2,00 | Aprovado (`0000`) |
| R$ 666,00 | **Timeout** |

**Aprovadas no sandbox:** qualquer valor **inteiro** entre **R$ 1,00** e **R$ 100,00** (R$ 5,00, R$ 10,00, R$ 50,00…).

**Testes Gate8 sugeridos no L3:**

1. Venda bilheteria **R$ 10,00** crédito → deve aprovar e sync na API.
2. Venda **R$ 10,01** → deve recusar e **não** gravar venda no servidor.
3. Estorno da última venda aprovada → `CancellationProvider`.
4. PIX (com credenciais sandbox) + [App Teste PIX](https://drive.google.com/file/d/1maLmiyuDy2Pk4TWmfp6MK7ufZZiNQYfz/view).

Tabela completa: [Retorno do Sandbox](https://sdkandroid.stone.com.br/reference/retorno-sandbox).

## Estrutura

Ver `docs/ANDROID-ESTRUTURA-PASTAS.md` no repo qr7-backend.

## Testes unitários

```bash
./gradlew :app:testMockGenericDebugUnitTest
```

## Fale Conosco

suporte@gate8.club (também exibido no app — requisito Stone).
