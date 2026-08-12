package br.com.gate8.pos.di

import br.com.gate8.pos.cielo.di.cieloFlavorModule
import org.koin.core.module.Module

fun flavorModules(): List<Module> = listOf(cieloFlavorModule)
