package br.com.gate8.pos.di

import br.com.gate8.pos.mercadopago.di.mercadoPagoFlavorModule
import org.koin.core.module.Module

fun flavorModules(): List<Module> = listOf(mercadoPagoFlavorModule)
