package br.com.gate8.pos.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.data.remote.dto.CashierStatusDto
import br.com.gate8.pos.data.remote.dto.ReportsSummaryDto
import br.com.gate8.pos.ui.common.Gate8BackTopBar
import br.com.gate8.pos.ui.common.Gate8MenuButton
import br.com.gate8.pos.ui.common.Gate8ScreenBackground
import br.com.gate8.pos.ui.theme.Gate8Colors
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    vm: ReportsViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onScreenVisible()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Gate8ScreenBackground {
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Gate8BackTopBar(onBack = onBack)

        Spacer(Modifier.height(16.dp))

        Text(
            "Relatórios",
            color = Gate8Colors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            state.periodLabel,
            color = Gate8Colors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        SectionLabel("Período")
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReportPeriod.entries.forEach { period ->
                PeriodChip(
                    label = period.label,
                    selected = state.period == period,
                    onClick = { vm.selectPeriod(period) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        SectionLabel("Categoria", centered = true)
        Spacer(Modifier.height(10.dp))
        SegmentTabs(
            selected = state.segment,
            onSelect = vm::selectSegment,
        )

        Spacer(Modifier.height(18.dp))

        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(32.dp),
                color = Gate8Colors.AccentBlue,
            )
        } else {
            state.error?.let {
                Text(it, color = Color(0xFFB3261E), fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
            }
            if (state.isDemoData) {
                Text(
                    "Dados de demonstração — aguardando endpoint na API",
                    color = Color(0xFF8A6D00),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            state.data?.let { data ->
                SummaryCard(data)
                Spacer(Modifier.height(12.dp))
                PaymentBreakdownCard(data)
                Spacer(Modifier.height(12.dp))
                BrandBreakdownCard(data)
                Spacer(Modifier.height(12.dp))
                TopItemsCard(data)
            }
            state.cashier?.let { cashier ->
                Spacer(Modifier.height(12.dp))
                CashierStatusCard(cashier)
            }
        }

        Spacer(Modifier.height(20.dp))

        Gate8MenuButton(
            title = "Imprimir relatório",
            subtitle = "Cupom na impressora da maquininha",
            onClick = vm::printReport,
            enabled = !state.loading && state.data != null,
            centerText = true,
        )
        state.printMessage?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                it,
                color = Color(0xFF1B7A3D),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (state.showCustomDialog) {
        CustomPeriodDialog(
            from = state.customFrom,
            to = state.customTo,
            onDismiss = vm::dismissCustomDialog,
            onApply = { from, to ->
                vm.updateCustomFrom(from)
                vm.updateCustomTo(to)
                vm.applyCustomPeriod()
            },
        )
    }
    }
    }
}

@Composable
private fun SectionLabel(text: String, centered: Boolean = false) {
    Text(
        text,
        color = Gate8Colors.TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SegmentTabs(
    selected: ReportSegment,
    onSelect: (ReportSegment) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReportSegment.entries.forEachIndexed { index, segment ->
            if (index > 0) {
                Text(
                    "|",
                    color = Gate8Colors.TextSecondary.copy(alpha = 0.4f),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
            val isSelected = segment == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(segment) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    segment.label,
                    color = if (isSelected) Gate8Colors.AccentBlue else Gate8Colors.TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .height(2.5.dp)
                        .width(if (isSelected) 22.dp else 0.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) Gate8Colors.AccentBlue else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun PeriodChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) Gate8Colors.AccentBlue else Gate8Colors.CardSurface
    val textColor = if (selected) Color.White else Gate8Colors.TextPrimary
    Text(
        label,
        color = textColor,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

@Composable
private fun SummaryCard(data: ReportsSummaryDto) {
    val s = data.summary
    ReportPanel(title = "Resumo geral") {
        StatRow("Vendas", s.saleCount.toString())
        StatRow("Estornos", s.voidCount.toString())
        StatRow("Total bruto", money(s.grossTotal))
        StatRow("Estornos (R$)", money(s.voidTotal))
        StatRow("Total líquido", money(s.netTotal), highlight = true)
        StatRow("Ticket médio", money(s.averageTicket))
    }
}

@Composable
private fun PaymentBreakdownCard(data: ReportsSummaryDto) {
    ReportPanel(title = "Por forma de pagamento") {
        if (data.byPaymentMethod.isEmpty()) {
            Text(
                "Nenhuma venda no período",
                color = Gate8Colors.TextOnLight.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        } else {
            data.byPaymentMethod.forEach { row ->
                BreakdownRow(row.label, row.count, row.total)
            }
        }
    }
}

@Composable
private fun BrandBreakdownCard(data: ReportsSummaryDto) {
    val hasCardSales = data.byPaymentMethod.any { row ->
        row.method.equals("credit", ignoreCase = true) ||
            row.method.equals("debit", ignoreCase = true)
    }
    ReportPanel(title = "Por bandeira (cartão)") {
        when {
            data.byBrand.isNotEmpty() -> data.byBrand.forEach { row ->
                BreakdownRow(row.brand, row.count, row.total)
            }
            hasCardSales -> Text(
                "Há vendas em crédito/débito, mas a Cielo não enviou a bandeira (Visa, Mastercard…). " +
                    "Elas aparecem só em “Por forma de pagamento”.",
                color = Gate8Colors.TextOnLight.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
            else -> Text(
                "Sem vendas em cartão no período",
                color = Gate8Colors.TextOnLight.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun TopItemsCard(data: ReportsSummaryDto) {
    ReportPanel(title = "Itens mais vendidos") {
        if (data.topItems.isEmpty()) {
            Text(
                "Nenhum item vendido no período",
                color = Gate8Colors.TextOnLight.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        } else {
            data.topItems.forEachIndexed { index, item ->
                BreakdownRow("${index + 1}. ${item.name}", item.quantity, item.total, unit = "un.")
            }
        }
    }
}

@Composable
private fun CashierStatusCard(cashier: CashierStatusDto) {
    val totals = cashier.totals
    val session = cashier.session
    val title = if (cashier.open) "Caixa · ainda aberto" else "Caixa · fechado"
    ReportPanel(title = title) {
        session?.operatorName?.takeIf { it.isNotBlank() }?.let {
            StatRow("Operador", it)
        }
        if (cashier.open) {
            StatRow("Troco inicial", money(totals?.openingBalance ?: session?.openingBalance ?: 0.0))
            StatRow("Vendas em dinheiro", money(totals?.cashSales ?: 0.0))
            (totals?.withdrawals ?: 0.0).takeIf { it > 0.0 }?.let { StatRow("Sangrias", money(it)) }
            (totals?.expenses ?: 0.0).takeIf { it > 0.0 }?.let { StatRow("Despesas", money(it)) }
            StatRow(
                "Saldo na gaveta",
                money(totals?.expectedDrawer ?: session?.expectedBalance ?: 0.0),
                highlight = true,
            )
        } else {
            StatRow("Troco inicial", money(totals?.openingBalance ?: session?.openingBalance ?: 0.0))
            StatRow("Vendas em dinheiro", money(totals?.cashSales ?: 0.0))
            StatRow("Esperado", money(totals?.expectedDrawer ?: session?.expectedBalance ?: 0.0))
            session?.countedBalance?.let { StatRow("Contado", money(it)) }
            session?.difference?.let { StatRow("Diferença", money(it), highlight = true) }
            if (session == null && totals == null) {
                Text(
                    "Nenhum caixa registrado",
                    color = Gate8Colors.TextOnLight.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ReportPanel(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Gate8Colors.CardSurface)
            .padding(16.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Gate8Colors.TextOnLight)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun StatRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Gate8Colors.TextOnLight.copy(alpha = 0.75f), fontSize = 13.sp)
        Text(
            value,
            color = if (highlight) Gate8Colors.AccentBlue else Gate8Colors.TextOnLight,
            fontSize = 13.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun BreakdownRow(label: String, count: Int, total: Double, unit: String = "venda(s)") {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Gate8Colors.TextOnLight, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("$count $unit", color = Gate8Colors.TextOnLight.copy(alpha = 0.6f), fontSize = 11.sp)
        }
        Text(
            money(total),
            color = Gate8Colors.AccentBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CustomPeriodDialog(
    from: LocalDateTime?,
    to: LocalDateTime?,
    onDismiss: () -> Unit,
    onApply: (LocalDateTime, LocalDateTime) -> Unit,
) {
    val fmtDateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    var fromText by remember(from) {
        mutableStateOf(from?.format(fmtDateTime)?.let(::maskDateTimeInput).orEmpty())
    }
    var toText by remember(to) {
        mutableStateOf(to?.format(fmtDateTime)?.let(::maskDateTimeInput).orEmpty())
    }
    var parseError by remember { mutableStateOf<String?>(null) }

    fun tryApply() {
        runCatching {
            val f = parseMaskedDateTime(fromText, endOfDayIfDateOnly = false)
            val t = parseMaskedDateTime(toText, endOfDayIfDateOnly = true)
            if (f.isAfter(t)) throw IllegalArgumentException("Início não pode ser depois do fim")
            onApply(f, t)
        }.onFailure {
            parseError = when (it) {
                is DateTimeParseException, is IllegalArgumentException ->
                    it.message ?: "Digite data e horário completos"
                else -> "Data/horário inválidos"
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.52f))
                .clickable(onClick = onDismiss),
        )
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.96f))
                .padding(horizontal = 22.dp, vertical = 24.dp),
        ) {
            Text(
                "Período personalizado",
                color = Gate8Colors.TextOnLight,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Teclado numérico — máscara dd/MM/aaaa HH:mm\n(8 dígitos = só data · 12 = data + hora)",
                color = Gate8Colors.TextOnLight.copy(alpha = 0.75f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            DateTimeMaskedField(
                label = "De",
                value = fromText,
                onValueChange = {
                    fromText = it
                    parseError = null
                },
                placeholder = "15/08/2026 20:00",
            )
            Spacer(Modifier.height(10.dp))
            DateTimeMaskedField(
                label = "Até",
                value = toText,
                onValueChange = {
                    toText = it
                    parseError = null
                },
                placeholder = "17/08/2026 14:00",
            )
            parseError?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    color = Gate8Colors.Error,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(20.dp))
            Gate8MenuButton(
                title = "Aplicar período",
                subtitle = "Carregar relatório do intervalo",
                onClick = ::tryApply,
                centerText = true,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Cancelar",
                color = Gate8Colors.AccentBlue,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun DateTimeMaskedField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    var field by remember {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length),
            ),
        )
    }
    LaunchedEffect(value) {
        if (field.text != value) {
            field = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    }

    OutlinedTextField(
        value = field,
        onValueChange = { incoming ->
            val prevDigits = field.text.filter { it.isDigit() }
            val nextDigits = incoming.text.filter { it.isDigit() }
            val hadFullSelection = field.selection.length == field.text.length && field.text.isNotEmpty()
            val digits = when {
                // Foco seleciona tudo → primeiro dígito começa a máscara do zero
                hadFullSelection && nextDigits.length <= 1 -> nextDigits
                nextDigits.length <= prevDigits.length -> nextDigits
                nextDigits.startsWith(prevDigits) -> nextDigits.take(12)
                else -> nextDigits.take(12)
            }
            val masked = maskDateTimeInput(digits)
            field = TextFieldValue(
                text = masked,
                selection = TextRange(masked.length),
            )
            onValueChange(masked)
        },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focus ->
                if (focus.isFocused && field.text.isNotEmpty()) {
                    field = field.copy(selection = TextRange(0, field.text.length))
                }
            },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Gate8Colors.TextOnLight,
            unfocusedTextColor = Gate8Colors.TextOnLight,
            focusedBorderColor = Gate8Colors.AccentBlue,
            unfocusedBorderColor = Gate8Colors.TextOnLight.copy(alpha = 0.5f),
            focusedLabelColor = Gate8Colors.AccentBlue,
            unfocusedLabelColor = Gate8Colors.TextOnLight,
            cursorColor = Gate8Colors.AccentBlue,
            focusedPlaceholderColor = Gate8Colors.TextOnLight.copy(alpha = 0.45f),
            unfocusedPlaceholderColor = Gate8Colors.TextOnLight.copy(alpha = 0.45f),
        ),
        shape = RoundedCornerShape(12.dp),
    )
}

/** Máscara progressiva: dígitos → `dd/MM/yyyy HH:mm` (máx. 12 dígitos). */
private fun maskDateTimeInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }.take(12)
    val out = StringBuilder()
    digits.forEachIndexed { index, c ->
        when (index) {
            2, 4 -> out.append('/')
            8 -> out.append(' ')
            10 -> out.append(':')
        }
        out.append(c)
    }
    return out.toString()
}

private fun parseMaskedDateTime(raw: String, endOfDayIfDateOnly: Boolean): LocalDateTime {
    val digits = raw.filter { it.isDigit() }
    return when (digits.length) {
        8 -> {
            val date = LocalDate.parse(digits, DateTimeFormatter.ofPattern("ddMMyyyy"))
            if (endOfDayIfDateOnly) date.atTime(23, 59) else date.atStartOfDay()
        }
        12 -> LocalDateTime.parse(digits, DateTimeFormatter.ofPattern("ddMMyyyyHHmm"))
        else -> throw IllegalArgumentException(
            "Informe 8 dígitos (data) ou 12 (data + hora)",
        )
    }
}

private fun money(value: Double): String = "R$ ${"%.2f".format(value)}"
