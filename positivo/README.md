# Positivo L3 / L4 — assinatura debug

Terminais **Positivo L3** (homologação Gate8) exigem APK assinado com a JKS da Positivo. Processo oficial Stone: [Assinatura Debug Positivo](https://drive.google.com/drive/folders/1Roxc3NsYmcT2I-ne3zTCJRScvkY048Yz?usp=sharing).

## Setup (uma vez)

1. Baixe `positivo-keystore.jks` no link acima (Stone Partner Community).
2. Coloque o `.jks` nesta pasta (`positivo/`).
3. Copie `positivo-keystore.properties.example` → `positivo-keystore.properties` e preencha as senhas.
4. Sincronize o Gradle — o projeto aplica `positivo-signing-config.gradle` automaticamente.

Não commitar `.jks` nem `positivo-keystore.properties`.

## Build para instalar no L3

Use o build type **`positivo`** (não `debug` comum):

```powershell
.\gradlew.bat :app:assembleStonePositivoSeriesLPositivo
```

APK:

```
app/build/outputs/apk/stone/positivoSeriesL/positivo/app-stone-positivoSeriesL-positivo.apk
```

No Android Studio: Build Variants → **stonePositivoSeriesLPositivo**.

> Release usa JKS própria (não a debug Positivo). O build type `positivo` é só para homologação em terminais de debug.
