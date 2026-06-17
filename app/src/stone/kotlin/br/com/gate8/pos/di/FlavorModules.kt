package br.com.gate8.pos.di

import br.com.gate8.pos.stone.di.stoneFlavorModule
import org.koin.core.module.Module

fun flavorModules(): List<Module> = listOf(stoneFlavorModule)
