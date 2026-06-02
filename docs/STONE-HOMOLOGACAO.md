# Homologação Stone — Gate8 POS

A Stone homologa o **APK**, não a API Gate8.

## Checklist

- [ ] SDK oficial Android (`co.stone.posmobile.sdk`)
- [ ] Venda crédito à vista, parcelado, débito, Pix
- [ ] Cancelamento / estorno conforme manual
- [ ] Comprovante via cliente e via estabelecimento
- [ ] NSU + autorização persistidos localmente
- [ ] Logs de transação para auditoria
- [ ] Tela administrativa: cancelar última venda, reimpressão, config terminal
- [ ] packageName `br.com.gate8.pos.terminal` (ajustar com parceiro Stone)
- [ ] minSdk 23, APK < 70MB
- [ ] Seção **Fale Conosco** no app
- [ ] HTTPS para `gate8.club` na whitelist

## Após homologação

Registrar venda no Gate8 via `POST /api/public/pos/sales` com dados Stone reais.
